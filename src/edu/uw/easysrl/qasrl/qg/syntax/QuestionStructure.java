package edu.uw.easysrl.qasrl.qg.syntax;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.QuestionAnswerPair;
import edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * CCG structure encoded in queryPrompt:
 *      category, argnum, queryPrompt dependency (dependency of other core args used in the queryPrompt).
 * CCG structure encoded in answer:
 *      argument id list, other dependencies used to generate the answer span.
 * Created by luheng on 3/19/16.
 */
public class QuestionStructure {
    public final int predicateIndex;
    public final Category category;
    public final int targetArgNum;
    public final int targetPrepositionIndex;
    public final ImmutableMap<Integer, ImmutableList<Integer>> otherDependencies;
    private final String hashString;

    public QuestionStructure(int predId, Category category, int argNum, Collection<ResolvedDependency> otherDeps) {
        this.predicateIndex = predId;
        this.category = category;
        this.targetArgNum = argNum;
        final boolean isAdverbArg = targetArgNum == -1;
        final boolean isPPArg = !isAdverbArg && category.getArgument(targetArgNum) == Category.PP;
        this.otherDependencies = otherDeps.stream()
                .filter(dep -> dep.getHead() == predId)
                .filter(dep -> isAdverbArg || isPPArg || dep.getArgNumber() != argNum)
                .collect(Collectors.groupingBy(ResolvedDependency::getArgNumber))
                .entrySet().stream()
                .collect(GuavaCollectors.toImmutableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(ResolvedDependency::getArgumentIndex)
                                .distinct().sorted()
                                .collect(GuavaCollectors.toImmutableList())));
        if(isAdverbArg) {
            targetPrepositionIndex = otherDependencies.get(2).get(0);
        } else if(isPPArg && otherDependencies.containsKey(targetArgNum)) {
            targetPrepositionIndex = otherDependencies.get(targetArgNum).get(0);
        } else {
            targetPrepositionIndex = -1;
        }
        hashString = predicateIndex + "\t" + category + "\t" + targetArgNum + "\t("
                        + otherDependencies.entrySet().stream()
                            .sorted(Comparator.comparing(Map.Entry::getKey))
                            .map(e -> String.format("%d:%s", e.getKey(), e.getValue().stream()
                            .map(String::valueOf).collect(Collectors.joining(","))))
                            .collect(Collectors.joining("_")) + ")";
    }

    /**
     * For convenience.
     * @param qaList: Q/A pairs sharing the same queryPrompt structure.
     */
    public QuestionStructure(final List<QuestionAnswerPair> qaList) {
        final QuestionAnswerPair qa = qaList.get(0);
        this.predicateIndex = qa.getPredicateIndex();
        this.category = qa.getPredicateCategory();
        this.targetArgNum = qa.getArgumentNumber();
        final boolean isAdverbArg = targetArgNum == -1;
        final boolean isPPArg = !isAdverbArg && category.getArgument(targetArgNum) == Category.PP;
        this.otherDependencies = qaList.get(0).getQuestionDependencies().stream()
                .filter(dep -> dep.getHead() == predicateIndex)
                .filter(dep -> isAdverbArg || isPPArg || dep.getArgNumber() != targetArgNum)
                .collect(Collectors.groupingBy(ResolvedDependency::getArgNumber))
                .entrySet().stream()
                .collect(GuavaCollectors.toImmutableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(ResolvedDependency::getArgumentIndex)
                                .distinct().sorted()
                                .collect(GuavaCollectors.toImmutableList())));
        if(isAdverbArg) {
            targetPrepositionIndex = qa.getTargetDependency().getHead();
        } else if(isPPArg && otherDependencies.containsKey(targetArgNum)) {
            targetPrepositionIndex = otherDependencies.get(targetArgNum).get(0);
        } else {
            targetPrepositionIndex = -1;
        }
        hashString = predicateIndex + "\t" + category + "\t" + targetArgNum + "\t("
                + otherDependencies.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> String.format("%d:%s", e.getKey(), e.getValue().stream()
                        .map(String::valueOf).collect(Collectors.joining(","))))
                .collect(Collectors.joining("_")) + ")";
    }

    public boolean equals(final Object other) {
        return QuestionStructure.class.isInstance(other) && hashString.equals(((QuestionStructure) other).hashString);
    }

    @Override
    public int hashCode() {
        return hashString.hashCode();
    }

    /**
     * * Return all the dependencies that's relevant to this structure.
     */
    public ImmutableSet<ResolvedDependency> filter(Collection<ResolvedDependency> dependencies) {
        return dependencies.stream()
                .filter(d -> (targetPrepositionIndex >= 0
                                && d.getCategory() == Category.valueOf("PP/NP")
                                && targetPrepositionIndex == d.getHead()) ||
                            (d.getHead() == predicateIndex && d.getCategory() == category
                                && d.getArgNumber() == targetArgNum))
                .collect(GuavaCollectors.toImmutableSet());
    }

    public String toString(final ImmutableList<String> words) {
        return String.format("%d:%s_%s.%d", predicateIndex, words.get(predicateIndex), category, targetArgNum)
                + " ("
                + otherDependencies.entrySet().stream()
                        .sorted(Comparator.comparing(Map.Entry::getKey))
                        .map(e -> String.format("%d:%s", e.getKey(), e.getValue().stream()
                                .map(String::valueOf).collect(Collectors.joining(","))))
                        .collect(Collectors.joining("_")) + ") ";
    }
}
