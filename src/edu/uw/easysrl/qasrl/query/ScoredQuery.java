package edu.uw.easysrl.qasrl.query;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.qg.TextGenerationHelper;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.util.DebugPrinter;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Query with Scores. Just more convenient.
 * Created by luheng on 3/20/16.
 */
public class ScoredQuery<QA extends QAStructureSurfaceForm> implements Query<QA> {
    public int getSentenceId() {
        return sentenceId;
    }

    public String getPrompt() {
        return prompt;
    }

    public ImmutableList<String> getOptions() {
        return options;
    }

    public ImmutableList<QA> getQAPairSurfaceForms() {
        return qaPairSurfaceForms;
    }

    private final int sentenceId;
    private final String prompt;
    private final ImmutableList<String> options;
    private final ImmutableList<QA> qaPairSurfaceForms;
    private QueryType queryType;
    private boolean allowMultipleChoices;

    private int queryId;
    private double promptScore, optionEntropy;
    private ImmutableList<Double> optionScores;
    private ImmutableList<ImmutableSet<Integer>> optionToParseIds;

    public ScoredQuery(int sentenceId,
                       String prompt,
                       ImmutableList<String> options,
                       ImmutableList<QA> qaPairSurfaceForms,
                       QueryType queryType,
                       boolean allowMultipleChoices) {
        this.sentenceId = sentenceId;
        this.prompt = prompt;
        this.options = options;
        this.qaPairSurfaceForms = qaPairSurfaceForms;
        this.queryType = queryType;
        this.allowMultipleChoices = allowMultipleChoices;
        this.optionScores = null;
        this.optionToParseIds = null;
    }

    @Deprecated
    public ScoredQuery(int sentenceId,
                       String prompt,
                       ImmutableList<String> options,
                       ImmutableList<QA> qaPairSurfaceForms,
                       QueryType queryType,
                       boolean allowMultipleChoices,
                       ImmutableList<ImmutableSet<Integer>> optionToParseIds,
                       ImmutableList<Double> optionScores) {
        this(sentenceId, prompt, options, qaPairSurfaceForms, queryType, allowMultipleChoices);
        this.optionToParseIds = optionToParseIds;
        this.optionScores = optionScores;
    }

    public void computeScores(NBestList nbestList) {
        Set<Integer> allParseIds = IntStream.range(0, nbestList.getN()).boxed().collect(Collectors.toSet());
        double totalScore = allParseIds.stream().mapToDouble(nbestList::getScore).sum();
        if (optionToParseIds == null) {
            optionToParseIds = IntStream.range(0, options.size()).boxed()
                    .map(i -> {
                        ImmutableSet<Integer> pids = ImmutableSet.of();
                        if (i < qaPairSurfaceForms.size()) {
                            pids = QueryGeneratorUtils.getParseIdsForQAPair(qaPairSurfaceForms.get(i), nbestList);
                            allParseIds.removeAll(pids);
                        } else if (QueryGeneratorUtils.isNAOption(options.get(i))) {
                            pids = ImmutableSet.copyOf(allParseIds);
                        }
                        return pids;
                    }).collect(GuavaCollectors.toImmutableList());
        }
        if (optionScores == null) {
            optionScores = optionToParseIds.stream()
                    .map(pids -> pids.stream().mapToDouble(nbestList::getScore).sum() / totalScore)
                    .collect(GuavaCollectors.toImmutableList());
        }

        promptScore = 1.0 - optionScores.get(getBadQuestionOptionId().getAsInt());
        optionEntropy = QueryGeneratorUtils.computeEntropy(optionScores);
    }

    public ImmutableList<Double> getOptionScores() {
        return optionScores;
    }

    public ImmutableList<ImmutableSet<Integer>> getOptionToParseIds() {
        return optionToParseIds;
    }

    public double getPromptScore() {
        return promptScore;
    }

    public double getOptionEntropy() {
        return optionEntropy;
    }

    public boolean isJeopardyStyle() { return queryType == QueryType.Jeopardy; }

    public QueryType getQueryType() { return queryType; }

    public boolean allowMultipleChoices() {
        return allowMultipleChoices;
    }

    public void setQueryId(int queryId) {
        this.queryId = queryId;
    }

    public int getQueryId() {
        return queryId;
    }

    public String getQueryKey() {
        return !isJeopardyStyle() ?
                qaPairSurfaceForms.get(0).getPredicateIndex() + "\t" + prompt :
                qaPairSurfaceForms.get(0).getArgumentIndices().stream().map(String::valueOf)
                        .collect(Collectors.joining(",")) + "\t" + prompt;
    }

    public OptionalInt getBadQuestionOptionId() {
        return IntStream.range(0, options.size())
                .filter(i -> QueryGeneratorUtils.isNAOption(options.get(i)))
                .findFirst();
    }

    public OptionalInt getUnlistedAnswerOptionId() {
        return IntStream.range(0, options.size())
                .filter(i -> options.get(i).equals(QueryGeneratorUtils.kUnlistedAnswerOptionString))
                .findFirst();
    }

    public OptionalInt getPredicateId() {
        return isJeopardyStyle() ? OptionalInt.empty() : OptionalInt.of(qaPairSurfaceForms.get(0).getPredicateIndex());
    }

    public Optional<Category> getPredicateCategory() {
        return isJeopardyStyle() ? Optional.empty() : Optional.of(qaPairSurfaceForms.get(0).getCategory());
    }

    public OptionalInt getArgumentNumber() {
        return isJeopardyStyle()? OptionalInt.empty() : OptionalInt.of(qaPairSurfaceForms.get(0).getArgumentNumber());
    }

    public OptionalInt getPrepositionIndex() {
        Optional<QuestionStructure> ppQuestionOpt = getQAPairSurfaceForms().stream()
                .flatMap(qa -> qa.getQuestionStructures().stream())
                .filter(q -> q.targetPrepositionIndex >= 0)
                .findAny();
        return ppQuestionOpt.isPresent() ? OptionalInt.of(ppQuestionOpt.get().targetPrepositionIndex)
                : OptionalInt.empty();
    }

    /**
     *
     * @param sentence
     * @param optionLegends: example: 'G', goldOptions, 'U', userOptions
     * @return
     */
    public String toString(final ImmutableList<String> sentence, Object ... optionLegends) {
        String result = String.format("SID=%d\t%s\n", sentenceId, TextGenerationHelper.renderString(sentence) + ".");

        // Prompt structure.
        String promptStructStr = isJeopardyStyle() ?
                qaPairSurfaceForms.stream()
                        .flatMap(qa -> qa.getAnswerStructures().stream())
                        .distinct()
                        .map(s -> s.toString(sentence))
                        .collect(Collectors.joining(" / ")) :
                qaPairSurfaceForms.stream()
                        .flatMap(qa -> qa.getQuestionStructures().stream())
                        .distinct()
                        .map(s -> s.toString(sentence))
                        .collect(Collectors.joining(" / "));

        // Prompt.
        result += String.format("[prompt]:\t \t%.2f\t%s\t%s\n", promptScore, prompt, promptStructStr);

        for (int i = 0; i < options.size(); i++) {
            String matchingStr = "";
            for (int j = 0; j + 1 < optionLegends.length; j += 2) {
                char legend = (char) optionLegends[j];
                if (legend == '*') {
                    final int[] optionDist = (int[]) optionLegends[j + 1];
                    for (int k = 0; k < optionDist[i]; k++) {
                        matchingStr += legend;
                    }
                } else {
                    final ImmutableList<Integer> chosenOptions = (ImmutableList<Integer>) optionLegends[j + 1];
                    matchingStr += chosenOptions.contains(i) ? (char) optionLegends[j] : "";
                }
            }
            String structStr = "";
            if (i < qaPairSurfaceForms.size()) {
                final QAStructureSurfaceForm qa = qaPairSurfaceForms.get(i);
                structStr = isJeopardyStyle() ?
                        qa.getQuestionStructures().stream().map(s -> s.toString(sentence)).collect(Collectors.joining(" / ")) :
                        qa.getAnswerStructures().stream().map(s -> s.toString(sentence)).collect(Collectors.joining(" / "));
            }
            //String parseIdsStr = DebugPrinter.getShortListString(optionToParseIds.get(i));
            //result += String.format("[%d]\t%-10s\t%.2f\t%s\t%s\t%s\n", i, matchingStr, optionScores.get(i),
            //            options.get(i), structStr, parseIdsStr);
            result += String.format("[%d]\t%-10s\t%.2f\t%s\t%s\n", i, matchingStr, optionScores.get(i), options.get(i),
                    structStr);
        }
        return result;
    }

    /**
     * @param sentence
     * @param annotations
     * @return
     */
    public String toAnnotationString(final ImmutableList<String> sentence,
                                     ImmutableList<ImmutableList<Integer>> annotations) {
        final String legend = "abcdefghijklm";
        String result = String.format("SID=%d\t%s\n", sentenceId, sentence.stream().collect(Collectors.joining(" ")));
        // Prompt structure.
        String promptStructStr = qaPairSurfaceForms.stream()
                .flatMap(qa -> qa.getQuestionStructures().stream())
                .distinct()
                .map(s -> s.toString(sentence))
                .collect(Collectors.joining(" / "));
        // Prompt.
        result += String.format("[prompt]:\t \t%.2f\t%s\t%s\n", promptScore, prompt, promptStructStr);
        for (int i = 0; i < options.size(); i++) {
            String matchingStr = "";
            for (int j = 0; j < annotations.size(); j++) {
                final char legendChar = legend.charAt(j);
                matchingStr += annotations.get(j).contains(i) ? legendChar : "";
            }
            String structStr = "";
            if (i < qaPairSurfaceForms.size()) {
                final QAStructureSurfaceForm qa = qaPairSurfaceForms.get(i);
                structStr = qa.getAnswerStructures().stream().map(s -> s.toString(sentence)).collect(Collectors.joining(" / "));
            }
            result += String.format("[%d]\t%-10s\t%.2f\t%s\t%s\n", i, matchingStr, optionScores.get(i), options.get(i), structStr);
        }
        return result;
    }
}
