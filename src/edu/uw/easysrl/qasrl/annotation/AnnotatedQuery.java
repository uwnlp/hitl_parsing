package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;

import static java.lang.Math.toIntExact;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * New cleaned-up class for annotation.
 * Created by luheng on 5/26/16.
 */
public class AnnotatedQuery {
    public int sentenceId, predicateId;
    public String sentenceString, questionString;
    public ImmutableList<String> optionStrings;
    public ImmutableList<ImmutableList<Integer>> responses;

    public AnnotatedQuery() {
    }

    public String toString() {
        return String.format("%d\t%s\n%d\t%s\n%s\n%s", sentenceId, sentenceString, predicateId, questionString,
                optionStrings.stream().collect(Collectors.joining("\n")),
                responses);
    }

    public String toPrettyString() {
        int[] optionDist = new int[optionStrings.size()];
        Arrays.fill(optionDist, 0);
        responses.forEach(chosen -> chosen.forEach(c -> optionDist[c]++));
        String ret = String.format("%d\t%s\n%d\t%s\n", sentenceId, sentenceString, predicateId, questionString);
        ret += IntStream.range(0, optionStrings.size())
                .mapToObj(i -> String.format("%6s\t%s", "*****".substring(0, optionDist[i]), optionStrings.get(i)))
                .collect(Collectors.joining("\n"));
        return ret;
    }

    public ImmutableList<ImmutableList<Integer>> getResponses(final ScoredQuery<QAStructureSurfaceForm> query) {
        final int numOptions = query.getOptions().size();
        return responses.stream()
                .map(response -> {
                    final ImmutableSet<String> choseOptions = response.stream()
                            .map(optionStrings::get)
                            .map(String::toLowerCase)
                            .collect(GuavaCollectors.toImmutableSet());
                    return IntStream.range(0, numOptions)
                            .boxed()
                            .filter(op -> choseOptions.contains(query.getOptions().get(op).toLowerCase()))
                            .collect(GuavaCollectors.toImmutableList());
                })
                .filter(response -> !response.isEmpty())
                .collect(GuavaCollectors.toImmutableList());
    }
}
