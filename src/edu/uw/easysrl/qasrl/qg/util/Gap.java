package edu.uw.easysrl.qasrl.qg.util;

import java.util.function.BiFunction;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public final class Gap extends Predication {
    public static final String PRED = "'e'";

    @Override
    public ImmutableList<String> getPhrase(Category desiredCategory) {
        // TODO make this check more stringent
        assert desiredCategory.matches(getPredicateCategory()) || getPredicateCategory().matches(desiredCategory)
            : "gap category has to match desired category";
        return words;
    }

    @Override
    public ImmutableSet<ResolvedDependency> getLocalDependencies() {
        return ImmutableSet.of();
    }

    @Override
    public Gap transformArgs(BiFunction<Integer, ImmutableList<Argument>, ImmutableList<Argument>> transform) {
        return this;
    }

    public Gap(Category predicateCategory) {
        super(PRED, predicateCategory, ImmutableMap.of());
        this.words = ImmutableList.of();
    }

    // for debugging purposes;
    public Gap(Category predicateCategory, String word) {
        super(PRED, predicateCategory, ImmutableMap.of());
        this.words = ImmutableList.of(word);
    }

    private final ImmutableList<String> words;
}
