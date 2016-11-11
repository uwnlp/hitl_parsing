package edu.uw.easysrl.qasrl.qg.util;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.function.BiFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static java.util.stream.Collectors.*;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.TextGenerationHelper;
import static edu.uw.easysrl.qasrl.util.GuavaCollectors.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * Convenience class for predicate--argument structures.
 *
 * Created by julianmichael on 3/18/16.
 */
public abstract class Predication {

    public static enum Type {
        CLAUSE(Category.S),
        VERB(Category.valueOf("S\\NP")),
        NOUN(Category.NP),
        PREPOSITION(Category.PP),
        ADVERB(Category.ADVERB);

        public Category getTypicalCategory() {
            return typicalCategory;
        }

        public static Optional<Type> getTypeForArgCategory(Category category) {
            if(Category.valueOf("S\\NP").matches(category)) {
                return Optional.of(VERB);
            } else if(Category.valueOf("(S\\NP)\\(S\\NP)").matches(category)) {
                return Optional.of(ADVERB);
            } else if(Category.S.matches(category)) {
                return Optional.of(CLAUSE);
            } else if(Category.NP.matches(category) || Category.N.matches(category)) {
                return Optional.of(NOUN);
            } else if(category.isFunctionInto(Category.PP)) {
                return Optional.of(PREPOSITION);
            } else {
                // TODO low-pri. special modifiers and other things.
                // System.err.println("No predication type exists for category: " + category);
                return Optional.empty();
            }
        }

        private final Category typicalCategory;

        private Type(Category typicalCategory) {
            this.typicalCategory = typicalCategory;
        }

    }

    /* public API */

    public final String getPredicate() {
        return predicate;
    }

    public final Category getPredicateCategory() {
        return predicateCategory;
    }

    public ImmutableMap<Integer, ImmutableList<Argument>> getArgs() {
        if(args == null) {
            args = argSupplier.get();
        }
        return args;
    }

    public ImmutableSet<ResolvedDependency> getAllDependencies() {
        return new ImmutableSet.Builder<ResolvedDependency>()
            .addAll(this.getLocalDependencies())
            .addAll(getArgs().entrySet().stream()
                    .flatMap(e -> e.getValue().stream()
                    .map(arg -> arg.getDependency()))
                    .filter(Optional::isPresent).map(Optional::get)
                    .collect(toImmutableSet()))
            .addAll(getArgs().entrySet().stream()
                    .flatMap(e -> e.getValue().stream()
                    .flatMap(arg -> arg.getPredication().getAllDependencies().stream()))
                    .collect(toImmutableSet()))
            .build();
    }

    /* abstract methods */

    public abstract Predication transformArgs(BiFunction<Integer, ImmutableList<Argument>, ImmutableList<Argument>> transform);

    public abstract ImmutableSet<ResolvedDependency> getLocalDependencies();

    public abstract ImmutableList<String> getPhrase(Category desiredCategory);

    /* protected methods and fields */

    protected static ImmutableMap<Integer, ImmutableList<Argument>> extractArgs(int headIndex, Category headCategory, Parse parse, PredicateCache preds) {
        return IntStream
            .range(1, headCategory.getNumberOfArguments() + 1)
            .boxed()
            .collect(toImmutableMap(argNum -> argNum, argNum -> {
                        ImmutableSet<ResolvedDependency> outgoingDeps = parse.dependencies.stream()
                            .filter(dep -> dep.getHead() == headIndex && dep.getArgument() != headIndex && argNum == dep.getArgNumber())
                            .collect(toImmutableSet());
                        if(headCategory.getArgument(argNum).matches(Category.NP)) {
                            return outgoingDeps.stream().collect(groupingBy(dep -> {
                                        return TextGenerationHelper.getLowestAncestorSatisfyingPredicate(parse.syntaxTree.getLeaves().get(dep.getArgument()),
                                                                                                         node -> node.getCategory().matches(Category.NP),
                                                                                                         parse.syntaxTree);})
                                ).entrySet().stream()
                                .flatMap(e -> {
                                        if(e.getValue().size() > 1) {
                                            ResolvedDependency lastDep = e.getValue().stream()
                                                .collect(maxBy((dep1, dep2) -> Integer.compare(dep1.getArgument(), dep2.getArgument())))
                                                .get();
                                            return Stream.of(new Argument(Optional.of(lastDep), Noun.getFromParse(lastDep.getArgument(), parse, e.getKey())));
                                        } else {
                                            ResolvedDependency argDep = e.getValue().get(0);
                                            return Stream.of(Predication.Type.getTypeForArgCategory(headCategory.getArgument(argDep.getArgNumber())))
                                                .filter(Optional::isPresent).map(Optional::get)
                                                .map(predType -> new Argument(Optional.of(argDep), preds.getPredication(argDep.getArgument(), predType)));
                                        }
                                    })
                            .collect(toImmutableList());
                        } else {
                            return outgoingDeps.stream()
                                .flatMap(argDep -> Stream.of(Predication.Type.getTypeForArgCategory(headCategory.getArgument(argDep.getArgNumber())))
                                         .filter(Optional::isPresent).map(Optional::get)
                                         .map(predType -> new Argument(Optional.of(argDep), preds.getPredication(argDep.getArgument(), predType))))
                                .collect(toImmutableList());
                        }
                    }));
    }

    protected final ImmutableMap<Integer, ImmutableList<Argument>>
        transformArgsAux(BiFunction<Integer, ImmutableList<Argument>, ImmutableList<Argument>> transform) {
        return getArgs().entrySet().stream()
            .collect(toImmutableMap(e -> e.getKey(), e -> (transform.apply(e.getKey(), e.getValue()))));
    }

    protected Predication(String predicate,
                          Category predicateCategory,
                          Supplier<ImmutableMap<Integer, ImmutableList<Argument>>> argSupplier) {
        this.predicate = predicate;
        this.predicateCategory = predicateCategory;
        this.argSupplier = argSupplier;
        this.args = null;
    }

    protected Predication(String predicate,
                          Category predicateCategory,
                          ImmutableMap<Integer, ImmutableList<Argument>> args) {
        this.predicate = predicate;
        this.predicateCategory = predicateCategory;
        this.argSupplier = null;
        this.args = args;
    }

    /* private fields */

    private final String predicate;
    private final Category predicateCategory;
    private final Supplier<ImmutableMap<Integer, ImmutableList<Argument>>> argSupplier;
    // final for all intents and purposes, but has to be set after all arguments are created
    private ImmutableMap<Integer, ImmutableList<Argument>> args;
}
