package edu.uw.easysrl.qasrl.reparsing;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.QueryType;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.model.Constraint;

import java.util.*;

/**
 * TODO: Need to refactor.
 * Created by luheng on 4/1/16.
 */
class ConstraintExtractor {

    static Set<Constraint> extractPositiveConstraints(ScoredQuery<QAStructureSurfaceForm> query,
                                                             Collection<Integer> chosenOptions) {
        Set<Constraint> constraints = new HashSet<>();
        chosenOptions.stream()
                .filter(op -> op < query.getQAPairSurfaceForms().size())
                .map(query.getQAPairSurfaceForms()::get)
                .forEach(qa -> qa.getQuestionStructures().stream()
                        .forEach(qstr -> {
                            final int headId = qstr.targetPrepositionIndex >= 0 ? qstr.targetPrepositionIndex : qstr.predicateIndex;
                            if (query.getQueryType() == QueryType.Forward) {
                                qa.getAnswerStructures().stream()
                                        .flatMap(astr -> astr.argumentIndices.stream())
                                        .distinct()
                                        .filter(argId -> argId != headId)
                                        .forEach(argId -> constraints.add(new Constraint.AttachmentConstraint(headId, argId, true, 1.0)));
                            }
                        })
                );
        return constraints;
    }

    static Set<Constraint> extractNegativeConstraints(ScoredQuery<QAStructureSurfaceForm> query,
                                                             Collection<Integer> chosenOptions,
                                                             boolean doNotPenalizePronouns) {
        final int numQAOptions = query.getQAPairSurfaceForms().size();
        final Set<Constraint> constraintList = new HashSet<>();
        Set<Integer> chosenArgIds = new HashSet<>(), listedArgIds = new HashSet<>();
        Map<Integer, String> argIdToSpan = new HashMap<>();
        for (int i = 0; i < query.getOptions().size(); i++) {
            final String option = query.getOptions().get(i);
            if (i < numQAOptions) {
                final QAStructureSurfaceForm qa = query.getQAPairSurfaceForms().get(i);
                final ImmutableList<Integer> argIds = qa.getArgumentIndices();
                if (chosenOptions.contains(i)) {
                    chosenArgIds.addAll(argIds);
                }
                listedArgIds.addAll(argIds);
                if (doNotPenalizePronouns) {
                    final String[] answerSpans = option.split(QAPairAggregatorUtils.answerDelimiter);
                    for (int j = 0; j < argIds.size(); j++) {
                        argIdToSpan.put(argIds.get(j), answerSpans[j]);
                    }
                }
            }
        }
        final boolean questionIsNA = chosenOptions.contains(query.getBadQuestionOptionId().getAsInt());
        if (questionIsNA) {
            query.getQAPairSurfaceForms().stream()
                    .flatMap(qa -> qa.getQuestionStructures().stream())
                    .forEach(qstr -> constraintList.add(
                            new Constraint.SupertagConstraint(qstr.predicateIndex, qstr.category, false, 1.0)));
        } else {
            query.getQAPairSurfaceForms().stream()
                    .flatMap(qa -> qa.getQuestionStructures().stream())
                    .map(qstr -> qstr.targetPrepositionIndex >= 0 ? qstr.targetPrepositionIndex : qstr.predicateIndex)
                    .distinct()
                    .forEach(headId -> listedArgIds.stream()
                            .filter(argId -> !chosenArgIds.contains(argId))
                            .filter(argId -> !doNotPenalizePronouns ||
                                    !PronounList.englishPronounSet.contains(argIdToSpan.get(argId).toLowerCase()))
                            .filter(argId -> argId.intValue() != headId.intValue())
                            .forEach(argId -> constraintList.add(
                                    new Constraint.AttachmentConstraint(headId, argId, false, 1.0)))
                    );
        }
        return constraintList;
    }
}
