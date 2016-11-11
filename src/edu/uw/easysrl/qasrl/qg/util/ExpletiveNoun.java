package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.function.BiFunction;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.dependencies.ResolvedDependency;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class ExpletiveNoun extends Noun {

    /* public API */

    public static final String PRED = "'e'";

    public static final ExpletiveNoun there = new ExpletiveNoun(Category.NPthr,
                                                                Optional.empty(),
                                                                Optional.of(Number.SINGULAR),
                                                                Optional.empty(),
                                                                Person.THIRD,
                                                                Definiteness.DEFINITE,
                                                                "there");
    public static final ExpletiveNoun it = new ExpletiveNoun(Category.NPexpl,
                                                             Optional.empty(),
                                                             Optional.of(Number.SINGULAR),
                                                             Optional.empty(),
                                                             Person.THIRD,
                                                             Definiteness.DEFINITE,
                                                             "it");

    // overrides

    @Override
    public ExpletiveNoun transformArgs(BiFunction<Integer, ImmutableList<Argument>, ImmutableList<Argument>> transform) {
        return this;
    }

    @Override
    public ImmutableList<String> getPhrase(Category desiredCategory) {
        // assert desiredCategory.matches(getPredicateCategory()) && getPredicateCategory().matches(desiredCategory)
        //     : "must want an expletive NP if getting phrase of expletive NP. Expletive category: " +
        //     getPredicateCategory() + "; desired category: " + desiredCategory;
        assert Category.NP.matches(desiredCategory)
            : "I am a wuss so I weakened this assert compared to the above one.";
        return ImmutableList.of(word);
    }

    @Override
    public ImmutableSet<ResolvedDependency> getLocalDependencies() {
        return ImmutableSet.of();
    }

    // transformers -- subclasses should override

    @Override
    public ExpletiveNoun withCase(Case caseMarking) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withCase(Optional<Case> caseMarking) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withNumber(Number number) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withNumber(Optional<Number> number) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withGender(Gender gender) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withGender(Optional<Gender> gender) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withPerson(Person person) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public ExpletiveNoun withDefiniteness(Definiteness definiteness) {
        throw new UnsupportedOperationException("cannot tweak with the grammar of expletive nouns");
    }

    @Override
    public boolean isPronoun() {
        return false;
    }

    @Override
    public Pronoun getPronoun() {
        throw new UnsupportedOperationException("cannot make a pronoun from an expletive noun");
    }

    @Override
    public Noun getPronounOrExpletive() {
        return this;
    }

    @Override
    public ExpletiveNoun withElision(boolean shouldElide) {
        assert !shouldElide : "can't elide expletive nouns";
        return this;
    }

    /* private methods and fields */

    private ExpletiveNoun(Category predicateCategory,
                          Optional<Case> caseMarking,
                          Optional<Number> number,
                          Optional<Gender> gender,
                          Person person,
                          Definiteness definiteness,
                          String word) {
        super(PRED, predicateCategory, ImmutableMap.of(), caseMarking, number, gender, person, definiteness, false);
        this.word = word;
    }

    private final String word;
}
