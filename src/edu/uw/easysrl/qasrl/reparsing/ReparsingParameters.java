package edu.uw.easysrl.qasrl.reparsing;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * Created by luheng on 5/29/16.
 */
public class ReparsingParameters {
    @Option(name="-pronoun",usage="Fix pronoun")
    boolean fixPronouns = true;

    @Option(name="-subspan",usage="Fix subspans")
    boolean fixSubspans = true;

    @Option(name="-pos_threshold",usage="")
    int positiveConstraintMinAgreement = 3;

    @Option(name="-neg_threshold",usage="")
    int negativeConstraintMaxAgreement = 0;

    @Option(name="-pos_penalty",usage="")
    double positiveConstraintPenalty = 2.0;

    @Option(name="-neg_penalty",usage="")
    double negativeConstraintPenalty = 1.5;

    @Option(name="-tag_penalty",usage="supertag penalty")
    double supertagPenalty = 1.0;


    public ReparsingParameters() {
    }

    public ReparsingParameters(final String[] args) {
        CmdLineParser parser = new CmdLineParser(this);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            // Simply skip invalid options.
        }
    }

    public String toString() {
        return new StringBuilder()
                .append("\nFix pronouns=\t").append(fixPronouns)
                .append("\nFix subspans=\t").append(fixSubspans)
                .append("\nPositive threshold=\t").append(positiveConstraintMinAgreement)
                .append("\nNegative threshold=\t").append(negativeConstraintMaxAgreement)
                .append("\nPositive constraint penalty=\t").append(positiveConstraintPenalty)
                .append("\nNegative constraint penalty=\t").append(negativeConstraintPenalty)
                .append("\nSupertag constraint penalty=\t").append(supertagPenalty)
                .toString();
    }

}
