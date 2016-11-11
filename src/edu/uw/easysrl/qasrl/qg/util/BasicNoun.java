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

public class BasicNoun extends Noun {

    /* public API */

    // overrides

    @Override
    public ImmutableList<String> getPhrase(Category desiredCategory) {
        // TODO verify that we want an NP out of it.
        if(isElided()) {
            return ImmutableList.of();
        } else {
            return words;
        }
    }

    @Override
    public ImmutableSet<ResolvedDependency> getLocalDependencies() {
        return deps;
    }

    @Override
    public BasicNoun transformArgs(BiFunction<Integer, ImmutableList<Argument>, ImmutableList<Argument>> transform) {
        return this;
    }

    // transformers -- subclasses should override

    @Override
    public BasicNoun withCase(Case caseMarking) {
        return this.withCase(Optional.of(caseMarking));
    }

    @Override
    public BasicNoun withCase(Optional<Case> caseMarking) {
        return new BasicNoun(getPredicate(), getPredicateCategory(), getArgs(),
                             getCase(), getNumber(), getGender(), getPerson(), getDefiniteness(), words, deps, isElided());
    }

    @Override
    public BasicNoun withNumber(Number number) {
        return this.withNumber(Optional.of(number));
    }

    @Override
    public BasicNoun withNumber(Optional<Number> number) {
        return new BasicNoun(getPredicate(), getPredicateCategory(), getArgs(),
                             getCase(), number, getGender(), getPerson(), getDefiniteness(), words, deps, isElided());
    }

    @Override
    public BasicNoun withGender(Gender gender) {
        return this.withGender(Optional.of(gender));
    }

    @Override
    public BasicNoun withGender(Optional<Gender> gender) {
        return new BasicNoun(getPredicate(), getPredicateCategory(), getArgs(),
                             getCase(), getNumber(), gender, getPerson(), getDefiniteness(), words, deps, isElided());
    }

    @Override
    public BasicNoun withPerson(Person person) {
        return new BasicNoun(getPredicate(), getPredicateCategory(), getArgs(),
                             getCase(), getNumber(), getGender(), person, getDefiniteness(), words, deps, isElided());
    }

    @Override
    public BasicNoun withDefiniteness(Definiteness definiteness) {
        return new BasicNoun(getPredicate(), getPredicateCategory(), getArgs(),
                             getCase(), getNumber(), getGender(), getPerson(), definiteness, words, deps, isElided());
    }

    @Override
    public boolean isPronoun() {
        return false;
    }

    @Override
    public Pronoun getPronoun() {
        return new Pronoun(getCase(), getNumber(), getGender(), getPerson(), getDefiniteness(), isElided());
    }

    @Override
    public Noun getPronounOrExpletive() {
        return getPronoun();
    }

    @Override
    public BasicNoun withElision(boolean shouldElide) {
        return new BasicNoun(getPredicate(), getPredicateCategory(), getArgs(),
                             getCase(), getNumber(), getGender(), getPerson(), getDefiniteness(), words, deps, shouldElide);
    }

    /* protected methods and fields */

    protected BasicNoun(String predicate,
                        Category predicateCategory,
                        ImmutableMap<Integer, ImmutableList<Argument>> args,
                        Optional<Case> caseMarking,
                        Optional<Number> number,
                        Optional<Gender> gender,
                        Person person,
                        Definiteness definiteness,
                        ImmutableList<String> words,
                        ImmutableSet<ResolvedDependency> deps,
                        boolean isElided) {
        super(predicate, predicateCategory, args, caseMarking, number, gender, person, definiteness, isElided);
        this.words = words;
        this.deps = deps;
    }

    /* private fields */

    private final ImmutableList<String> words;
    private final ImmutableSet<ResolvedDependency> deps;
}
