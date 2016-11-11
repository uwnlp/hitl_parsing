package edu.uw.easysrl.qasrl.qg.util;

import java.util.Set;
import java.util.HashSet;
import java.util.Optional;
import java.util.function.BiFunction;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import static edu.uw.easysrl.qasrl.util.GuavaCollectors.*;

import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.TextGenerationHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public final class Preposition extends Predication {

    public static final ImmutableSet<String> prepositionWords =
        ImmutableSet.of("aboard", "about", "above", "across", "afore", "after", "against", "ahead", "along", "alongside", "amid",
                        "amidst", "among", "amongst", "around", "as", "aside", "astride", "at", "atop", "before", "behind",
                        "below", "beneath", "beside", "besides", "between", "beyond", "by", "despite", "down", "during", "except",
                        "for", "from", "given", "in", "inside", "into", "near", "next", "of", "off", "on", "onto", "opposite",
                        "out", "outside", "over", "pace", "per", "round", "since", "than", "through", "throughout", "till", "times",
                        "to", "toward", "towards", "under", "underneath", "until", "unto", "up", "upon", "versus", "via", "with",
                        "within", "without");

    private static Set<Category> prepositionCategories = new HashSet<>();

    public static Preposition makeSimplePP(String predicate, Noun object) {
        return new Preposition(predicate, Category.valueOf("PP/NP"), new ImmutableMap.Builder<Integer, ImmutableList<Argument>>()
                               .put(1, ImmutableList.of(Argument.withNoDependency(object)))
                               .build());
    }

    public static Preposition getFromParse(Integer headIndex, PredicateCache preds, Parse parse) {
        final SyntaxTreeNode tree = parse.syntaxTree;
        final SyntaxTreeNodeLeaf headLeaf = tree.getLeaves().get(headIndex);

        final String predicate = TextGenerationHelper.renderString(TextGenerationHelper.getNodeWords(headLeaf));
        final Category predicateCategory = parse.categories.get(headIndex);
        final ImmutableMap<Integer, ImmutableList<Argument>> args = IntStream
            .range(1, predicateCategory.getNumberOfArguments() + 1)
            .boxed()
            .collect(toImmutableMap(argNum -> argNum, argNum -> parse.dependencies.stream()
            .filter(dep -> dep.getHead() == headIndex && dep.getArgument() != headIndex && argNum == dep.getArgNumber())
            .flatMap(argDep -> Stream.of(Predication.Type.getTypeForArgCategory(predicateCategory.getArgument(argDep.getArgNumber())))
            .filter(Optional::isPresent).map(Optional::get)
            .map(predType -> new Argument(Optional.of(argDep), preds.getPredication(argDep.getArgument(), predType))))
            .collect(toImmutableList())));

        if(!prepositionCategories.contains(predicateCategory)) {
            // System.err.println(String.format("new preposition category: %s (e.g., %s)",
            //                                  predicateCategory, predicate));
            prepositionCategories.add(predicateCategory);
        }

        return new Preposition(predicate, predicateCategory, args);
    }

    @Override
    public ImmutableList<String> getPhrase(Category desiredCategory) {
        // assert getPredicateCategory().isFunctionInto(desiredCategory)
        //     : "can only make prepositional phrases or functions into them with preposition. " +
        //     "predicate: " + getPredicate() + " (" + getPredicateCategory() + "); " +
        //     "desired category: " + desiredCategory;

        assert getArgs().entrySet().stream().allMatch(e -> e.getValue().size() >= 1)
            : "can only get phrase for preposition with at least one arg in each slot";
        ImmutableMap<Integer, Argument> args = getArgs().entrySet().stream()
            .collect(toImmutableMap(e -> e.getKey(), e -> e.getValue().get(0)));

        if(!getPredicateCategory().isFunctionInto(desiredCategory)) {
            // System.err.println("can only make prepositional phrases or functions into them with preposition. " +
            //                    "predicate: " + getPredicate() + " (" + getPredicateCategory() + "); " +
            //                    "desired category: " + desiredCategory);
            // System.err.println(args.get(1).getPredication().getPhrase(getPredicateCategory().getArgument(1)));
        }

        ImmutableList<String> leftArgs = ImmutableList.of();
        ImmutableList<String> rightArgs = ImmutableList.of();
        Category curCat = getPredicateCategory();
        // sadness
        // while(!desiredCategory.matches(curCat)) {
        while(curCat.getNumberOfArguments() > desiredCategory.getNumberOfArguments()) {
            Predication curArg = args.get(curCat.getNumberOfArguments()).getPredication();
            Category curArgCat = curCat.getArgument(curCat.getNumberOfArguments());
            Slash slash = curCat.getSlash();
            switch(slash) {
            case BWD: leftArgs = new ImmutableList.Builder<String>()
                    .addAll(curArg.getPhrase(curArgCat))
                    .addAll(leftArgs)
                    .build();
                break;
            case FWD: rightArgs = new ImmutableList.Builder<String>()
                    .addAll(rightArgs)
                    .addAll(curArg.getPhrase(curArgCat))
                    .build();
                break;
            default: assert false;
            }
            curCat = curCat.getLeft();
        }
        return new ImmutableList.Builder<String>()
            .addAll(leftArgs)
            .add(getPredicate())
            .addAll(rightArgs)
            .build();
    }

    @Override
    public ImmutableSet<ResolvedDependency> getLocalDependencies() {
        return ImmutableSet.of();
    }

    @Override
    public Preposition transformArgs(BiFunction<Integer, ImmutableList<Argument>, ImmutableList<Argument>> transform) {
        return new Preposition(getPredicate(), getPredicateCategory(), transformArgsAux(transform));
    }

    private Preposition(String predicate, Category predicateCategory, ImmutableMap<Integer, ImmutableList<Argument>> args) {
        super(predicate, predicateCategory, args);
    }
}
