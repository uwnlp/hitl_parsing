package edu.uw.easysrl.qasrl.reparsing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryType;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * Simulates user response in active learning. When presented with a queryPrompt, the simulator answerOptions with its
 * knowledge of gold dependencies.
 * Created by luheng on 1/5/16.
 */
public class ResponseSimulatorGold implements ResponseSimulator {
    private final ParseData parseData;

    public ResponseSimulatorGold(ParseData parseData) {
        this.parseData = parseData;
    }

     public ImmutableList<Integer> respondToQuery(ScoredQuery<QAStructureSurfaceForm> query) {
         final int sentenceId = query.getSentenceId();
         final Parse goldParse = parseData.getGoldParses().get(sentenceId);

         Set<Integer> chosenOptions = new HashSet<>();

         if (query.getQueryType() == QueryType.Forward) {
             final ImmutableList<QAStructureSurfaceForm> qaOptions = query.getQAPairSurfaceForms();
             // The gold considers labeled dependency. If the dependency labels don't match, gold outputs "bad question".
             // So the gold outputs "bad question" in case of dropped pp argument.
             AtomicBoolean nonEmptyGoldArgs = new AtomicBoolean(false);
             qaOptions.stream().flatMap(qa -> qa.getQuestionStructures().stream())
                     .forEach(qstr -> {
                         // FIXME: this doesn't work for the radiobutton version.
                         final ImmutableSet<Integer> goldArgIds = goldParse.dependencies.stream()
                                 .filter(dep -> (dep.getHead() == qstr.predicateIndex
                                                    && dep.getCategory() == qstr.category
                                                    && dep.getArgNumber() == qstr.targetArgNum)
                                         || dep.getHead() == qstr.targetPrepositionIndex)
                                 .map(ResolvedDependency::getArgument)
                                 .distinct()
                                 .collect(GuavaCollectors.toImmutableSet());
                         if (goldArgIds.size() > 0) {
                             nonEmptyGoldArgs.getAndSet(true);
                         }
                         // Checkbox version.
                         if (query.allowMultipleChoices()) {
                             IntStream.range(0, qaOptions.size())
                                     .filter(optionId -> qaOptions.get(optionId).getAnswerStructures().stream()
                                             .anyMatch(astr -> goldArgIds.containsAll(astr.argumentIndices)))
                                     .forEach(chosenOptions::add);
                         }
                         // Radio Button version.
                         else {
                             IntStream.range(0, qaOptions.size())
                                     .filter(optionId -> qaOptions.get(optionId).getAnswerStructures().stream()
                                                 .map(astr -> astr.argumentIndices)
                                                 .anyMatch(argIds -> goldArgIds.containsAll(argIds) &&
                                                                     argIds.containsAll(goldArgIds)))
                                     .forEach(chosenOptions::add);
                         }
                     });
             if (chosenOptions.size() == 0) {
                 if (nonEmptyGoldArgs.get() && query.getUnlistedAnswerOptionId().isPresent()) {
                     chosenOptions.add(query.getUnlistedAnswerOptionId().getAsInt());
                 } else if (query.getBadQuestionOptionId().isPresent()){
                     chosenOptions.add(query.getBadQuestionOptionId().getAsInt());
                }
             }
         } else {
             IntStream.range(0, query.getQAPairSurfaceForms().size()).boxed()
                     .filter(i -> query.getQAPairSurfaceForms().get(i).canBeGeneratedBy(goldParse))
                     .forEach(chosenOptions::add);
             if (chosenOptions.size() == 0) {
                chosenOptions.add(query.getBadQuestionOptionId().getAsInt());
             }
         }
         return chosenOptions.stream().sorted().collect(GuavaCollectors.toImmutableList());
     }
}
