package edu.uw.easysrl.qasrl.main;

import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.annotation.AnnotationFileLoader;
import edu.uw.easysrl.qasrl.util.RandomUtil;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Analysis for inter-annotator agreement and question quality.
 * Created by luheng on 9/6/16.
 */
public class AnnotationQualityAnalysis {

    private static void printInterAnnotatorAgreement(final Map<Integer, List<AnnotatedQuery>> annotations) {
        int[] agreementCount = new int[6],
                strictAgreementCount = new int[6];
        Arrays.fill(agreementCount, 0);
        Arrays.fill(strictAgreementCount, 0);
        int numTotalAnnotations = 0;
        for  (int sentenceId : annotations.keySet()) {
            for (AnnotatedQuery annotation : annotations.get(sentenceId)) {
                numTotalAnnotations ++;
                int[] optionDist = new int[annotation.optionStrings.size()];
                Arrays.fill(optionDist, 0);
                annotation.responses.forEach(res -> res.forEach(r -> optionDist[r] ++));

                int maxAgree = 0;
                for (int agr : optionDist) {
                    maxAgree = Math.max(maxAgree, agr);
                }
                agreementCount[maxAgree] ++;
                final int strictAgreement = annotation.responses.stream()
                        .collect(Collectors.groupingBy(Function.identity()))
                        .values().stream()
                        .map(Collection::size)
                        .max(Integer::compare).get();
                strictAgreementCount[strictAgreement] ++;
            }
        }
        System.out.println("Agreement:");
        for (int i = 0; i < agreementCount.length; i++) {
            if (agreementCount[i] > 0) {
                System.out.println(String.format("%d\t%d\t%.2f%%", i, agreementCount[i],
                        100.0 * agreementCount[i] / numTotalAnnotations));
            }
        }
        System.out.println("Strict Agreement:");
        for (int i = 0; i < strictAgreementCount.length; i++) {
            if (strictAgreementCount[i] > 0) {
                System.out.println(String.format("%d\t%d\t%.2f%%", i, strictAgreementCount[i],
                        100.0 * strictAgreementCount[i] / numTotalAnnotations));
            }
        }
    }

    private static void printQuestionQuality(final Map<Integer, List<AnnotatedQuery>> annotations,
                                             int numQueriesToSample) {
        int numQueries = 0;
        int[] numQueriesWithNA = new int[6];
        Arrays.fill(numQueriesWithNA, 0);
        ArrayList<String> queryPool = new ArrayList<>();
        for  (int sentenceId : annotations.keySet()) {
            for (AnnotatedQuery annotation : annotations.get(sentenceId)) {
                int[] optionDist = new int[annotation.optionStrings.size()];
                Arrays.fill(optionDist, 0);
                annotation.responses.forEach(res -> res.forEach(r -> optionDist[r] ++));
                int naOptionId = annotation.optionStrings.size() - 1;
                int numNAVotes = optionDist[naOptionId];
                numQueries ++;
                numQueriesWithNA[numNAVotes] ++;
                if (numNAVotes > 1 && numQueriesToSample > 0) {
                    queryPool.add(annotation.toPrettyString() + "\n");
                }
            }
        }
        if (numQueriesToSample > 0) {
            Collections.shuffle(queryPool, new Random(RandomUtil.randomSeed));
            queryPool.stream()
                    .limit(numQueriesToSample)
                    .forEach(System.out::println);
        }
        System.out.println("Number of queries:\t" + numQueries);
        int cumulative = 0;
        for (int i = 0; i < 6; i++) {
            cumulative += numQueriesWithNA[i];
            System.out.println(String.format("%d queries (%.2f%%) with %d- N/A votes.", cumulative,
                    100.0 * cumulative / numQueries, i));
        }
        System.out.println();
    }

    public static void main(String[] args) {
        Map<Integer, List<AnnotatedQuery>> ccgDevAnnotations = AnnotationFileLoader.loadCCGDev();
        printInterAnnotatorAgreement(ccgDevAnnotations);
        printQuestionQuality(ccgDevAnnotations, 0);

        Map<Integer, List<AnnotatedQuery>> ccgTestAnnotations = AnnotationFileLoader.loadCCGTest();
        printInterAnnotatorAgreement(ccgTestAnnotations);
        printQuestionQuality(ccgTestAnnotations, 0);

        Map<Integer, List<AnnotatedQuery>> bioinferAnnotations = AnnotationFileLoader.loadBioinfer();
        printInterAnnotatorAgreement(bioinferAnnotations);
        printQuestionQuality(bioinferAnnotations, 0);
    }
}
