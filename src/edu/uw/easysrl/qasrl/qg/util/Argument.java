package edu.uw.easysrl.qasrl.qg.util;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import java.util.Optional;

public final class Argument {
    private Optional<ResolvedDependency> depOpt;
    private Predication pred;

    public Optional<ResolvedDependency> getDependency() { return depOpt; }
    public Predication getPredication() { return pred; }

    public Argument(Optional<ResolvedDependency> depOpt, Predication pred) {
        this.depOpt = depOpt;
        this.pred = pred;
    }

    public static Argument withNoDependency(Predication pred) {
        return new Argument(Optional.empty(), pred);
    }

    @Override
    public String toString() {
        return String.format("(%s, %s)", depOpt, pred.getPredicate());
    }
}
