package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.*;

/**
 * Created by luheng on 2/13/16.
 */
public class AlignedAnnotation extends RecordedAnnotation {
    public Map<String, ImmutableList<Integer>> annotatorToAnswerIds;
    public Map<String, String> annotatorToComment;
    public List<String> answerOptions;
    public int[] answerDist;
    public double[] answerTrust;

    public AlignedAnnotation(RecordedAnnotation annotation) {
        super();
        this.iterationId = annotation.iterationId;
        this.sentenceId = annotation.sentenceId;
        this.sentenceString = annotation.sentenceString;
        this.predicateId = annotation.predicateId;
        this.argumentNumber = annotation.argumentNumber;
        this.predicateCategory = annotation.predicateCategory;
        this.predicateString = annotation.predicateString;
        this.queryId = annotation.queryId;
        this.queryPrompt = annotation.queryPrompt;
        this.optionStrings = annotation.optionStrings;
        this.userOptionIds = annotation.userOptionIds;
        this.goldOptionIds = annotation.goldOptionIds;
        annotatorToAnswerIds = new HashMap<>();
        annotatorToComment = new HashMap<>();
        answerDist = new int[optionStrings.size()];
        answerTrust = new double[optionStrings.size()];
        Arrays.fill(answerDist, 0);
        Arrays.fill(answerTrust, 0.0);
    }

    boolean addAnnotation(String annotator, RecordedAnnotation annotation) {
        // TODO: check options are the same.
        if (answerOptions == null) {
            answerOptions = annotation.optionStrings;
        }
        if (goldOptionIds == null) {
            goldOptionIds = annotation.goldOptionIds;
        }
        // Some annotation records may contain duplicates.
        //if (this.isSameQuestionAs(annotation) && !annotatorToAnswerIds.containsKey(annotator)) {
        if (!annotatorToAnswerIds.containsKey(annotator)) {
            annotatorToAnswerIds.put(annotator, annotation.userOptionIds);
            annotation.userOptionIds.forEach(answerId -> {
                answerDist[answerId]++;
                answerTrust[answerId] += annotation.trust;
            });
            if (annotation.comment != null && !annotation.comment.isEmpty()) {
                annotatorToComment.put(annotator, annotation.comment);
            }
            return true;
        }
        return false;
    }

    int getNumAnnotated() {
        return annotatorToAnswerIds.size();
    }

    public static List<AlignedAnnotation> getAlignedAnnotations(List<RecordedAnnotation> annotations) {
        Map<String, AlignedAnnotation> alignedAnnotations = new HashMap<>();
        annotations.forEach(annotation -> {
            String queryKey = "SID=" + annotation.sentenceId
                    + "_PRED=" + annotation.predicateId
                    + "_Q=" + annotation.queryPrompt
                    + "_QID=" + annotation.queryId;
            if (!alignedAnnotations.containsKey(queryKey)) {
                alignedAnnotations.put(queryKey, new AlignedAnnotation(annotation));
            }
            AlignedAnnotation alignedAnnotation = alignedAnnotations.get(queryKey);
            alignedAnnotation.addAnnotation(annotation.annotatorId, annotation);
        });
        return new ArrayList<>(alignedAnnotations.values());
    }

    @Override
    public String toString() {
        // Number of iteration in user session.
        String result = "ITER=" + iterationId + "\n"
                + "SID=" + sentenceId + "\t" + sentenceString + "\n"
                + "PRED=" + predicateId + "\t" + predicateString + "\t" + predicateCategory + "." + argumentNumber + "\n"
                + "QID=" + queryId + "\t" + queryPrompt + "\n";
        for (int i = 0; i < optionStrings.size(); i++) {
            String match = "";
            for (int j = 0; j < answerDist[i]; j++) {
                match += "*";
            }
            if (goldOptionIds != null && goldOptionIds.contains(i)) {
                match += "G";
            }
            result += String.format("%-8s\t%d\t%s\n", match, i, optionStrings.get(i));
        }
        for (String annotator : annotatorToComment.keySet()) {
            result += annotator + ":\t" + annotatorToComment.get(annotator) + "\n";
        }
        result += "\n";
        return result;
    }
}
