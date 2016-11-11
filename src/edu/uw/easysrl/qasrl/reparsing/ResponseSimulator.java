package edu.uw.easysrl.qasrl.reparsing;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;

public interface ResponseSimulator {
    ImmutableList<Integer> respondToQuery(ScoredQuery<QAStructureSurfaceForm> query);
}
