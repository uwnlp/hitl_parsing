package edu.uw.easysrl.qasrl.query;

import edu.uw.easysrl.qasrl.qg.surfaceform.*;

import com.google.common.collect.ImmutableList;

/**
 * A QueryAggregator governs how QAPairSurfaceForms are combined together into Queries.
 * Examples of how this may vary include:
 *  - how many answers to include
 *  - whether to filter out certain questions based on the presence of answers
 *  - confidence thresholds for questions or answers
 *  - including additional answers like "Bad Question."
 *
 * Created by julianmichael on 3/17/16.
 */
@FunctionalInterface
public interface QueryGenerator<QA extends QAPairSurfaceForm, Q extends Query<QA>> {
    ImmutableList<Q> generate(ImmutableList<QA> qaPairs);
}
