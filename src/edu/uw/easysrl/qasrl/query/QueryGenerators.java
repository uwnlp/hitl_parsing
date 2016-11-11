package edu.uw.easysrl.qasrl.query;

import edu.uw.easysrl.qasrl.qg.surfaceform.*;
import edu.uw.easysrl.syntax.grammar.Category;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static edu.uw.easysrl.qasrl.util.GuavaCollectors.*;
import static java.util.stream.Collectors.*;

/**
 * Convenience class to hold our QueryAggregator instances,
 * which are often polymorphic (so they appear as polymorphic static methods).
 *
 * This class encodes LOGIC, NOT DATA.
 *
 * Created by julianmichael on 3/17/16.
 */
public class QueryGenerators {
    public static QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> checkboxQueryGenerator() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(QueryGeneratorUtils::getQueryKey))
                .values()
                .stream()
                .map(qaList -> {
                    ImmutableList<QAStructureSurfaceForm> sortedQAList = qaList.stream()
                            .sorted((qa1, qa2) -> Integer.compare(qa1.getArgumentIndices().get(0),
                                                                  qa2.getArgumentIndices().get(0)))
                            .collect(GuavaCollectors.toImmutableList());
                    List<String> options = sortedQAList.stream().map(QAStructureSurfaceForm::getAnswer).collect(toList());
                    options.add(QueryGeneratorUtils.kNoneApplicableString);
                    return new ScoredQuery<>(qaList.get(0).getSentenceId(),
                                             qaList.get(0).getQuestion(),
                                             ImmutableList.copyOf(options),
                                             sortedQAList,
                                             QueryType.Forward,
                                             true /* allow multiple */);
                }).collect(toImmutableList());
    }

    public static QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> radioButtonQueryGenerator() {
        return qaPairs -> qaPairs
                .stream()
                .collect(groupingBy(QueryGeneratorUtils::getQueryKey))
                .values()
                .stream()
                .map(qaList -> {
                    List<String> options = qaList.stream().map(QAStructureSurfaceForm::getAnswer).collect(toList());
                    options.add(QueryGeneratorUtils.kUnlistedAnswerOptionString);
                    options.add(QueryGeneratorUtils.kBadQuestionOptionString);
                    return new ScoredQuery<>(
                            qaList.get(0).getSentenceId(),
                            qaList.get(0).getQuestion(),
                            ImmutableList.copyOf(options),
                            ImmutableList.copyOf(qaList),
                            QueryType.Forward,
                            false /* allow multiple */);
                }).collect(toImmutableList());
    }

    private QueryGenerators() {
        throw new AssertionError("no instances");
    }
}
