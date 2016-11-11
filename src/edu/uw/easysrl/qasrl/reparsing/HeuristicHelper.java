package edu.uw.easysrl.qasrl.reparsing;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;

import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.PronounList;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;


public class HeuristicHelper {
    public static Table<Integer, Integer, String> getOptionRelations(final ScoredQuery<QAStructureSurfaceForm> query) {
        Table<Integer, Integer, String> relations = HashBasedTable.create();
        final int predicateId = query.getPredicateId().getAsInt();
        final int headId = query.getPrepositionIndex().isPresent() ?
                query.getPrepositionIndex().getAsInt() : predicateId;

        final int numQAs = query.getQAPairSurfaceForms().size();
        for (int opId1 = 0; opId1 < numQAs; opId1++) {
            final QAStructureSurfaceForm qa1 = query.getQAPairSurfaceForms().get(opId1);
            final ImmutableList<Integer> args1 = qa1.getAnswerStructures().stream()
                    .flatMap(ans -> ans.argumentIndices.stream())
                    .distinct().sorted().collect(GuavaCollectors.toImmutableList());
            final String answer1 = qa1.getAnswer().toLowerCase();
            final int dist1 = Math.abs(headId - args1.get(0));

            for (int opId2 = 0; opId2 < numQAs; opId2++) {
                if (opId2 == opId1 || relations.contains(opId1, opId2) || relations.contains(opId2, opId1)) {
                    continue;
                }
                final QAStructureSurfaceForm qa2 = query.getQAPairSurfaceForms().get(opId2);
                final ImmutableList<Integer> args2 = qa2.getAnswerStructures().stream()
                        .flatMap(ans -> ans.argumentIndices.stream())
                        .distinct().sorted().collect(GuavaCollectors.toImmutableList());
                final String answer2 = qa2.getAnswer().toLowerCase();
                final int dist2 = Math.abs(headId - args2.get(0));

                final boolean isSubspan = answer1.endsWith(" " + answer2)
                        || answer1.startsWith(answer2 + " ");
                final boolean isPronoun = PronounList.nonPossessivePronouns.contains(qa2.getAnswer().toLowerCase())
                        && dist2 < dist1;

                if (isSubspan) {
                    relations.put(opId1, opId2, "subspan");
                } else if (isPronoun) {
                    relations.put(opId1, opId2, "pronoun");
                }
            }
        }
        return relations;
    }
}

