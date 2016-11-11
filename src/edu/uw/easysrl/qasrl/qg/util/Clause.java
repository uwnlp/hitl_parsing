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

public final class Clause extends Predication {

    private static Set<Category> clauseCategories = new HashSet<>();

    public static enum Type {
        UNSPECIFIED(Category.S),
        DECLARATIVE(Category.Sdcl),
        BARE(Category.valueOf("S[b]")),
        EMBEDDED(Category.valueOf("S[em]")),
        EMBEDDED_BARE(Category.valueOf("S[bem]")),
        FOR(Category.valueOf("S[for]")),
        QUESTION(Category.valueOf("S[q]")),
        WH_QUESTION(Category.valueOf("S[wq]")),
        EMBEDDED_QUESTION(Category.valueOf("S[qem]")),
        POSS(Category.valueOf("S[poss]")),
        AS(Category.valueOf("S[poss]")),
        NG(Category.valueOf("S[ng]")),
        INV(Category.valueOf("S[inv]"));

        public Category getHeadCategory() {
            return headCategory;
        }

        Type(Category headCategory) {
            this.headCategory = headCategory;
        }

        private final Category headCategory;
    }

    public static Clause getFromParse(int headIndex, PredicateCache preds, Parse parse) {
        final SyntaxTreeNode tree = parse.syntaxTree;
        final SyntaxTreeNodeLeaf headLeaf = tree.getLeaves().get(headIndex);

        final String predicate = TextGenerationHelper.renderString(TextGenerationHelper.getNodeWords(headLeaf));
        final Category predicateCategory = parse.categories.get(headIndex);

        final Category clauseCategory = predicateCategory.getHeadCategory();
        assert Category.S.matches(clauseCategory)
            : "must get clause from an S-headed head word";

        if(!clauseCategories.contains(predicateCategory)) {
            // System.err.println(String.format("new clause category: %s (e.g., %s)",
            //                                  predicateCategory, predicate));
            // System.err.println(parse.syntaxTree.getLeaves().stream().map(l -> l.getWord()).collect(joining(" ")));
            clauseCategories.add(predicateCategory);
        }

        final Type clauseType;
        final ImmutableMap<Integer, ImmutableList<Argument>> args;
        // XXX: as a hacky thing, we're only getting the first arg/inner clause for non-dcl clause types. doing better would require some other BS, I think.
        if(Category.Sdcl.matches(clauseCategory)) {
            clauseType = Type.DECLARATIVE;
            final Verb mainVerb = Verb.getFromParse(headIndex, preds, parse);
            args = new ImmutableMap.Builder<Integer, ImmutableList<Argument>>()
                .put(0, ImmutableList.of(Argument.withNoDependency(mainVerb))) // arg number 0 because it's just the head
                .build();
        } else {
            args = IntStream
                .range(1, predicateCategory.getNumberOfArguments() + 1)
                .boxed()
                .collect(toImmutableMap(argNum -> argNum, argNum -> parse.dependencies.stream()
                                        .filter(dep -> dep.getHead() == headIndex && dep.getArgument() != headIndex && argNum == dep.getArgNumber())
                                        .flatMap(argDep -> Stream.of(Predication.Type.getTypeForArgCategory(predicateCategory.getArgument(argDep.getArgNumber())))
                                                 .filter(Optional::isPresent).map(Optional::get)
                                                 .map(predType -> new Argument(Optional.of(argDep), preds.getPredication(argDep.getArgument(), predType))))
                                        .collect(toImmutableList())));

            if(Category.valueOf("S[em]").matches(clauseCategory)) {
                // assert predicateCategory.isFunctionInto(Category.valueOf("S[em]/S[dcl]")) ||
                //     predicateCategory.isFunctionInto(Category.valueOf("S[em]/S[b]"))
                //     : "head of S[em] must be 'that' category. instead have " +
                //     predicate + " (" + predicateCategory + ")";
                clauseType = Type.EMBEDDED;
            } else if(Category.valueOf("S[poss]").matches(clauseCategory)) {
                // assert predicateCategory.matches(Category.valueOf("S[poss]/((S[adj])\\NP)")) || // growing very slowly, if at all
                //     predicateCategory.matches(Category.valueOf("S[poss]/S[dcl]")) // declare higher dividends even if their earnings weaken
                //     : "head of if-expression must have correct category";
                clauseType = Type.POSS;
            } else if(Category.valueOf("S[qem]").matches(clauseCategory)) {
                // assert predicateCategory.isFunctionInto(Category.valueOf("S[qem]/S[dcl]")) || // whether
                //     predicateCategory.isFunctionInto(Category.valueOf("S[qem]/(S[to]\\NP)")) || // whether
                //     predicateCategory.isFunctionInto(Category.valueOf("S[qem]/(S[dcl]/NP)")) || // who
                //     predicateCategory.isFunctionInto(Category.valueOf("(S[qem]/S[dcl])/(S[adj]\\NP)")) // how
                //     : "head of S[qem] must be a valid qem category";
                clauseType = Type.EMBEDDED_QUESTION;
            } else if(Category.valueOf("S[inv]").matches(clauseCategory)) {
                // assert predicateCategory.matches(Category.valueOf("S[inv]/NP")) // as does China
                //     : "head of inv clause must have correct category";
                clauseType = Type.INV;
            } else if(Category.valueOf("S[b]").matches(clauseCategory)) {
                clauseType = Type.BARE;
            } else if(Category.valueOf("S[bem]").matches(clauseCategory)) {
                clauseType = Type.EMBEDDED_BARE;
            } else if(Category.valueOf("S[for]").matches(clauseCategory)) {
                clauseType = Type.FOR;
            } else if(Category.valueOf("S[q]").matches(clauseCategory)) {
                clauseType = Type.QUESTION;
            } else if(Category.valueOf("S[wq]").matches(clauseCategory)) {
                clauseType = Type.WH_QUESTION;
            } else if(Category.valueOf("S[as]").matches(clauseCategory)) {
                clauseType = Type.AS;
            } else if(Category.valueOf("S[ng]").matches(clauseCategory)) {
                clauseType = Type.NG;
            } else {
                assert Category.S.matches(clauseCategory);
                if(!clauseCategory.matches(Category.S)) {
                    System.err.println("Oops, we missed a clause type: " + clauseCategory);
                }
                clauseType = Type.UNSPECIFIED;
            }
        }

        return new Clause(predicate, predicateCategory, args, clauseType);
    }

    @Override
    public ImmutableList<String> getPhrase(Category desiredCategory) {
        switch(type) {
        case DECLARATIVE:
            assert Category.Sdcl.matches(desiredCategory)
                : "declarative clauses can only make S[dcl] phrases";
            return getArgs().get(0).get(0).getPredication().getPhrase(Category.Sdcl);
        case EMBEDDED:
            assert (Category.valueOf("S[em]").matches(desiredCategory) ||
                    Category.valueOf("S[dcl]").matches(desiredCategory))
                : "embedded clauses can only make S[em] phrases... " +
                "except S[dcl] is ok because of CERTAIN not-so-nice coordinations...";
            desiredCategory = Category.valueOf("S[em]"); // but we don't want to mess things up in those cases.
            break;
        default:
            // assert type.getHeadCategory().matches(desiredCategory)
            //     : "desired category " + desiredCategory +
            //     " needs to match clause category " + type.getHeadCategory();
            // if(!type.getHeadCategory().matches(desiredCategory)) {
            //     System.err.println("desired category " + desiredCategory +
            //         " needs to match clause category " + type.getHeadCategory());
            // }
            break;
        }

        assert getArgs().entrySet().stream().allMatch(e -> e.getValue().size() >= 1)
            : "can only get phrase for clause with at least one arg in each slot";
        ImmutableMap<Integer, Argument> args = getArgs().entrySet().stream()
            .collect(toImmutableMap(e -> e.getKey(), e -> e.getValue().get(0)));

        ImmutableList<String> leftArgs = ImmutableList.of();
        ImmutableList<String> rightArgs = ImmutableList.of();
        Category curCat = getPredicateCategory();
        while(!(curCat.matches(desiredCategory) || desiredCategory.matches(curCat))) {
            Category curArgCat = curCat.getArgument(curCat.getNumberOfArguments());
            final Predication curArg;
            if(args.get(curCat.getNumberOfArguments()) == null) {
                System.err.println("null argument " + curCat.getNumberOfArguments() + " in clause for " +
                                   getPredicate() + " (" + getPredicateCategory() + ")" +
                                   "where type is " + type.name() + " and desired category is " + desiredCategory);
                curArg = args.get(curCat.getNumberOfArguments()).getPredication();
            } else {
                curArg = new Gap(curArgCat);
            }
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
    public Clause transformArgs(BiFunction<Integer, ImmutableList<Argument>, ImmutableList<Argument>> transform) {
        return new Clause(getPredicate(), getPredicateCategory(), transformArgsAux(transform), type);
    }

    private Clause(String predicate, Category predicateCategory, ImmutableMap<Integer, ImmutableList<Argument>> args,
                   Type type) {
        super(predicate, predicateCategory, args);
        this.type = type;
    }

    private Type type;
}
