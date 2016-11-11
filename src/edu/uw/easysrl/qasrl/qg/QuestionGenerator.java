package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.*;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.qasrl.qg.util.*;

import com.google.common.collect.ImmutableList;

import static edu.uw.easysrl.qasrl.util.GuavaCollectors.*;
import static edu.uw.easysrl.qasrl.qg.TextGenerationHelper.TextWithDependencies;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by luheng on 12/8/15.
 */
public class QuestionGenerator {
    private static boolean indefinitesOnly = true; // if true: only ask questions with indefinite noun args
    public static void setIndefinitesOnly(boolean flag) {
        indefinitesOnly = flag;
    }

    private static boolean askAllStandardQuestions = false; // all (false: just one) standard questions
    public static void setAskAllStandardQuestions(boolean flag) {
        askAllStandardQuestions = flag;
    }

    private static boolean includeSupersenseQuestions = false; // if true: include supersense questions
    public static void setIncludeSupersenseQuestions(boolean flag) {
        includeSupersenseQuestions = flag;
    }

    // if this is true, the previous three don't matter
    private static boolean askPPAttachmentQuestions = false; // if true: include PP attachment questions
    public static void setAskPPAttachmentQuestions(boolean flag) {
        askPPAttachmentQuestions = flag;
    }

    /**
     * Generate all queryPrompt answer pairs for a sentence, given the n-best list.
     * @param sentenceId: unique identifier of the sentence.
     * @param words: words in the sentence.
     * @param nBestList: the nbest list.
     * @return
     */
    public static ImmutableList<QuestionAnswerPair> generateAllQAPairs(int sentenceId,
                                                                       ImmutableList<String> words,
                                                                       NBestList nBestList) {
        return IntStream.range(0, nBestList.getN()).boxed()
                .flatMap(parseId -> generateQAPairsForParse(sentenceId, parseId, words, nBestList.getParse(parseId)).stream())
                .collect(toImmutableList());
    }

    public static ImmutableList<QuestionAnswerPair> generateQAPairsForParse(int sentenceId,
                                                                            int parseId,
                                                                            ImmutableList<String> words,
                                                                            Parse parse) {
        return IntStream.range(0, words.size())
                .mapToObj(Integer::new)
                .flatMap(predIndex -> generateQAPairsForPredicate(sentenceId, parseId, predIndex, words, parse).stream())
                .collect(toImmutableList());
    }

    private static ImmutableList<QuestionAnswerPair> generateQAPairsForPredicate(int sentenceId,
                                                                                 int parseId,
                                                                                 int predicateIdx,
                                                                                 List<String> words,
                                                                                 Parse parse) {
        final MultiQuestionTemplate template = new MultiQuestionTemplate(sentenceId, parseId, predicateIdx, words, parse);
        return askPPAttachmentQuestions ?
                template.getAllPPAttachmentQAPairs(indefinitesOnly)
                        .stream()
                        .collect(toImmutableList()) :
                IntStream.range(1, parse.categories.get(predicateIdx).getNumberOfArguments() + 1)
                        .boxed()
                        .flatMap(argNum -> template.getAllQAPairsForArgument(argNum, indefinitesOnly,
                                askAllStandardQuestions, includeSupersenseQuestions).stream())
                        .collect(toImmutableList());
    }

    private static Verb withTenseForQuestion(Verb verb) {
        if(verb.getTense() == Verb.Tense.FUTURE ||
           verb.getTense() == Verb.Tense.PRESENT ||
           verb.getTense() == Verb.Tense.PAST) {
            return verb;
        } else {
            return verb.withModal("would").withProgressive(false);
        }

    }

    private static int numberOfLeftNPArgs(Category cat) {
        if(cat.getNumberOfArguments() > 0) {
            boolean isLeftNPSlash = cat.getRight().matches(Category.NP) && cat.getSlash() == Category.Slash.BWD;
            return (isLeftNPSlash ? 1 : 0) + numberOfLeftNPArgs(cat.getLeft());
        } else {
            return 0;
        }
    }

    private static boolean isVerbValid(Verb verb, boolean shouldBeCopula) {
        return verb != null &&
            (shouldBeCopula
             ? verb.isCopular()
             : !verb.isCopular() && !VerbHelper.isAuxiliaryVerb(verb.getPredicate(), verb.getPredicateCategory())) &&
            !Preposition.prepositionWords.contains(verb.getPredicate()) &&
            numberOfLeftNPArgs(verb.getPredicateCategory()) <= 1;
    }

    public static ImmutableList<QuestionAnswerPair> newCoreNPArgQuestions(int sentenceId, int parseId, Parse parse) {
        final PredicateCache preds = new PredicateCache(parse);
        final ImmutableList<String> words = parse.syntaxTree.getLeaves().stream().map(l -> l.getWord()).collect(toImmutableList());
        final ImmutableList<QuestionAnswerPair> qaPairs = IntStream
            .range(0, parse.categories.size())
            .boxed()
            .filter(index -> parse.categories.get(index).isFunctionInto(Category.valueOf("S\\NP")) &&
                    // parse.categories.get(index).isFunctionInto(Category.valueOf("(S[dcl]|S[dcl])|NP")) &&
                    !parse.categories.get(index).isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)")) &&
                    !VerbHelper.isAuxiliaryVerb(words.get(index), parse.categories.get(index)))
            .flatMap(predicateIndex -> {
                    Verb v = null;
                    try {
                        v = Verb.getFromParse(predicateIndex, preds, parse);
                    } catch(IllegalArgumentException e) {
                        // System.err.println(e.getMessage());
                        v = null;
                    }
                    return Stream.of(v)
            .filter(verb -> isVerbValid(verb, false))
            .map(verb -> PredicationUtils.elideInnerPPs(verb))
            .flatMap(verb -> IntStream.range(1, verb.getPredicateCategory().getNumberOfArguments() + 1)
            .boxed()
            .filter(argNum -> Category.NP.matches(verb.getPredicateCategory().getArgument(argNum)))
            .flatMap(askingArgNum -> verb.getArgs().get(askingArgNum).stream()
            .filter(answerArg -> !((Noun) answerArg.getPredication()).isExpletive()) // this should always be true when arg cat is NP
            .filter(answerArg -> !answerArg.getPredication().getPredicate().matches("[0-9]*"))
            .filter(answerArg -> answerArg.getDependency().isPresent())
            .flatMap(answerArg -> PredicationUtils
                     .sequenceArgChoices(PredicationUtils
                     .withIndefinitePronouns(PredicationUtils
                     .addPlaceholderArguments(verb))).stream()
            .map(sequencedVerb -> {
                    Noun whNoun = ((Noun) answerArg.getPredication())
                    .getPronoun()
                    .withPerson(Noun.Person.THIRD)
                    .withNumber(Noun.Number.SINGULAR)
                    .withGender(Noun.Gender.INANIMATE)
                    .withDefiniteness(Noun.Definiteness.FOCAL);
                    ImmutableList<String> whWord = whNoun.getPhrase(Category.NP);
                    Verb questionPred = withTenseForQuestion(sequencedVerb.transformArgs((argNum, args) -> argNum == askingArgNum
                                    ? ImmutableList.of(Argument.withNoDependency(whNoun.withElision(true)))
                                    : args));
                    ImmutableList<String> questionWords = Stream.concat(whWord.stream(), questionPred.getQuestionWords().stream())
                    .collect(toImmutableList());
                    return new BasicQuestionAnswerPair(sentenceId, parseId, parse,
                                                       predicateIndex, verb.getPredicateCategory(), askingArgNum,
                                                       predicateIndex, null,
                                                       questionPred.getAllDependencies(), questionWords,
                                                       answerArg.getDependency().get(),
                                                       new TextWithDependencies(answerArg.getPredication().getPhrase(Category.NP),
                                                                                answerArg.getPredication().getAllDependencies()));
                }))));})
            .collect(toImmutableList());
        return qaPairs;
    }

    public static ImmutableList<QuestionAnswerPair> newCopulaQuestions(int sentenceId, int parseId, Parse parse) {
        final PredicateCache preds = new PredicateCache(parse);
        final ImmutableList<String> words = parse.syntaxTree.getLeaves().stream().map(l -> l.getWord()).collect(toImmutableList());
        final ImmutableList<QuestionAnswerPair> qaPairs = IntStream
            .range(0, parse.categories.size())
            .boxed()
            .filter(index -> parse.categories.get(index).isFunctionInto(Category.valueOf("S\\NP")) &&
                    !parse.categories.get(index).isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)")) &&
                    VerbHelper.isCopulaVerb(words.get(index)))
            .flatMap(predicateIndex -> {
                    Verb v = null;
                    try {
                        v = Verb.getFromParse(predicateIndex, preds, parse);
                    } catch(IllegalArgumentException e) {
                        // System.err.println(e.getMessage());
                        v = null;
                    }
                    return Stream.of(v)
            .filter(verb -> isVerbValid(verb, true))
            .map(verb -> PredicationUtils.elideInnerPPs(verb))
            .flatMap(verb -> IntStream.range(1, verb.getPredicateCategory().getNumberOfArguments() + 1)
            .boxed()
            .filter(argNum -> Category.NP.matches(verb.getPredicateCategory().getArgument(argNum)))
            .flatMap(askingArgNum -> verb.getArgs().get(askingArgNum).stream()
            .filter(answerArg -> !((Noun) answerArg.getPredication()).isExpletive()) // this should always be a noun when arg cat is NP
            .filter(answerArg -> answerArg.getDependency().isPresent())
            .flatMap(answerArg -> PredicationUtils.sequenceArgChoices(PredicationUtils.addPlaceholderArguments(verb)).stream()
            .map(reducedVerb -> {
                    // args is not empty; was filled in before.
                    Noun whNoun = ((Noun) answerArg.getPredication())
                    .getPronoun()
                    .withPerson(Noun.Person.THIRD)
                    .withGender(Noun.Gender.INANIMATE)
                    .withDefiniteness(Noun.Definiteness.FOCAL);
                    ImmutableList<String> whWords = whNoun.getPhrase(Category.NP);
                    // these may be necessary now since we didn't auto-pronoun everything
                    Verb questionPred = withTenseForQuestion(reducedVerb.transformArgs((argNum, args) -> {
                                if(argNum == askingArgNum) {
                                    return ImmutableList.of(Argument.withNoDependency(whNoun.withElision(true)));
                                } else {
                                    Category argCat = verb.getPredicateCategory().getArgument(argNum);
                                    if(!Category.NP.matches(argCat)) {
                                        // since we sequenced the verb, there should be exactly 1 arg
                                        return ImmutableList.of(Argument.withNoDependency(PredicationUtils.withIndefinitePronouns(args.get(0).getPredication())));
                                    } else {
                                        return args;
                                    }
                                }
                            }));
                    ImmutableList<String> questionWords = Stream.concat(whWords.stream(), questionPred.getQuestionWords().stream())
                    .collect(toImmutableList());
                    return new BasicQuestionAnswerPair(sentenceId, parseId, parse,
                                                       predicateIndex, verb.getPredicateCategory(), askingArgNum,
                                                       predicateIndex, null,
                                                       questionPred.getAllDependencies(), questionWords,
                                                       answerArg.getDependency().get(),
                                                       new TextWithDependencies(answerArg.getPredication().getPhrase(Category.NP),
                                                                                answerArg.getPredication().getAllDependencies()));
                }))));})
            .collect(toImmutableList());
        return qaPairs;
    }
}

