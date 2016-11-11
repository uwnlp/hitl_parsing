package edu.uw.easysrl.qasrl.qg.surfaceform;

import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.*;

import com.google.common.collect.ImmutableList;

/**
 * QAPairSurfaceForm encapsulates the method by which many QuestionAnswerPairs
 * are aggregated together to use a single common string form.
 * For example, we might aggregate by exact string match,
 * or we might aggregate by target dependency.
 *
 * In the case of, for example, aggregating by target dependency,
 * we would want to store some extra information in the QAPairSurfaceForm
 * so we could use it in analysis.
 *
 * That is the purpose of classes that implement this interface:
 * to provide the API necessary to reason about surface forms
 * arrived at by various aggregation methods.
 *
 * NOTE: QAPairSurfaceForm and its implementors will just be DATA.
 * They will NOT encode BUSINESS LOGIC.
 * Logic will be handled in the processes of QAPairAggregator and QAPairAnalyzer.
 *
 * Created by julianmichael on 3/17/2016.
 */
public interface QAPairSurfaceForm {
    int getSentenceId();
    String getQuestion();
    String getAnswer();
    ImmutableList<QuestionAnswerPair> getQAPairs();
    boolean canBeGeneratedBy(Parse parse);
}
