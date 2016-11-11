package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Table;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.uw.easysrl.qasrl.util.GuavaCollectors.toImmutableList;
import static edu.uw.easysrl.qasrl.util.GuavaCollectors.toImmutableSet;
import static java.util.stream.Collectors.*;

/**
 * Helpers.
 * Created by luheng on 3/19/16.
 */
public class QAPairAggregatorUtils {
    public static String answerDelimiter = " _AND_ ";

    static String getQuestionLabelString(final QuestionAnswerPair qa) {
        return qa.getPredicateIndex() + "\t" + qa.getPredicateCategory() + "\t" + qa.getArgumentNumber() + "\t";
    }

    static QAStructureSurfaceForm getQAStructureSurfaceForm(final List<QuestionSurfaceFormToStructure> qs2sEntries,
                                                            final List<AnswerSurfaceFormToStructure> as2sEntries) {
        final ImmutableList<QuestionAnswerPair> qaPairs = as2sEntries.stream()
                .flatMap(as2s -> as2s.qaList.stream())
                .collect(toImmutableList());
        return new QAStructureSurfaceForm(
                qaPairs.get(0).getSentenceId(),
                qs2sEntries.get(0).question,
                as2sEntries.get(0).answer,
                qaPairs,
                qs2sEntries.stream().map(qs2s -> qs2s.structure).distinct().collect(toImmutableList()),
                as2sEntries.stream().map(as2s -> as2s.structure).distinct().collect(toImmutableList()));
    }

    static QuestionSurfaceFormToStructure getQuestionSurfaceFormToStructure(
            final List<QuestionAnswerPair> qaList) {
        final List<QuestionAnswerPair> bestSurfaceFormQAs = getQAListWithBestQuestionSurfaceForm(qaList);
        return new QuestionSurfaceFormToStructure(
                bestSurfaceFormQAs.get(0).getQuestion(),
                new QuestionStructure(bestSurfaceFormQAs),
                qaList);
    }

    static AnswerSurfaceFormToStructure getAnswerSurfaceFormToSingleHeadedStructure(
            final List<QuestionAnswerPair> qaList) {
        final AnswerStructure answerStructure = new AnswerStructure(
                ImmutableList.of(qaList.get(0).getArgumentIndex()),
                true /* single headed */);
        final List<QuestionAnswerPair> bestSurfaceFormQAs = getQAListWithBestAnswerSurfaceForm(qaList);
        return new AnswerSurfaceFormToStructure(
                bestSurfaceFormQAs.get(0).getAnswer(),
                answerStructure,
                qaList);
    }

    static ImmutableList<AnswerSurfaceFormToStructure> getAnswerSurfaceFormToMultiHeadedStructures(
            final List<QuestionAnswerPair> qaList) {
        // Get answer indices list.
        Table<ImmutableList<Integer>, String, Double> argListToAnswerToScore = HashBasedTable.create();
        Table<ImmutableList<Integer>, QuestionAnswerPair, Boolean> argListToQAs = HashBasedTable.create();
        qaList.stream()
                .collect(groupingBy(QuestionAnswerPair::getParseId))
                .values().stream()
                .forEach(someParseQAs -> {
                    Map<Integer, String> argIdToSpan = new HashMap<>();
                    someParseQAs.forEach(qa -> argIdToSpan.put(qa.getArgumentIndex(), qa.getAnswer()));
                    ImmutableList<Integer> argList = argIdToSpan.keySet().stream()
                            .sorted()
                            .collect(toImmutableList());
                    String answerString = argList.stream()
                            .map(argIdToSpan::get)
                            .collect(Collectors.joining(answerDelimiter));
                    double score = someParseQAs.get(0).getParse().score;
                    double s0 = argListToAnswerToScore.contains(argList, answerString) ?
                            argListToAnswerToScore.get(argList, answerString) : .0;
                    argListToAnswerToScore.put(argList, answerString, score + s0);
                    someParseQAs.stream().forEach(qa -> argListToQAs.put(argList, qa, Boolean.TRUE));
                });

        return argListToAnswerToScore.rowKeySet().stream()
                .map(argList -> {
                    String bestAnswerString = argListToAnswerToScore.row(argList).entrySet().stream()
                            .max(Comparator.comparing(Map.Entry::getValue))
                            .get().getKey();
                    final Collection<QuestionAnswerPair> qaList2 = argListToQAs.row(argList).keySet();
                    return new AnswerSurfaceFormToStructure(
                            bestAnswerString,
                            new AnswerStructure(argList, false /* not single headed */),
                            qaList2);
                }).collect(GuavaCollectors.toImmutableList());
    }

    static List<QuestionAnswerPair> getQAListWithBestQuestionSurfaceForm(Collection<QuestionAnswerPair> qaList) {
        return qaList.stream()
                .collect(groupingBy(QuestionAnswerPair::getQuestion))
                .entrySet().stream()
                .max(Comparator.comparing(e -> QAPairAggregatorUtils.getScore(e.getValue())))
                .get().getValue();
    }

    static List<QuestionAnswerPair> getQAListWithBestAnswerSurfaceForm(Collection<QuestionAnswerPair> qaList) {
        return qaList.stream()
                .collect(groupingBy(QuestionAnswerPair::getAnswer))
                .entrySet().stream()
                .max(Comparator.comparing(e -> QAPairAggregatorUtils.getScore(e.getValue())))
                .get().getValue();
    }

    private static double getScore(Collection<QuestionAnswerPair> qaList) {
        return qaList.stream()
                .collect(groupingBy(QuestionAnswerPair::getParseId))
                .entrySet().stream()
                .mapToDouble(e -> e.getValue().get(0).getParse().score)
                .sum();
    }

    static class QuestionSurfaceFormToStructure {
        public final String question;
        public final QuestionStructure structure;
        public final Collection<QuestionAnswerPair> qaList;

         QuestionSurfaceFormToStructure(String question, QuestionStructure structure, Collection<QuestionAnswerPair> qaList) {
            this.question = question;
            this.structure = structure;
            this.qaList = qaList;
        }
    }

    static class AnswerSurfaceFormToStructure {
        public final String answer;
        public final AnswerStructure structure;
        public final Collection<QuestionAnswerPair> qaList;

        AnswerSurfaceFormToStructure(String answer, AnswerStructure structure, Collection<QuestionAnswerPair> qaList) {
            this.answer = answer;
            this.structure = structure;
            this.qaList = qaList;
        }
    }
}
