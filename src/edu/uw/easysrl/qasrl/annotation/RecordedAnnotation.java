package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.util.DebugPrinter;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.ArrayList;
import java.util.List;

/**
 * Annotation record from a single annotator.
 * Created by luheng on 2/12/16.
 */
public class RecordedAnnotation {
    // Number of iteration in user session.
    public int iterationId, sentenceId;
    public String sentenceString;

    // Predicate information
    public int predicateId, argumentNumber;
    public String predicateString;
    public Category predicateCategory;

    // Question information.
    public int queryId;
    public String queryPrompt;

    // Answer information
    List<String> optionStrings;

    // Answer information, compatible with checkbox version.
    ImmutableList<String> userOptions;
    ImmutableList<Integer> userOptionIds, goldOptionIds;

    // Current accuracy
    double rerankF1, oracleF1, onebestF1;

    // Crowdflower computed stuff.
    double trust;

    // Other
    public String annotatorId;
    public String comment;

    protected RecordedAnnotation() {
        optionStrings = new ArrayList<>();
    }

    @Override
    public String toString() {
        // Number of iteration in user session.
        String result = "ITER=" + iterationId + "\n"
                + "SID=" + sentenceId + "\t" + sentenceString + "\n"
                + "PRED=" + predicateId + "\t" + predicateString + "\t" + predicateCategory + "." + argumentNumber + "\n"
                + "QID=" + queryId + "\t" + queryPrompt + "\n"
                + "ANS=" + DebugPrinter.getShortListString(userOptionIds) + "\n"
                + "GOLD=" + DebugPrinter.getShortListString(goldOptionIds) + "\n";
        for (int i = 0; i < optionStrings.size(); i++) {
            result += i + "\t" + optionStrings.get(i) + "\n";
        }
        //result += String.format("1B=%.3f\tRR=%.3f\tOR=%.3f", onebestF1, rerankF1, oracleF1) + "\n";
        result += comment + "\n";
        return result;
    }
}