package edu.uw.easysrl.qasrl.query;

/**
 * Created by luheng on 3/5/16.
 */
public class QueryPruningParameters {
    public double minPromptConfidence = 0.1;
    public double minOptionConfidence = 0.05;
    public double minOptionEntropy = 0.05;

    // Not including "Bad queryPrompt" or "Answer unlisted" option.
    public int maxNumOptionsPerQuery = 6;

    public boolean skipBinaryQueries = true;
    public boolean skipSAdjQuestions = true;
    public boolean skipPPQuestions = false;
    public boolean skipQueriesWithPronounOptions = false;

    public QueryPruningParameters() {
    }

    public QueryPruningParameters(QueryPruningParameters other) {
        this.minPromptConfidence = other.minPromptConfidence;
        this.minOptionConfidence = other.minOptionConfidence;
        this.minOptionEntropy = other.minOptionEntropy;
        this.maxNumOptionsPerQuery = other.maxNumOptionsPerQuery;
        this.skipBinaryQueries = other.skipBinaryQueries;
        this.skipPPQuestions = other.skipPPQuestions;
        this.skipQueriesWithPronounOptions = other.skipQueriesWithPronounOptions;
        this.skipSAdjQuestions = other.skipSAdjQuestions;
    }

    public String toString() {
        // TODO
        return "";
    }

}