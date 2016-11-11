package edu.uw.easysrl.qasrl.qg.util;

import com.google.common.collect.ImmutableSet;

/**
 * Partitive genitive words from the paper Rebanking CCGbank for improved NP interpretation.
 * missing "many"
 * Created by luheng on 5/5/16.
 */
public class Partitive {
    public static final ImmutableSet<String> tokens = ImmutableSet.of(
            "all", "another", "average", "both", "each", "another", "any",
            "anything", "both", "certain", "each", "either", "enough", "few",
            "little", "many", "most", "much", "neither", "nothing", "other", "part",
            "plenty", "several", "some", "something", "that", "those"
    );
}
