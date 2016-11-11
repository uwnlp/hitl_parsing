package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.qg.TextGenerationHelper;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.qasrl.qg.syntax.QuestionStructure;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import org.apache.commons.csv.CSVPrinter;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by luheng on 3/22/16.
 */
public class CrowdFlowerDataUtils {
    static final String[] csvHeaderNew = {
            "query_id", "sent_id", "sentence", "query_prompt", "options", "_golden ", "choice_gold", "choice_gold_reason",
            "query_key", "jeopardy_style", "query_confidence", "query_uncertainty" };

    static final String answerDelimiter = " ### ";

    static void printQueryToCSVFile(final ScoredQuery<QAStructureSurfaceForm> query,
                                           final ImmutableList<String> sentence,
                                           final ImmutableList<Integer> goldOptionIds,
                                           final int lineCounter,
                                           final boolean highlightPredicate,
                                           final String goldReason,
                                           final CSVPrinter csvPrinter) throws IOException {
        // Print to CSV files.
        // "query_id", "sent_id", "sentence", "query_prompt", "options", "_golden ", "choice_gold", "choice_gold_reason",
        // "query_key", "jeopardy_style", "query_confidence", "query_uncertainty"
        final QuestionStructure questionStructure = query.getQAPairSurfaceForms().get(0).getQuestionStructures().get(0);
        final AnswerStructure answerStructure = query.getQAPairSurfaceForms().get(0).getAnswerStructures().get(0);
        int predicateIndex = query.isJeopardyStyle() ?
                answerStructure.argumentIndices.get(0) :
                questionStructure.predicateIndex;

        int sentenceId = query.getSentenceId();
        final String sentenceStr = TextGenerationHelper.renderHTMLSentenceString(sentence, predicateIndex,
                highlightPredicate);
        final ImmutableList<String> options = query.getOptions();
        List<String> csvRow = new ArrayList<>();
        csvRow.add(String.valueOf(lineCounter)); // Query id

        csvRow.add(String.valueOf(sentenceId));
        csvRow.add(String.valueOf(sentenceStr));
        csvRow.add(query.getPrompt());
        csvRow.add(options.stream().collect(Collectors.joining(answerDelimiter)));
        if (goldOptionIds == null) {
            csvRow.add(""); // _gold
            csvRow.add(""); // choice_gold
            csvRow.add(""); // choice_gold_reason
        } else {
            csvRow.add("TRUE");
            csvRow.add(goldOptionIds.stream().map(options::get).collect(Collectors.joining("\n")));
            csvRow.add(goldReason);
        }
        // Query key.
        csvRow.add(query.isJeopardyStyle() ? answerStructure.toString(sentence) : questionStructure.toString(sentence));
        csvRow.add(String.format("%d", query.isJeopardyStyle() ? 1 : 0));
        csvRow.add(String.format("%.3f", query.getPromptScore()));
        csvRow.add(String.format("%.3f", query.getOptionEntropy()));

        csvPrinter.printRecord(csvRow);
    }

    public static Map<Integer, List<AlignedAnnotation>> loadCrowdflowerAnnotation(String[] fileNames) {
        Map<Integer, List<AlignedAnnotation>> sentenceToAnnotations;
        List<AlignedAnnotation> annotationList = new ArrayList<>();
        try {
            for (String fileName : fileNames) {
                annotationList.addAll(CrowdFlowerDataReader.readAggregatedAnnotationFromFile(fileName));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        sentenceToAnnotations = new HashMap<>();
        annotationList.forEach(annotation -> {
            int sentId = annotation.sentenceId;
            if (!sentenceToAnnotations.containsKey(sentId)) {
                sentenceToAnnotations.put(sentId, new ArrayList<>());
            }
            sentenceToAnnotations.get(sentId).add(annotation);
        });
        return sentenceToAnnotations;
    }

}
