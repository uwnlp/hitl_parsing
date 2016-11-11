package edu.uw.easysrl.qasrl.qg;

import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import static edu.uw.easysrl.qasrl.qg.QAPairAggregatorUtils.*;
import edu.uw.easysrl.qasrl.qg.syntax.AnswerStructure;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import static edu.uw.easysrl.qasrl.util.GuavaCollectors.*;
import static java.util.stream.Collectors.*;

import java.util.*;

/**
 * Helper class where we put all of our useful QAPairAggregator instances
 * (which in general may be polymorphic over subtypes of QAPairSurfaceForm).
 *
 * This class is for LOGIC, NOT DATA.
 *
 * Created by julianmichael on 3/17/2016.
 */
public final class QAPairAggregators {

    /**
     * Demonstration of how aggregator works.
     * @return
     */
    @SuppressWarnings("unused")
    public static QAPairAggregator<QAPairSurfaceForm> aggregateByString() {
        return qaPairs -> qaPairs
            .stream()
            .collect(groupingBy(QuestionAnswerPair::getQuestion))
            .entrySet()
            .stream()
            .flatMap(eQuestion -> eQuestion.getValue()
                    .stream()
                    .collect(groupingBy(QuestionAnswerPair::getAnswer))
                    .entrySet()
                    .stream()
                    .map(eAnswer -> {
                        assert eAnswer.getValue().size() > 0
                                : "list in group should always be nonempty";
                        int sentenceId = eAnswer.getValue().get(0).getSentenceId();
                        return new BasicQAPairSurfaceForm(sentenceId,
                                eQuestion.getKey(),
                                eAnswer.getKey(),
                                ImmutableList.copyOf(eAnswer.getValue()));
                    }))
            .collect(toImmutableList());
    }

    /**
     * The input should be all the queryPrompt-answer pairs given a sentence and its n-best list.
     * Each aggregated answer is single headed.
     * @return Aggregated QA pairs with structure information.
     */
    public static QAPairAggregator<QAStructureSurfaceForm>  aggregateForMultipleChoiceQA() {
        return qaPairs ->  qaPairs
                .stream()
                .collect(groupingBy(QuestionAnswerPair::getPredicateIndex))
                .values().stream()
                .flatMap(samePredicateQAs -> samePredicateQAs
                        .stream()
                        .collect(groupingBy(QAPairAggregatorUtils::getQuestionLabelString))
                        .values().stream()
                        .map(QAPairAggregatorUtils::getQuestionSurfaceFormToStructure)
                        .collect(groupingBy(qs2s -> qs2s.question))
                        .values().stream()
                        .flatMap(qs2sEntries -> qs2sEntries
                                .stream()
                                .flatMap(qs -> qs.qaList.stream())
                                .collect(groupingBy(QuestionAnswerPair::getArgumentIndex))
                                .values().stream()
                                .map(QAPairAggregatorUtils::getAnswerSurfaceFormToSingleHeadedStructure)
                                .collect(groupingBy(as2s -> as2s.answer))
                                .values().stream()
                                .map(as2sEntries -> getQAStructureSurfaceForm(qs2sEntries, as2sEntries))
                        )
                ).collect(toImmutableList());
    }


    private QAPairAggregators() {
        throw new AssertionError("no instance.");
    }
}
