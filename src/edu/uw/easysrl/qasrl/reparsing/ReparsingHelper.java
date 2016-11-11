package edu.uw.easysrl.qasrl.reparsing;

import com.google.common.collect.*;
import edu.uw.easysrl.syntax.model.Constraint;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.IntStream;

/**
 * Extract constraints from a annotator responses.
 * Created by luheng on 5/30/16.
 */
public class ReparsingHelper {
    public static ImmutableSet<Constraint> getConstraints(final ScoredQuery<QAStructureSurfaceForm> query,
                                                          final ImmutableList<ImmutableList<Integer>> matchedResponses,
                                                          final ReparsingParameters config) {
        final int[] optionDist = new int[query.getOptions().size()];
        Arrays.fill(optionDist, 0);
        matchedResponses.forEach(r -> r.stream().forEach(op -> optionDist[op] ++));

        final Set<Constraint> constraints = new HashSet<>();
        final int numQAs = query.getQAPairSurfaceForms().size();

        // Add supertag constraints.
        if (optionDist[query.getBadQuestionOptionId().getAsInt()] >= config.positiveConstraintMinAgreement) {
            query.getQAPairSurfaceForms().stream()
                    .flatMap(qa -> qa.getQuestionStructures().stream())
                    .distinct()
                    .forEach(qstr -> constraints.add(new Constraint.SupertagConstraint(qstr.predicateIndex,
                            qstr.category, false, config.supertagPenalty)));
            return ImmutableSet.copyOf(constraints);
        }

        // Order option from highest votes.
        final ImmutableList<Integer> optionOrders = IntStream.range(0, numQAs)
                .boxed()
                .sorted((i, j) -> Integer.compare(-optionDist[i], -optionDist[j]))
                .collect(GuavaCollectors.toImmutableList());

        // Apply heuristics.
        Table<Integer, Integer, String> relations = HeuristicHelper.getOptionRelations(query);

        Set<Integer> skipOps = new HashSet<>();
        for (int opId1 : optionOrders) {
            if (skipOps.contains(opId1)) {
                continue;
            }
            skipOps.add(opId1);
            final int votes = optionDist[opId1];
            boolean appliedHeuristic = false;
            for (int opId2 : optionOrders) {
                if (skipOps.contains(opId2) || !relations.contains(opId1, opId2)) {
                    continue;
                }
                final String rel = relations.get(opId1, opId2);
                final int votes2 = optionDist[opId2];
                if (votes + votes2 < config.positiveConstraintMinAgreement) {
                    continue;
                }
                if (config.fixPronouns && rel.startsWith("pronoun")) {
                    addConstraints(constraints, query, ImmutableList.of(opId1, opId2), true, config);
                    appliedHeuristic = true;
                    skipOps.add(opId2);
                    break;
                }
                if (config.fixSubspans && rel.equals("subspan") && Math.abs(votes - votes2) <= 1) {
                    addConstraints(constraints, query, ImmutableList.of(opId1, opId2), true, config);
                    appliedHeuristic = true;
                    skipOps.add(opId2);
                    break;
                }
            }
            if (appliedHeuristic) {
                continue;
            }
            // Normal positive/negative constraints.
            if (votes >= config.positiveConstraintMinAgreement) {
                addConstraints(constraints, query, ImmutableList.of(opId1), true, config);
            } else if (votes <= config.negativeConstraintMaxAgreement) {
                addConstraints(constraints, query, ImmutableList.of(opId1), false, config);
            }
        }
        return ImmutableSet.copyOf(constraints);
    }

    private static void addConstraints(final Set<Constraint> constraints,
                                       final ScoredQuery<QAStructureSurfaceForm> query,
                                       final ImmutableList<Integer> options,
                                       boolean positive,
                                       ReparsingParameters config) {
        final ImmutableList<QuestionStructure> questionStructures = query.getQAPairSurfaceForms()
                .stream()
                .flatMap(qa -> qa.getQuestionStructures().stream())
                .distinct()
                .collect(GuavaCollectors.toImmutableList());
        final ImmutableList<Integer> argIds = options.stream()
                .map(query.getQAPairSurfaceForms()::get)
                .flatMap(qa -> qa.getAnswerStructures().stream())
                .flatMap(astr -> astr.argumentIndices.stream())
                .distinct().sorted()
                .collect(GuavaCollectors.toImmutableList());
        for (QuestionStructure qstr : questionStructures) {
            final int headId = qstr.targetPrepositionIndex >= 0 ? qstr.targetPrepositionIndex : qstr.predicateIndex;
            final ImmutableList<Integer> filteredArgs = argIds.stream()
                    .filter(argId -> argId != headId)
                    .collect(GuavaCollectors.toImmutableList());
            if (argIds.isEmpty()) {
                continue;
            }
            if (positive) {
                if (filteredArgs.size() == 1) {
                    constraints.add(new Constraint.AttachmentConstraint(headId, filteredArgs.get(0), true,
                            config.positiveConstraintPenalty));
                } else if (filteredArgs.size() > 1) {
                    constraints.add(new Constraint.DisjunctiveAttachmentConstraint(headId, filteredArgs, true,
                            config.positiveConstraintPenalty));
                }
            } else {
                filteredArgs.forEach(argId -> constraints.add(new Constraint.AttachmentConstraint(headId, argId,
                        false, config.negativeConstraintPenalty)));
            }
        }
    }
}
