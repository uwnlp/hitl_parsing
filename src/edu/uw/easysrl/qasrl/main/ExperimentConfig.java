package edu.uw.easysrl.qasrl.main;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * Configuration for main experiments.
 * Created by luheng on 10/1/16.
 */
class ExperimentConfig {
    @Option(name="-ccg-test",usage="Run on CCG Test")
    boolean runCcgTest = false;

    @Option(name="-bioinfer",usage="Run with Bioinfer setup.")
    boolean runBioinfer = false;

    @SuppressWarnings("unused")
    public ExperimentConfig() {
    }

    ExperimentConfig(final String[] args) {
        CmdLineParser parser = new CmdLineParser(this);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            // Simply skip invalid options.
        }
    }
}
