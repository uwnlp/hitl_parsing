package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeBinary;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.*;
import java.util.stream.Collectors;
import java.util.function.Predicate;

import static java.util.stream.Collectors.*;
import static edu.uw.easysrl.qasrl.util.GuavaCollectors.*;

/**
 * Tools for generating text from trees, dependencies, and lists of tokens.
 * Created by luheng on 1/20/16.
 */
public class TextGenerationHelper {

    private static final boolean useShorterNPs = false;

    // Reference: https://www.cis.upenn.edu/~treebank/tokenization.html
    private static final String trimPunctuation = " ,.:;!?-";
    private static final String vowels = "aeiou";
    private static Set<String> noSpaceBefore = new HashSet<String>();
    private static Set<String> noSpaceAfter = new HashSet<String>();
    static {
        noSpaceBefore.add(".");
        noSpaceBefore.add(",");
        noSpaceBefore.add("!");
        noSpaceBefore.add("?");
        noSpaceBefore.add(";");
        noSpaceBefore.add(":");
        noSpaceBefore.add("\'");
        noSpaceBefore.add("n't");
        noSpaceBefore.add("'s");
        noSpaceBefore.add("'re");
        noSpaceBefore.add("'ve");
        noSpaceBefore.add("'ll");
        noSpaceBefore.add("na");
        noSpaceBefore.add("'m");
        noSpaceBefore.add("'d");
        noSpaceBefore.add("%");
        noSpaceBefore.add(")");
        noSpaceBefore.add("]");
        noSpaceBefore.add("}");

        noSpaceAfter.add("$");
        noSpaceAfter.add("#");
        noSpaceAfter.add("(");
        noSpaceAfter.add("[");
        noSpaceAfter.add("{");
    }

    // the result here is all possible expansions
    public static final ImmutableMap<String, ImmutableList<String>> contractionExpansions =
            new ImmutableMap.Builder<String, ImmutableList<String>>()
        .put("'s", ImmutableList.of("has", "is"))
        .put("'ve", ImmutableList.of("have"))
        .put("n't", ImmutableList.of("not"))
        .put("'re", ImmutableList.of("are"))
        .put("'ll", ImmutableList.of("will"))
        .put("'m", ImmutableList.of("am"))
        .put("'d", ImmutableList.of("would", "had"))
        .build();

    public static boolean startsWithVowel(String word) {
        if(word.length() == 0) return false;
        char firstLetter = word.toLowerCase().charAt(0);
        return vowels.indexOf(firstLetter) > -1;
    }

    /**
     * Turns a list of tokens into a nicely rendered string, spacing everything appropriately.
     * Trims extra punctuation at the end though. (Useful feature for now; might want to change later.)
     */
    public static String renderString(List<String> rawWords) {
        StringBuilder result = new StringBuilder();
        if(rawWords.size() == 0) {
            return "";
        }
        List<String> words = rawWords
            .stream()
            .map(TextGenerationHelper::translateTreeBankSymbols)
            .collect(Collectors.toList());
        Iterator<String> prevIterator = words.iterator();
        Optional<String> prevWord = Optional.empty();
        Iterator<String> nextIterator = words.iterator(); nextIterator.next();
        Optional<String> nextWord = Optional.empty(); if(nextIterator.hasNext()) nextWord = Optional.of(nextIterator.next());
        for(String word : words) {
            boolean noSpace = !prevWord.isPresent() ||
                (prevWord.isPresent() && noSpaceAfter.contains(prevWord.get())) ||
                noSpaceBefore.contains(word);
            if(!noSpace) {
                result.append(" ");
            }
            // NP shortcut
            if(useShorterNPs) {
                if(word.equalsIgnoreCase("a") && nextWord.isPresent() && startsWithVowel(nextWord.get())) {
                    result.append("an");
                } else if(word.equalsIgnoreCase("an") && nextWord.isPresent() && !startsWithVowel(nextWord.get())) {
                    result.append("a");
                } else {
                    result.append(word);
                }
            } else {
                result.append(word);
            }
            prevWord = Optional.of(prevIterator.next());
            if(nextIterator.hasNext()) {
                nextWord = Optional.of(nextIterator.next());
            } else {
                nextWord = Optional.empty();
            }
        }
        while(result.length() > 0 &&
              trimPunctuation.indexOf(result.charAt(result.length() - 1)) >= 0) {
            result.deleteCharAt(result.length() - 1);
        }
        return result.toString();
    }

    public static String renderHTMLSentenceString(List<String> rawWords, int predicateIndex,
                                                  boolean highlightPredicate) {
        StringBuilder result = new StringBuilder();
        if(rawWords.size() == 0) {
            return "";
        }
        List<String> words = rawWords.stream().map(TextGenerationHelper::translateTreeBankSymbols)
                                              .collect(Collectors.toList());
        Optional<String> prevWord = Optional.empty();
        for (int i = 0; i < words.size(); i++) {
            final String word = words.get(i);
            boolean noSpace = (prevWord.isPresent() && noSpaceAfter.contains(prevWord.get()))
                                || noSpaceBefore.contains(word);
            if(!noSpace) {
                result.append(" ");
            }
            if (i == predicateIndex && highlightPredicate) {
                result.append("<mark><strong>" + word + "</mark></strong>");
            } else {
                result.append(word);
            }
            prevWord = Optional.of(word);
        }
        result.deleteCharAt(0);
        return result.toString();
    }

    public static String translateTreeBankSymbols(String word) {
        if (word.equalsIgnoreCase("-LRB-")) {
            word = "(";
        } else if (word.equalsIgnoreCase("-RRB-")) {
            word = ")";
        } else if (word.equalsIgnoreCase("-LCB-")) {
            word = "{";
        } else if (word.equalsIgnoreCase("-RCB-")) {
            word = "}";
        } else if (word.equalsIgnoreCase("-LSB-")) {
            word = "[";
        } else if (word.equalsIgnoreCase("-RSB-")) {
            word = "]";
        } else if (word.equals("\\/")) {
            word = "/";
        } else if (word.equals("``") || word.equals("\'\'")) {
            word = "\"";
        } else if (word.contains("\\/")) {
            word = word.replace("\\/", "/");
        }
        return word;
    }

    public static Set<Integer> getArgumentIdsForDependency(final List<String> words, Parse parse,
                                                           ResolvedDependency dependency) {
        Set<Integer> answers = new HashSet<>();
        Category answerCategory = dependency.getCategory().getArgument(dependency.getArgNumber());
        int argumentId = dependency.getArgument();
        if (answerCategory.equals(Category.PP) || words.get(argumentId).equals("to")) {
            parse.dependencies.stream()
                    .filter(d -> d.getHead() == argumentId)
                    .forEach(d2 -> answers.add(d2.getArgument()));
            if (answers.size() == 0) {
                answers.add(argumentId);
            }
        } else {
            answers.add(argumentId);
        }
        return answers;
    }

    public static Set<Integer> getArgumentIds(final List<String> words, final Parse parse, int predicateIndex,
                                              Category category, int argumentNumber) {
        Set<Integer> argumentIds = new HashSet<>();
        parse.dependencies.forEach(dependency -> {
            if (dependency.getHead() == predicateIndex && dependency.getCategory() == category &&
                    dependency.getArgNumber() == argumentNumber) {
                argumentIds.addAll(getArgumentIdsForDependency(words, parse, dependency));
            }
        });
        return argumentIds;
    }

    public static Set<Integer> getArgumentIds(final List<String> words, Parse parse, ResolvedDependency dependency) {
        Set<Integer> answers = new HashSet<>();
        Category answerCategory = dependency.getCategory().getArgument(dependency.getArgNumber());
        int argumentId = dependency.getArgument();
        if (answerCategory.equals(Category.PP) || words.get(argumentId).equals("to")) {
            parse.dependencies.stream().filter(d -> d.getHead() == argumentId)
                    .forEach(d2 -> answers.add(d2.getArgument()));
            if (answers.size() == 0) {
                answers.add(argumentId);
            }
        } else {
            answers.add(argumentId);
        }
        return answers;
    }

    /**
     * Tries to get the parent of a node in a tree.
     * Assumes the given node is in the given tree. If not, it will probably return empty, maybe... but maybe not.
     * Returns empty if the node is just the whole tree.
     */
    public static Optional<SyntaxTreeNode> getParent(SyntaxTreeNode node, SyntaxTreeNode wholeTree) {
        int nodeStart = node.getStartIndex();
        int nodeEnd = node.getEndIndex();
        Optional<SyntaxTreeNode> curCandidate = Optional.of(wholeTree);
        Optional<SyntaxTreeNode> lastCandidate = Optional.empty();
        while(curCandidate.isPresent() && curCandidate.get() != node) {
            lastCandidate = curCandidate;
            curCandidate = curCandidate.get().getChildren().stream().filter(child -> {
                    int childStart = child.getStartIndex();
                    int childEnd = child.getEndIndex();
                    return (childStart <= nodeStart) && (childEnd >= nodeEnd);
                }).findFirst();
        }
        return lastCandidate;
    }

    public static Optional<SyntaxTreeNode> getLowestAncestorOfNodes(SyntaxTreeNode one, SyntaxTreeNode two, SyntaxTreeNode wholeTree) {
        final int start = Math.min(one.getStartIndex(), two.getStartIndex());
        final int end = Math.max(one.getEndIndex(), two.getEndIndex());
        Optional<SyntaxTreeNode> cur = Optional.of(one);
        while(cur.isPresent() && (cur.get().getStartIndex() > start || cur.get().getEndIndex() < end)) {
            cur = getParent(cur.get(), wholeTree);
        }
        return cur;
    }

    /**
     * Climbs up the tree, starting at the given node,
     * until we reach a node whose category is a function into (i.e., ends on the left with)
     * the given category. If none is found before getting to the root, returns empty.
     */
    // XXX replace body with call to utility method below
    public static Optional<SyntaxTreeNode> getLowestAncestorFunctionIntoCategory(SyntaxTreeNode node,
                                                                                 Category category,
                                                                                 SyntaxTreeNode wholeTree) {
        Optional<SyntaxTreeNode> curNode = Optional.of(node);
        Optional<Category> curCat = curNode.map(SyntaxTreeNode::getCategory);
        while(curNode.isPresent() && !curCat.get().isFunctionInto(category)) {
            curNode = getParent(curNode.get(), wholeTree);
            curCat = curNode.map(SyntaxTreeNode::getCategory);
        }
        return curNode;
    }

    public static Optional<SyntaxTreeNode> getHighestAncestorStillSatisfyingPredicate(SyntaxTreeNode node,
                                                                                      Predicate<SyntaxTreeNode> pred,
                                                                                      SyntaxTreeNode wholeTree) {
        if(!pred.test(node)) {
            return Optional.empty();
        } else {
            SyntaxTreeNode curNode = node;
            Optional<SyntaxTreeNode> nextNode = getParent(curNode, wholeTree);
            while(nextNode.isPresent() && pred.test(nextNode.get())) {
                curNode = nextNode.get();
                nextNode = getParent(curNode, wholeTree);
            }
            return Optional.of(curNode);
        }
    }

    public static Optional<SyntaxTreeNode> getLowestAncestorSatisfyingPredicate(SyntaxTreeNode node,
                                                                                Predicate<SyntaxTreeNode> pred,
                                                                                SyntaxTreeNode wholeTree) {
        Optional<SyntaxTreeNode> curNode = Optional.of(node);
        while(curNode.isPresent() && !pred.test(curNode.get())) {
            curNode = getParent(curNode.get(), wholeTree);
        }
        return curNode;
    }

    // Could be improved for PPs and such if necessary.
    public static List<TextWithDependencies> getRepresentativePhrasesForUnrealized(Category category) {
        List<String> words = new ArrayList<>();
        Set<ResolvedDependency> deps = new HashSet<>();
        if(category.isFunctionInto(Category.valueOf("PP"))) {
            // do nothing. we don't know the preposition so we can't come up with anything to say
        } else if(category.isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)"))) {
            // still do nothing. adverbials shouldn't appear here honestly but we don't want to wreck things for verbs
        // } else if(category.isFunctionInto(Category.valueOf("(S[ng]\\NP)"))) {
        //     words.add("doing");
        //     words.add("something");
        } else if(category.isFunctionInto(Category.valueOf("(S\\NP)"))) {
            words.add("do");
            words.add("something");
        } else if(category.matches(Category.valueOf("NP")) || category.matches(Category.valueOf("N"))) {
            words.add("something");
        } else if(category.matches(Category.valueOf("NP[thr]"))) {
            words.add("there");
        } else if(category.matches(Category.valueOf("S[dcl]"))) {
            words.add("something");
            words.add("were");
            words.add("the");
            words.add("case");
        } else if(category.matches(Category.valueOf("S[dcl]/NP"))) {
            words.add("would");
            words.add("do");
            words.add("something");
        } else {
            System.err.println("need unrealized phrase for category " + category);
        }
        List<TextWithDependencies> result = new LinkedList<>();
        result.add(new TextWithDependencies(words, deps));
        return result;
    }

    public static List<TextWithDependencies> getRepresentativePhrases(Optional<Integer> headIndexOpt,
                                                                      Category neededCategory,
                                                                      Parse parse) {
        return getRepresentativePhrases(headIndexOpt, neededCategory, parse, Optional.empty());
    }

    public static List<TextWithDependencies> getRepresentativePhrases(Optional<Integer> headIndexOpt,
                                                                      Category neededCategory,
                                                                      Parse parse,
                                                                      String replacementWord) {
        return getRepresentativePhrases(headIndexOpt, neededCategory, parse, Optional.of(replacementWord));
    }

    public static List<TextWithDependencies> getRepresentativePhrases(Optional<Integer> headIndexOpt,
                                                                      Category neededCategory,
                                                                      Parse parse,
                                                                      Optional<String> replacementWord) {
        return getRepresentativePhrases(headIndexOpt, neededCategory, parse, headIndexOpt, replacementWord, true);
    }

    /**
     * Constructs a phrase with the desired head and category label.
     * takes an optional (by overloading---see above) argument asking for the head word to be replaced by something else.
     *   the optional argument is used when stripping verbs of their tense. (maybe there's a less hacky way to deal with that...)
     * In particular this would be for the phrases inside questions: consider "What did something do between April 1991?"
     * For multiple answers to the same queryPrompt, we just call this multiple times. (it should only get one of multiple
     * constituents together in a coordination construction.)
     */
    private static List<TextWithDependencies> getRepresentativePhrases(Optional<Integer> headIndexOpt,
                                                                       Category neededCategory,
                                                                       Parse parse,
                                                                       Optional<Integer> replacementIndexOpt,
                                                                       Optional<String> replacementWord,
                                                                       boolean lookForOf) {
        SyntaxTreeNode tree = parse.syntaxTree;
        if(!headIndexOpt.isPresent()) {
            return getRepresentativePhrasesForUnrealized(neededCategory);
        }
        int headIndex = headIndexOpt.get();
        SyntaxTreeNode headLeaf = tree.getLeaves().get(headIndex);
        Set<ResolvedDependency> touchedDeps = new HashSet<>();

        Optional<SyntaxTreeNode> nodeOpt = getLowestAncestorFunctionIntoCategory(headLeaf, neededCategory, tree);
        if(!nodeOpt.isPresent()) {
            // fall back to just the original leaf. this failure case is very rare.
            List<String> resultWords = new ArrayList<>();
            resultWords.addAll(getNodeWords(headLeaf, replacementIndexOpt, replacementWord));
            List<TextWithDependencies> result = new LinkedList<>();
            result.add(new TextWithDependencies(resultWords, touchedDeps));
            return result;
        }
        SyntaxTreeNode node = nodeOpt.get();

        // TODO figure out if this is better: optional fix for reducing the size of noun phrases
        // if asking for an NP, just take the determiner and the noun itself.
        // But then when aking for a noun, get one modifier if present.
        // And don't do this for proper nouns.
        if(useShorterNPs && !node.getHead().getPos().equals("NNP") && !node.getHead().getPos().equals("NNPS")) {
            if(Category.valueOf("N").matches(node.getCategory())) {
                Optional<SyntaxTreeNode> parentOpt = getParent(node, tree);
                if(parentOpt.isPresent()) {
                    SyntaxTreeNode parent = parentOpt.get();
                    if(Category.valueOf("N").matches(parent.getCategory())) {
                        List<TextWithDependencies> result = new LinkedList<>();
                        result.add(new TextWithDependencies(getNodeWords(parent, replacementIndexOpt, replacementWord),
                                                            getContainedDependencies(parent, parse)));
                        return result;
                    }
                }
            } else if((node instanceof SyntaxTreeNodeBinary) &&
                      Category.valueOf("NP").matches(node.getCategory()) &&
                      Category.valueOf("NP/N").matches(((SyntaxTreeNodeBinary) node).getLeftChild().getHead().getCategory()) &&
                      !(lookForOf && // don't do the NP shortcut when there's an of-phrase later that we need to include.
                        node.getEndIndex() < tree.getEndIndex() &&
                        tree.getLeaves().get(node.getEndIndex()).getWord().equals("of"))) {
                SyntaxTreeNodeLeaf detHead = ((SyntaxTreeNodeBinary) node).getLeftChild().getHead();
                List<TextWithDependencies> phrases = getRepresentativePhrases(headIndexOpt, Category.valueOf("N"),
                        parse, replacementIndexOpt, replacementWord, lookForOf);
                // System.err.println("Reworking headedness of node: category " + node.getCategory() + ", word " + node.getWord());
                // System.err.println("Head node: category " + detHead.getCategory() + ", word " + detHead.getWord());
                // System.err.println("Sub phrase: " + renderString(phrases.get(0).tokens));
                return phrases
                    .stream()
                    .map(twd -> {
                            List<String> tokens = new LinkedList<>();
                            tokens.addAll(getNodeWords(detHead, replacementIndexOpt, replacementWord));
                            tokens.addAll(twd.tokens);
                            return new TextWithDependencies(tokens, twd.dependencies);
                        })
                    .collect(Collectors.toList());
            }
        }

        // get all of the dependencies that were (presumably) touched in the course of getting the lowest good ancestor
        touchedDeps.addAll(getContainedDependencies(node, parse));

        // here we don't necessarily have the whole phrase. `node` is a function into the phrase.
        // especially common is the case where we get a transitive verb and it doesn't bother including the object.
        // so we need to populate the remaining spots by accessing the arguments of THIS guy,
        // until he exactly matches the category we're looking for.
        // using this method will capture and appropriately rearrange extracted arguments and such.

        final Category currentCategory = node.getCategory();

        if(neededCategory.matches(currentCategory)) {
            // if we already have the right kind of phrase, consider adding a trailing "of"-phrase.
            if(lookForOf && // that is, if we're not in the midst of deriving an "of"-phrase already...
               node.getEndIndex() < tree.getEndIndex() &&
               tree.getLeaves().get(node.getEndIndex()).getWord().equals("of") // if the next word is "of",
               ) {
                List<TextWithDependencies> phrasesWithOf =
                    getRepresentativePhrases(Optional.of(node.getEndIndex()),
                                            neededCategory,
                                            parse,
                                            replacementIndexOpt,
                                            replacementWord,
                                            false);
                List<TextWithDependencies> goodOfPhrases = phrasesWithOf
                    .stream()
                    .filter(phrase -> phrase.tokens
                            .stream()
                            .collect(Collectors.joining(" "))
                            .toLowerCase()
                            .contains(headLeaf.getWord().toLowerCase()))
                    .collect(Collectors.toList());
                if(goodOfPhrases.size() > 0) {
                    return goodOfPhrases;
                }
            }
            List<TextWithDependencies> result = new LinkedList<>();
            result.add(new TextWithDependencies(getNodeWords(node, replacementIndexOpt, replacementWord), touchedDeps));
            return result;
        } else {
            assert currentCategory.isFunctionInto(neededCategory)
                : "Current category should be a function into the needed category";
            assert neededCategory.getNumberOfArguments() < currentCategory.getNumberOfArguments()
                : "Current category should have fewer args than needed category, since they don't match";
            List<String> center = getNodeWords(node, replacementIndexOpt, replacementWord);

            // choose argument list
            Map<Integer, Set<ResolvedDependency>> allArgDeps = new HashMap<>();
            for (int i = neededCategory.getNumberOfArguments() + 1;
                 i <= currentCategory.getNumberOfArguments();
                 i++) {
                allArgDeps.put(i, new HashSet<ResolvedDependency>());
            }
            parse.dependencies
                .stream()
                .filter(dep -> dep.getHead() == headIndex)
                .filter(dep -> dep.getArgNumber() > neededCategory.getNumberOfArguments())
                .filter(dep -> dep.getArgNumber() <= currentCategory.getNumberOfArguments())
                .forEach(dep -> allArgDeps.get(dep.getArgNumber()).add(dep));
            List<Map<Integer, Optional<ResolvedDependency>>> argumentChoicePaths = getAllArgumentChoicePaths(allArgDeps);

            List<TextWithDependencies> phrases = new LinkedList<>();

            for(Map<Integer, Optional<ResolvedDependency>> chosenArgDeps : argumentChoicePaths) {
                List<TextWithDependencies> lefts = new ArrayList<>();
                lefts.add(new TextWithDependencies(new LinkedList<>(), new HashSet<>()));

                List<TextWithDependencies> rights = new ArrayList<>();
                rights.add(new TextWithDependencies(new LinkedList<>(), new HashSet<>()));

                Set<ResolvedDependency> localDeps = new HashSet<>();

                Category shrinkingCategory = currentCategory;
                for(int currentArgNum = shrinkingCategory.getNumberOfArguments();
                    currentArgNum > neededCategory.getNumberOfArguments();
                    currentArgNum--) {
                    // otherwise, add arguments on either side until done, according to CCG category.
                    Category argCat = shrinkingCategory.getRight();
                    Optional<ResolvedDependency> argDepOpt = chosenArgDeps.get(currentArgNum);
                    Optional<Integer> argIndexOpt = argDepOpt.map(ResolvedDependency::getArgument);
                    // recover dep using the fact that we know the head leaf, arg num, and arg index.
                    argDepOpt.map(localDeps::add);
                    List<TextWithDependencies> argTWDs =
                        getRepresentativePhrases(argIndexOpt, argCat, parse, replacementIndexOpt, replacementWord, lookForOf);
                    // add the argument on the left or right side, depending on the slash
                    Slash slash = shrinkingCategory.getSlash();
                    switch(slash) {
                    case FWD:
                        rights = rights.stream()
                            .flatMap(right -> argTWDs.stream()
                                     .map(argTWD -> right.concat(argTWD)))
                            .collect(Collectors.toList());
                        break;
                    case BWD:
                        lefts = lefts.stream()
                            .flatMap(left -> argTWDs.stream()
                                     .map(argTWD -> argTWD.concat(left)))
                            .collect(Collectors.toList());
                        break;
                    case EITHER:
                        System.err.println("Undirected slash appeared in supertagged data :(");
                        break;
                    }
                    // proceed to the next argument
                    shrinkingCategory = shrinkingCategory.getLeft();
                }
                for(TextWithDependencies left : lefts) {
                    for(TextWithDependencies right : rights) {
                        List<String> resultTokens = new ArrayList<>();
                        resultTokens.addAll(left.tokens);
                        resultTokens.addAll(center);
                        resultTokens.addAll(right.tokens);
                        localDeps.addAll(touchedDeps);
                        localDeps.addAll(left.dependencies);
                        localDeps.addAll(right.dependencies);
                        phrases.add(new TextWithDependencies(resultTokens, localDeps));
                    }
                }
            }
            // remove empty phrases; this may happen when we have a 1-word phrase and replaced the target word with "";
            // particularly this is the case when we have a 1-word PP as an argument.
            return phrases
                .stream()
                .filter(twd -> twd.tokens.size() > 0)
                .collect(toList());
        }

    }

    public static List<String> getNodeWords(SyntaxTreeNode node) {
        return getNodeWords(node, Optional.empty(), Optional.empty());
    }

    // helper method to make sure we decapitalize the first letter of the sentence
    // and replace a word if necessary.
    public static List<String> getNodeWords(SyntaxTreeNode node, Optional<Integer> replaceIndexOpt, Optional<String> replacementWord) {
        List<String> words = node.getLeaves()
            .stream()
            .map(leaf -> leaf.getWord())
            .collect(Collectors.toList());
        if(node.getStartIndex() == 0) {
            SyntaxTreeNodeLeaf firstLeaf = node.getLeaves().stream().findFirst().get(); // this should always be present
            String firstWord = firstLeaf.getWord();
            String firstPos = firstLeaf.getPos();
            boolean isProper = firstPos.equals("NNP") || firstPos.equals("NNPS");
            boolean isAllCaps = firstWord.matches("[A-Z]{2,}");
            if(!isProper && !isAllCaps) {
                words.set(0, Character.toLowerCase(words.get(0).charAt(0)) + words.get(0).substring(1));
            }
        }
        if(replacementWord.isPresent() && replaceIndexOpt.isPresent()) {
            int indexInWords = replaceIndexOpt.get() - node.getStartIndex();
            String word = replacementWord.get();
            if(indexInWords >= 0 && indexInWords < words.size()) {
                // if we replace with empty string, just remove the word
                if(word.length() > 0) {
                    words.set(indexInWords, replacementWord.get());
                } else {
                    words.remove(indexInWords);
                }
            }
        }
        return words;
    }

    /**
     * Gets all of the dependencies that start and end inside the syntax tree rooted at the given node
     */
    public static ImmutableSet<ResolvedDependency> getContainedDependencies(SyntaxTreeNode node, Parse parse) {
        final int minIndex = node.getStartIndex();
        final int maxIndex = node.getEndIndex();
        return parse.dependencies.stream()
            .filter(dep -> dep.getHead() >= minIndex && dep.getHead() < maxIndex &&
                    dep.getArgument() >= minIndex && dep.getArgument() <= maxIndex)
            .collect(toImmutableSet());
    }

    public static List<Map<Integer, Optional<ResolvedDependency>>>
        getOnlyTargetAndVerbPaths(Map<Integer, Set<ResolvedDependency>> choices, int targetArgNum,
                                  Optional<Integer> verbArgNumOpt) {

        List<Map<Integer, Optional<ResolvedDependency>>> paths = new LinkedList<>();

        if(!verbArgNumOpt.isPresent() || verbArgNumOpt.get() == targetArgNum) {
            for(ResolvedDependency targetDep : choices.get(targetArgNum)) {
                Map<Integer, Optional<ResolvedDependency>> path = new HashMap<>();
                for(int argNum : choices.keySet()) {
                    path.put(argNum, Optional.empty());
                }
                // only the target dep is populated.
                path.put(targetArgNum, Optional.of(targetDep));
                paths.add(path);
            }
        } else {
            int verbArgNum = verbArgNumOpt.get();
            for(ResolvedDependency targetDep : choices.get(targetArgNum)) {
                for(ResolvedDependency verbDep : choices.get(verbArgNum)) {
                    Map<Integer, Optional<ResolvedDependency>> path = new HashMap<>();
                    for(int argNum : choices.keySet()) {
                        path.put(argNum, Optional.empty());
                    }
                    // only the target and verb deps are populated.
                    path.put(targetArgNum, Optional.of(targetDep));
                    path.put(verbArgNum, Optional.of(verbDep));
                    paths.add(path);
                }
            }
        }
        return paths;
    }

    // this is somewhat stupid.
    public static List<Map<Integer, Optional<ResolvedDependency>>>
        getAllArgumentChoicePaths(Map<Integer, Set<ResolvedDependency>> choices) {

        List<Map<Integer, Optional<ResolvedDependency>>> pastPaths = new LinkedList<>();
        pastPaths.add(new TreeMap<Integer, Optional<ResolvedDependency>>());
        List<Map.Entry<Integer, Set<ResolvedDependency>>> choicesList = choices
            .entrySet()
            .stream()
            .sorted(Comparator.comparing(e -> e.getKey()))
            .collect(Collectors.toList());
        for(Map.Entry<Integer, Set<ResolvedDependency>> argAndChoiceSet : choicesList) {
            final int argNum = argAndChoiceSet.getKey();
            final Set<ResolvedDependency> choiceSet = argAndChoiceSet.getValue();
            final List<Map<Integer, Optional<ResolvedDependency>>> currentPaths;
            if(choiceSet.isEmpty()) {
                currentPaths = pastPaths
                    .stream()
                    .map(path -> {
                            Map<Integer, Optional<ResolvedDependency>> newPath = new HashMap<>();
                            newPath.putAll(path);
                            newPath.put(argNum, Optional.empty());
                            return newPath;
                        })
                    .collect(Collectors.toList());
            } else {
                currentPaths = pastPaths
                    .stream()
                    .flatMap(path -> choiceSet
                             .stream()
                             .map(choice -> {
                                     Map<Integer, Optional<ResolvedDependency>> newPath = new HashMap<>();
                                     newPath.putAll(path);
                                     newPath.put(argNum, Optional.of(choice));
                                     return newPath;
                                 }))
                    .collect(Collectors.toList());
            }
            pastPaths = currentPaths;
        }
        return pastPaths;
    }

    public static String dependencyString(ImmutableList<String> words, ResolvedDependency dep) {
        return words.get(dep.getHead()) + "\t-"
            + dep.getArgNumber() + "->\t"
            + words.get(dep.getArgument());
    }

    /**
     * Data structure used to return text generated from a parsed sentence.
     * Indicates which dependencies were used in constructing the text.
     * This is returned by getRepresentativePhrase to indicate what parts of the parse were used.
     */
    public static class TextWithDependencies {
        public final List<String> tokens;
        public final Set<ResolvedDependency> dependencies;

        public TextWithDependencies(List<String> tokens, Set<ResolvedDependency> dependencies) {
            this.tokens = tokens;
            this.dependencies = dependencies;
        }

        public String toString() {
            return tokens.toString() + ", " + dependencies.toString();
        }

        // functional concat
        public TextWithDependencies concat(TextWithDependencies other) {
            List<String> newTokens = new LinkedList<>();
            Set<ResolvedDependency> newDependencies = new HashSet<>();
            newTokens.addAll(this.tokens);
            newTokens.addAll(other.tokens);
            newDependencies.addAll(this.dependencies);
            newDependencies.addAll(other.dependencies);
            return new TextWithDependencies(newTokens, newDependencies);
        }

        public TextWithDependencies concatWithDep(TextWithDependencies other, Optional<ResolvedDependency> depOpt) {
            if(depOpt.isPresent()) {
                return concatWithDep(other, depOpt.get());
            } else {
                return concat(other);
            }
        }

        public TextWithDependencies concatWithDep(TextWithDependencies other, ResolvedDependency dep) {
            TextWithDependencies newTWD = this.concat(other);
            newTWD.dependencies.add(dep);
            return newTWD;
        }

        public static TextWithDependencies fromWord(String word) {
            List<String> tokens = new LinkedList<>();
            tokens.add(word);
            return new TextWithDependencies(tokens, new HashSet<>());
        }
    }
}
