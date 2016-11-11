package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Reads AlignedAnnotation from Crowdflower.
 * Created by luheng on 2/24/16.
 */
public class CrowdFlowerDataReader {
    public static ImmutableList<AlignedAnnotation> readAggregatedAnnotationFromFile(String filePath) throws IOException {
        final Iterable<CSVRecord> records = CSVFormat.DEFAULT.withHeader().parse(new FileReader(filePath));
        List<RecordedAnnotation> annotations = new ArrayList<>();
        for (CSVRecord record : records) {
            // Skip gold (test questions).
            if (record.get("_golden").equalsIgnoreCase("true") ||
                    (record.isMapped("orig_golden") && record.get("orig_golden").equalsIgnoreCase("true"))) {
                continue;
            }
            RecordedAnnotation annotation = new RecordedAnnotation();
            annotation.iterationId = -1; // unknown
            annotation.sentenceId = Integer.parseInt(record.get("sent_id"));
            annotation.sentenceString = record.get("sentence");

            if (record.isMapped("pred_id")) {
                annotation.predicateId = Integer.parseInt(record.get("pred_id"));
                annotation.predicateString = record.get("pred_head");
                final String[] qkeyInfo = record.get("question_key").split("\\.");
                annotation.predicateCategory = Category.valueOf(qkeyInfo[1]);
                annotation.argumentNumber = parseIntOrElse(qkeyInfo[2], -1);
                // Do not include pp questions in agreement count.
                if (AnnotationUtils.propositionalCategories.contains(annotation.predicateCategory)) {
                    continue;
                }
            } else {
                // 2:is_(S[dcl]\NP)/NP.2
                String qkey = record.get("query_key");
                annotation.predicateId = Integer.parseInt(qkey.split(":")[0]);
                String qkey2 = qkey.split("_")[1].split("\\s+")[0];
                annotation.predicateCategory = Category.valueOf(qkey2.split("\\.")[0]);
                annotation.argumentNumber = Integer.parseInt(qkey2.split("\\.")[1]);
            }

            annotation.queryId = Integer.parseInt(record.get("query_id"));
            annotation.queryPrompt = record.isMapped("question") ? record.get("question") : record.get("query_prompt");

            String[] options = record.isMapped("answers") ?
                    record.get("answers").split("\n") :
                    record.get("options").split("\n");
            annotation.optionStrings = ImmutableList.copyOf(options)
                    .stream()
                    .map(String::trim)
                    .collect(Collectors.toList());
            annotation.userOptions = ImmutableList.copyOf(record.get("choice").split("\n"))
                    .stream()
                    .map(String::trim)
                    .collect(GuavaCollectors.toImmutableList());
            annotation.userOptionIds = IntStream.range(0, annotation.optionStrings.size())
                    .boxed()
                    .filter(id -> annotation.userOptions.contains(annotation.optionStrings.get(id)))
                    .collect(GuavaCollectors.toImmutableList());

            if (annotation.userOptionIds.size() == 0) {
                System.err.print("Unannotated:\t" + record);
            }
            annotation.goldOptionIds = null; /* no gold */
            annotation.comment = record.get("comment");

            // Crowdflower stuff
            annotation.annotatorId = record.get("_worker_id");
            annotation.trust = Double.parseDouble(record.get("_trust"));
            annotations.add(annotation);
        }
        System.out.println(String.format("Read %d annotation records from %s.", annotations.size(), filePath));

        // Align and aggregated annotations.
        List<AlignedAnnotation> alignedAnnotations = AlignedAnnotation.getAlignedAnnotations(annotations);
        System.out.println("Getting " + alignedAnnotations.size() + " aligned annotations.");
        int maxNumAnnotators = alignedAnnotations.stream()
                .map(AlignedAnnotation::getNumAnnotated)
                .max(Integer::compare).get();
        int[] agreementCount = new int[maxNumAnnotators + 1],
              strictAgreementCount = new int[maxNumAnnotators + 1];
        Arrays.fill(agreementCount, 0);
        Arrays.fill(strictAgreementCount, 0);

        alignedAnnotations.forEach(annotation -> {
            //if (annotation.getNumAnnotated() != 5) {
            //    System.err.println(annotation);
            //}
            int maxAgree = 0;
            for (int agr : annotation.answerDist) {
                maxAgree = Math.max(maxAgree, agr);
            }
            agreementCount[maxAgree] ++;
            final int strictAgreement = annotation.annotatorToAnswerIds.values().stream()
                    .collect(Collectors.groupingBy(Function.identity()))
                    .values().stream()
                    .map(Collection::size)
                    .max(Integer::compare).get();
            strictAgreementCount[strictAgreement] ++;
        });

        System.out.println("Agreement:");
        for (int i = 0; i < agreementCount.length; i++) {
            if (agreementCount[i] > 0) {
                System.out.println(String.format("%d\t%d\t%.2f%%", i, agreementCount[i],
                        100.0 * agreementCount[i] / alignedAnnotations.size()));
            }
        }
        System.out.println("Strict Agreement:");
        for (int i = 0; i < strictAgreementCount.length; i++) {
            if (strictAgreementCount[i] > 0) {
                System.out.println(String.format("%d\t%d\t%.2f%%", i, strictAgreementCount[i],
                        100.0 * strictAgreementCount[i] / alignedAnnotations.size()));
            }
        }

        return ImmutableList.copyOf(alignedAnnotations);
    }

    private static int parseIntOrElse(final String toParse, int elseInt) {
        try {
            return Integer.parseInt(toParse);
        } catch (NumberFormatException e) {
            return elseInt;
        }
    }
}
