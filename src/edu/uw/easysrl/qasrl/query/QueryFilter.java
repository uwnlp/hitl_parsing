package edu.uw.easysrl.qasrl.query;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAPairSurfaceForm;

/**
 * Created by luheng on 3/26/16.
 */
@FunctionalInterface
public interface QueryFilter<QA extends QAPairSurfaceForm, Q extends Query<QA>> {
    ImmutableList<Q> filter(final ImmutableList<Q> queries,
                            final NBestList nBestList,
                            final QueryPruningParameters queryPruningParameters);
}
