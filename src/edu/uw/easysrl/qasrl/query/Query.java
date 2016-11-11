package edu.uw.easysrl.qasrl.query;

import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.syntax.grammar.Category;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * General interface that must be implemented by any query, i.e.,
 * an instance that we show to an annotator.
 *
 * This interface and subclasses of it should be for DATA, not LOGIC.
 *
 * Created by julianmichael on 3/17/2016.
 */
public interface Query<QA extends QAPairSurfaceForm> {
    int getSentenceId();
    String getPrompt();
    ImmutableList<String> getOptions();
    ImmutableList<QA> getQAPairSurfaceForms();
    boolean isJeopardyStyle();
    boolean allowMultipleChoices();

    OptionalInt getBadQuestionOptionId();
    OptionalInt getUnlistedAnswerOptionId();

    // To uniquely identify a query.
    String getQueryKey();

    // Only works for non-jeopardy style.
    OptionalInt getPredicateId();
    Optional<Category> getPredicateCategory();
    OptionalInt getArgumentNumber();

    String toString(ImmutableList<String> sentence, Object ... optionLegends);
}
