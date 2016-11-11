package edu.uw.easysrl.qasrl.qg.util;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import static edu.uw.easysrl.qasrl.util.GuavaCollectors.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public final class PredicationUtils {

    public static <T extends Predication> T withIndefinitePronouns(T pred) {
        if(pred instanceof Noun) {
            Noun nounPred = (Noun) pred;
            if(nounPred.isExpletive()) {
                return (T) nounPred;
            } else {
                return (T) nounPred.getPronoun()
                    .withNumber(Noun.Number.SINGULAR)
                    .withPerson(Noun.Person.THIRD)
                    .withGender(Noun.Gender.INANIMATE)
                    .withDefiniteness(Noun.Definiteness.INDEFINITE);
            }
        } else {
            return (T) pred.transformArgs((argNum, args) -> {
                    if(pred.getPredicateCategory().getArgument(argNum).matches(Category.NP)) {
                        final Predication replacement;
                        if(args.isEmpty()) {
                            replacement = Pronoun.fromString("something").get();
                        } else {
                            Predication predToReplace = args.get(0).getPredication();
                            if(predToReplace instanceof Noun) {
                                Noun noun = (Noun) predToReplace;
                                if(noun.isExpletive()) {
                                    replacement = noun;
                                }
                                else {
                                    replacement = noun.getPronoun()
                                        .withNumber(Noun.Number.SINGULAR)
                                        .withPerson(Noun.Person.THIRD)
                                        .withGender(Noun.Gender.INANIMATE)
                                        .withDefiniteness(Noun.Definiteness.INDEFINITE);
                                }
                            } else {
                                assert predToReplace instanceof Gap;
                                replacement = predToReplace;
                            }
                        }
                        return ImmutableList.of(Argument.withNoDependency(replacement));
                    } else {
                        return args.stream()
                            .map(arg -> new Argument(arg.getDependency(), withIndefinitePronouns(arg.getPredication())))
                            .collect(toImmutableList());
                    }
                });
        }
    }

    public static <T extends Predication> T elideInnerPPs(T pred) {
        if(pred instanceof Preposition) {
            return pred;
        } else {
            return (T) pred.transformArgs((argNum, args) -> {
                    if(pred.getPredicateCategory().getArgument(argNum).matches(Category.PP)) {
                        return ImmutableList.of(Argument.withNoDependency(new Gap(Category.PP)));
                    } else {
                        return args.stream()
                            .map(arg -> new Argument(arg.getDependency(), elideInnerPPs(arg.getPredication())))
                            .collect(toImmutableList());
                    }
                });
        }
    }

    public static Noun fillerNoun(Category cat) {
        if(cat.matches(Category.NP)) {
            return Pronoun.fromString("something").get();
        } else if(cat.matches(Category.NPexpl)) {
            return ExpletiveNoun.it;
        } else if(cat.matches(Category.NPthr)) {
            return ExpletiveNoun.there;
        } else {
            assert !Category.NP.matches(cat);
            throw new IllegalArgumentException("must make filler noun for an NP category");
        }
    }

    public static <T extends Predication> T addPlaceholderArguments(T pred) {
        return (T) pred.transformArgs((argNum, args) -> {
                if(args.size() > 0) {
                    return args.stream()
                        .map(arg -> new Argument(arg.getDependency(), addPlaceholderArguments(arg.getPredication())))
                        .collect(toImmutableList());
                } else {
                    // TODO XXX should add indefinite pro-form of the correct category.
                    Category argCat = pred.getPredicateCategory().getArgument(argNum);
                    if(Category.NP.matches(argCat)) {
                        return ImmutableList.of(Argument.withNoDependency(fillerNoun(argCat)));
                    }
                    else {
                        // TODO low-pri. may want more pro forms. probably fine though.
                        // System.err.println("Filling in empty argument with gap (no pro-form available). initial pred: " +
                        //                    pred.getPredicate() + " (" + pred.getPredicateCategory() + ")");
                        // System.err.println("arg category: " + argCat);
                        return ImmutableList.of(Argument.withNoDependency(new Gap(argCat)));
                    }
                }
            });
    }

    public static <T extends Predication> ImmutableList<T> sequenceArgChoices(T pred) {
        final ImmutableList<ImmutableMap<Integer, Argument>> sequencedArgChoices = sequenceMap(pred.getArgs());
        return (ImmutableList<T>) sequencedArgChoices.stream()
            .map(argChoice -> pred.transformArgs((argNum, args) -> ImmutableList.of(argChoice.get(argNum))))
            .collect(toImmutableList());
    }

    public static <A, B> ImmutableList<ImmutableMap<A, B>> sequenceMap(ImmutableMap<A, ImmutableList<B>> map) {
        Stream<ImmutableMap<A, B>> paths = Stream.of(ImmutableMap.of());
        for(Map.Entry<A, ImmutableList<B>> mapEntry : map.entrySet()) {
            A key = mapEntry.getKey();
            ImmutableList<B> choices = mapEntry.getValue();
            paths = paths
                .flatMap(path -> choices.stream()
                .map(choice -> new ImmutableMap.Builder<A, B>()
                     .putAll(path)
                     .put(key, choice)
                     .build()));
        }
        return paths.collect(toImmutableList());
    }

    public static Category permuteCategoryArgs(final Category category, Function<Integer, Integer> permutation) {
        final int numArgs = category.getNumberOfArguments();
        Category newCategory = category.getHeadCategory();
        for(int newArgNum = 1; newArgNum <= numArgs; newArgNum++) {
            final int oldArgNum = permutation.apply(newArgNum);
            Category reducedCategory = category;
            int auxArgNum = numArgs;
            while(auxArgNum > oldArgNum) {
                reducedCategory = reducedCategory.getLeft();
                auxArgNum--;
            }
            final Category newArgCategory = reducedCategory.getRight();
            final Slash newSlash = reducedCategory.getSlash();
            newCategory = Category.valueOf("(" + newCategory.toString() + ")" +
                                           newSlash.toString() +
                                           "(" + newArgCategory.toString() + ")");
        }
        return newCategory;
    }

    public static ImmutableMap<Integer, ImmutableList<Argument>> permuteArgs(final ImmutableMap<Integer, ImmutableList<Argument>> args, Function<Integer, Integer> permutation) {
        return args.keySet().stream()
            .collect(toImmutableMap(argNum -> argNum, argNum -> args.get(permutation.apply(argNum))));
    }

}
