package edu.uw.easysrl.qasrl.query;

import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAPairSurfaceForm;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;

import java.util.Collection;
import java.util.stream.IntStream;

/**
 * Created by luheng on 3/22/16.
 */
public class QueryGeneratorUtils {
    public static String kBadQuestionOptionString = "Bad Question.";
    public static String kUnlistedAnswerOptionString = "Answer is not listed.";
    public static String kNoneApplicableString = "None of the above.";
    public static String kOldBadQuestionOptionString = "Question is not valid.";

    static ImmutableSet<Integer> getParseIdsForQAPair(final QAStructureSurfaceForm qaPair,
                                                      final NBestList nBestList) {
        return IntStream.range(0, nBestList.getN())
                .filter(i -> qaPair.canBeGeneratedBy(nBestList.getParse(i)))
                .boxed()
                .collect(GuavaCollectors.toImmutableSet());
    }

    static double computeEntropy(Collection<Double> scores) {
        final double sum = scores.stream().mapToDouble(s -> s).sum();
        return 0.0 - scores.stream()
                .mapToDouble(s -> s / sum)
                .filter(p -> p > 0)
                .map(p -> p * Math.log(p))
                .sum() / Math.log(2);
    }

    static String getQueryKey(final QAStructureSurfaceForm qa) {
        return qa.getSentenceId() + "\t" + qa.getPredicateIndex() + "\t" + qa.getQuestion();
    }

    static boolean isNAOption(final String optionStr) {
        return optionStr.equals(kBadQuestionOptionString) || optionStr.equals(kNoneApplicableString) ||
                optionStr.equals(kOldBadQuestionOptionString);
    }
}
