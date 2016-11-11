package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.qg.util.*;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.TextGenerationHelper.TextWithDependencies;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.IntStream;
import static java.util.stream.Collectors.*;
import static edu.uw.easysrl.qasrl.util.GuavaCollectors.*;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.*;

/**
 * Generates higher-granularity QA Pairs for multi-answer questions.
 *
 * Created by julian on 2/29/16.
 */
public class MultiQuestionTemplate {

    private static boolean logging = true;

    public enum QuestionType {
        VERB (
                    Category.valueOf("S\\NP") // intransitives
                    ,Category.valueOf("(S\\NP)/NP") // transitives
                    ,Category.valueOf("(S\\NP)/PP")
                    ,Category.valueOf("((S\\NP)/PP)/NP")
                    // ,Category.valueOf("(S\\NP)/(PP/NP)") // e.g., an action was called for
                 /* ,Category.valueOf("((S\\NP)/NP)/PR")
                    ,Category.valueOf("((S\\NP)/PP)/PR")
                    // T1 said (that) T2
                    ,Category.valueOf("(S[dcl]\\NP)/S")
                    // T1, T2 said, or T1, said T2
                    ,Category.valueOf("(S[dcl]\\S[dcl])|NP")
                    // T1 agreed to do T2
                    ,Category.valueOf("(S\\NP)/(S[to]\\NP)")
                    // T1 stopped using T2
                    ,Category.valueOf("(S\\NP)/(S[ng]\\NP)")
                    // T1 made T3 T2; ditransitives
                    ,Category.valueOf("((S\\NP)/NP)/NP")
                    // T1 promised T3 to do T2
                    ,Category.valueOf("((S\\NP)/(S[to]\\NP))/NP") // Category.valueOf("((S[dcl]\\NP)/(S[to]\\NP))/NP")
                 */
            ),
        ADJECTIVE_ADJUNCT(
                    Category.valueOf("((S[adj]\\NP)\\(S[adj]\\NP))/NP")
            ),
        NOUN_ADJUNCT(
                    Category.valueOf("(NP\\NP)/NP")
                    // ,Category.valueOf("N|N"),
            ),
        // right now we're assuming the second arg of a verb adjunct is always the main verb.
        VERB_ADJUNCT(
                    Category.valueOf("((S\\NP)\\(S\\NP))/NP")
                    ,Category.valueOf("((S\\NP)\\(S\\NP))/S[dcl]")
                 /* ,Category.valueOf("(S\\NP)\\(S\\NP)")
                    ,Category.valueOf("((S\\NP)\\(S\\NP))/S")
                    ,Category.valueOf("((S\\NP)\\(S\\NP))/(S[ng]\\NP)") // ``by'' as in ``by doing something''.
                    ,Category.valueOf("((S\NP)\(S\NP))/PP") // according (to):
                    ,Category.valueOf("((S\NP)\(S\NP))/PP") // down (from):
                 */
            ),
        CLAUSE_ADJUNCT(
                // Category.valueOf("S|S"),
                    ),
        RELATIVIZER(
                // Category.valueOf("(NP\\NP)/(S[dcl]\\NP)")
            ),
        INVALID();

        public final Category[] categories;
        QuestionType(Category... categories) {
            this.categories = categories;
        }

        public boolean admits(Category category) {
            for (Category c : categories) {
                if (c.matches(category)) {
                    return true;
                }
            }
            return false;
        }

        public static QuestionType getTypeFor(Category category) {
            for(QuestionType type : QuestionType.values()) {
                if(type.admits(category)) return type;
            }
            return INVALID;
        }


    }

    public enum Supersense {
        HOW, // means/method/manner/style/role...lots of stuff
        FAR, // distance
        LONG, // duration
        WHEN, // time
        WHERE, // place
        WHY, // reason/explanation/benefactor
        MANNER,
        // ROLE,
        MEANS, // should require NP as answer?
        // MUCH, // magnitude---like frequency (mass)
        // OFTEN, // frequency (mass)
        // TIMES, // frequency (count)
        UNKNOWN;

        // source, destination, beginning, end, more general scale/motion related stuff
        // medium?

        private final List<String> whWords = new LinkedList<String>();

        static {
            HOW.setWhs("How");
            FAR.setWhs("How", "far");
            LONG.setWhs("How", "long");
            WHEN.setWhs("When");
            WHERE.setWhs("Where");
            WHY.setWhs("Why");
            MANNER.setWhs("In", "what", "manner");
            // ROLE.setWhs("In", "what", "capacity"); // TODO capacity? role?
            MEANS.setWhs("By", "what", "means");
            // SOURCE.setWhs("From", "what", "source"); // should have NP as answer
            // DESTINATION.setWhs("To", "what", "destination"); // should have NP as answer
            UNKNOWN.setWhs("UNKNOWN");
        }

        private static final Map<String, List<Supersense>> prepositionToSupersenses = new HashMap<>();
        private static void addPreposition(String preposition, Supersense... senses) {
            List<Supersense> supersenseList = Arrays.asList(senses);
            prepositionToSupersenses.put(preposition, supersenseList);
        }

        static {
            addPreposition("as", HOW, WHEN, WHY);
            addPreposition("among", WHERE);
            addPreposition("for", FAR, LONG, WHY);
            addPreposition("from");
            addPreposition("in", HOW, WHEN, WHERE, MEANS);
            addPreposition("to", WHERE, WHY);
            addPreposition("with", HOW, WHEN, WHERE, MANNER, MEANS);
        }

        private void setWhs(String... whWords) {
            this.whWords.clear();
            this.whWords.addAll(Arrays.asList(whWords));
        }
        private List<String> getWhWords() {
            return whWords;
        }

        public static List<Supersense> getSupersensesFor(String preposition) {
            List<Supersense> supersenses = prepositionToSupersenses.get(preposition);
            if(supersenses == null) {
                /*
                int code = preposition.hashCode();
                if(!missedPrepositionsPrinted.contains(code)) {
                    if(logging) System.err.println("Need preposition supersenses: " + preposition);
                    missedPrepositionsPrinted.add(code);
                }
                */
                supersenses = new LinkedList<>();
                supersenses.add(UNKNOWN);
            }
            // whs = Arrays.asList(Supersense.values());
            return supersenses;
        }

    }

    private static final Set<Integer> qaPairsPrinted = new HashSet<Integer>();
    private static final Set<Integer> missedPrepositionsPrinted = new HashSet<Integer>();

    // Categories to skip
    private static final Category auxiliaries = Category.valueOf("(S[dcl]\\NP)/(S[b]\\NP)");
    // private static final Category controlParticles = Category.valueOf("(S[to]\\NP)/(S[b]\\NP)");
    private static final Category controlParticles = Category.valueOf("(S\\NP)/(S[b]\\NP)");
    private static final Category pastParticiples = Category.valueOf("(S[dcl]\\NP)/(S[pt]\\NP)");

    // private static final String[] otherFilteredCategories = new String[] {
    //     "(S/S)/NP",
    //     "(S\\NP)\\(S\\NP)",
    //     "S[em]/S[dcl]",
    //     "(S/S)/(S/S)",
    //     "(S[b]\\NP)/(S[pt]\\NP)",
    //     "S[qem]/S[dcl]",
    //     "(S\\S)/S[dcl]",
    //     "(S[adj]\\NP)/(S[to]\\NP)",
    //     "S/S",
    //     "((S\\NP)/(S\\NP))/((S\\NP)/(S\\NP))", // i.e. more
    //     "((S\\NP)\\(S\\NP))/((S\\NP)\\(S\\NP))",
    //     "((S\\NP)\\(S\\NP))\\((S\\NP)\\(S\\NP))",
    //     "((S\\NP)\\(S\\NP))/(S[b]\\NP)",
    //     "(((S\\NP)\\(S\\NP))\\((S\\NP)\\(S\\NP)))/(((S\\NP)\\(S\\NP))\\((S\\NP)\\(S\\NP)))",
    // };

    private static final Set<String> badPredicates = new HashSet<>();
    static {
        badPredicates.add("--");
        badPredicates.add("-LRB-");
        badPredicates.add("-RRB-");
        badPredicates.add("-LCB-");
        badPredicates.add("-RCB-");
        badPredicates.add("-LSB-");
        badPredicates.add("-RSB-");
    }


    public Parse parse;
    public List<String> words;
    public List<Category> categories;
    public SyntaxTreeNode tree;

    public int predicateIndex;
    public Category predicateCategory;

    // the category here is the one of the argument of the head's category,
    // so it's not sensitive to having multiple arguments or locations.
    public Map<Integer, Category> argCategories;
    // for each arg num, lists all of the deps ending in arguments
    public Map<Integer, Set<ResolvedDependency>> allArgDeps;
    // returns a list of all indices of arguments for each arg num
    // public List<Set<Integer>> argIndices;

    private static VerbHelper verbHelper = VerbHelper.trainingSetVerbHelper;

    public QuestionType type;

    private final int sentenceId;
    private final int parseId;

    public MultiQuestionTemplate(int sentenceId, int parseId, int predicateIndex, List<String> words, Parse parse) {
        this.categories = parse.categories;
        this.predicateIndex = predicateIndex;
        this.predicateCategory = categories.get(predicateIndex);
        this.parse = parse;
        this.tree = parse.syntaxTree;
        this.words = words;
        this.sentenceId = sentenceId;
        this.parseId    = parseId;
        final int numArguments = predicateCategory.getNumberOfArguments();
        this.argCategories = new HashMap<Integer, Category>();
        this.allArgDeps = new HashMap<Integer, Set<ResolvedDependency>>();
        for (int i = 1; i <= numArguments; i++) {
            allArgDeps.put(i, new HashSet<ResolvedDependency>());
            argCategories.put(i, predicateCategory.getArgument(i));
        }
        for (ResolvedDependency dep : parse.dependencies) {
            if (dep.getHead() == predicateIndex && dep.getArgument() != dep.getHead()) {
                assert allArgDeps.containsKey(dep.getArgNumber())
                    : "Arg number on dependency should be > 0 and not exceed the number of arguments to the head";
                allArgDeps.get(dep.getArgNumber()).add(dep);
            }
        }
        this.type = QuestionType.getTypeFor(predicateCategory);

        /* I'll burn this bridge when I get to it
        // Special case: T1, T2 said, or T2, said T1
        if (numArguments == 2 && predicateCategory.getArgument(1).equals(Category.Sdcl)) {
            ArgumentSlot[] slots = new ArgumentSlot[] { arguments[2], arguments[1] };
            return new QuestionTemplate(pred, slots, tree, words, categories, verbHelper);
        }
        */
    }

    private boolean cantAskQuestion(int argNumber, Map<Integer, Optional<ResolvedDependency>> chosenArgDeps) {
        if(argNumber > predicateCategory.getNumberOfArguments()) {
            return true;
        }
        Optional<ResolvedDependency> argDepOpt = chosenArgDeps.get(argNumber);
        Optional<Integer> argIndexOpt = argDepOpt.map(ResolvedDependency::getArgument);
        if(!argIndexOpt.isPresent()) {
            return true;
        }
        int argIndex = argIndexOpt.get();
        Category observedArgCategory = categories.get(argIndex);
        boolean cantAsk = type == QuestionType.INVALID ||
            (type == QuestionType.VERB && Prepositions.prepositionWords.contains(words.get(predicateIndex))) ||
            (words.get(argIndex).matches("[0-9]+")) ||
            (badPredicates.contains(words.get(predicateIndex))) ||
            (auxiliaries.matches(observedArgCategory) ||
             controlParticles.matches(observedArgCategory) ||
             pastParticiples.matches(observedArgCategory) ||
             Category.valueOf("NP[thr]").matches(observedArgCategory) ||
             Category.valueOf("NP[expl]").matches(observedArgCategory)) ||
            words.get(predicateIndex).equalsIgnoreCase("of") || // "of" is just a doozy
            (type == QuestionType.VERB_ADJUNCT &&
             chosenArgDeps.values()
             .stream()
             .filter(Optional::isPresent).map(Optional::get)
             .map(ResolvedDependency::getArgument)
             .map(words::get)
             .anyMatch(VerbHelper::isCopulaVerb)) || // adverbs of copulas are wonky and not helpful
            (type == QuestionType.VERB_ADJUNCT &&
             words.get(predicateIndex).equalsIgnoreCase("when") &&
             argNumber == 3) || // #what did I eat ice cream when? <-- bad queryPrompt. but works for others: after, before, etc.
            (type == QuestionType.ADJECTIVE_ADJUNCT &&
             argNumber == 2) || // "full of promise" -> "something was _ of promise; what's _?" --- can't really ask it.
            categories.get(argIndex).matches(Category.valueOf("PR")) // don't ask about a particle; TODO where are all the PR arguments...?
            ;
        return cantAsk;
        // return cantAsk ||
        //     !((Category.valueOf("(S\\NP)/PP").matches(predicateCategory) && argNumber == 2) ||
        //       (Category.valueOf("((S\\NP)/PP)/NP").matches(predicateCategory) && argNumber == 2));
        // return cantAsk || !(type == QuestionType.VERB_ADJUNCT && argNumber == 3);
        // return !(type == QuestionType.NOUN_ADJUNCT);
    }

    public ImmutableList<BasicQuestionAnswerPair> getAllPPAttachmentQAPairs(boolean subjectPronoun) {
        return TextGenerationHelper.getAllArgumentChoicePaths(allArgDeps).stream()
            .flatMap(chosenArgDeps -> getPPAttachmentQAPairs(chosenArgDeps, subjectPronoun).stream())
            .collect(toImmutableList());
    }

    /**
     * Get all possible queryPrompt--answer pairs identifying a particular argument.
     * This will include a lot of QA pairs that don't make sense,
     * because we will be trying all of the different preposition supersenses
     * that could possibly be appropriate for a preposition in queryPrompt.
     */
    public List<BasicQuestionAnswerPair> getAllQAPairsForArgument(int targetArgNum,
                                                                  boolean indefinitesOnly,
                                                                  boolean askAllStandardQuestions,
                                                                  boolean askSupersenseQuestions) {
        Optional<Integer> verbArgNumOpt;
        if(type == QuestionType.VERB_ADJUNCT) {
            verbArgNumOpt = Optional.of(2);
        } else {
            verbArgNumOpt = Optional.empty();
        }
        final List<BasicQuestionAnswerPair> qaPairs = new ArrayList<>();
        if(indefinitesOnly) {
            // these have indefinites in place of everything except the target argument
            List<Map<Integer, Optional<ResolvedDependency>>> justTargetArgPaths = TextGenerationHelper
                .getOnlyTargetAndVerbPaths(allArgDeps, targetArgNum, verbArgNumOpt);
            for(Map<Integer, Optional<ResolvedDependency>> chosenArgDeps : justTargetArgPaths) {
                if(askAllStandardQuestions) {
                    instantiateForArgument(targetArgNum, chosenArgDeps, !indefinitesOnly).forEach(qaPairs::add);
                } else {
                    instantiateForArgument(targetArgNum, chosenArgDeps, !indefinitesOnly).stream().findFirst().ifPresent(qaPairs::add);
                }
                if(askSupersenseQuestions) {
                    getBasicSupersenseQAPairs(targetArgNum, chosenArgDeps).forEach(qaPairs::add);
                }
            }
        } else {
            List<Map<Integer, Optional<ResolvedDependency>>> argPaths = TextGenerationHelper.getAllArgumentChoicePaths(allArgDeps);
            for(Map<Integer, Optional<ResolvedDependency>> chosenArgDeps : argPaths) {
                if(askAllStandardQuestions) {
                    instantiateForArgument(targetArgNum, chosenArgDeps, !indefinitesOnly).forEach(qaPairs::add);
                } else {
                    instantiateForArgument(targetArgNum, chosenArgDeps, !indefinitesOnly).stream().findFirst().ifPresent(qaPairs::add);
                }
                if(askSupersenseQuestions) {
                    getBasicSupersenseQAPairs(targetArgNum, chosenArgDeps).forEach(qaPairs::add);
                }
            }
        }
        return qaPairs;
    }

    private List<BasicQuestionAnswerPair> getBasicSupersenseQAPairs(int targetArgNum, Map<Integer, Optional<ResolvedDependency>> chosenArgDeps) {
        if (cantAskQuestion(targetArgNum, chosenArgDeps)) {
            return new LinkedList<BasicQuestionAnswerPair>();
        }
        // for now, we will only try to do this with prepositional verb adjuncts,
        // when asking about the verb.
        List<BasicQuestionAnswerPair> qaPairs = new LinkedList<>();
        if(Category.valueOf("((S\\NP)\\(S\\NP))/NP").matches(predicateCategory) && targetArgNum == 2 && chosenArgDeps.get(2).isPresent()) {
            for(Supersense supersense : Supersense.getSupersensesFor(words.get(predicateIndex))) {
                qaPairs.add(getBasicSupersenseQAPair(targetArgNum, chosenArgDeps, supersense));
            }
        }
        return qaPairs;
    }

    private BasicQuestionAnswerPair getBasicSupersenseQAPair(int targetArgNum,
                                                        Map<Integer, Optional<ResolvedDependency>> chosenArgDeps,
                                                        Supersense supersense) {
        final ResolvedDependency verbDep = chosenArgDeps.get(2).get();
        final int verbIndex = verbDep.getArgument();
        final List<String> wh = supersense.getWhWords();
        final Set<ResolvedDependency> questionDeps = new HashSet<>();
        final List<String> auxiliaries = getAuxiliariesForPredVerb(verbIndex);
        final String verbArgReplacement = TextGenerationHelper.renderString(getBarePredVerb(verbIndex));
        final TextWithDependencies verbWithDeps = TextGenerationHelper
            .getRepresentativePhrases(Optional.of(verbIndex), argCategories.get(2), parse, verbArgReplacement).get(0);
        questionDeps.addAll(verbWithDeps.dependencies);
        final List<String> verb = verbWithDeps.tokens;
        // first we add the deps to the list of deps we've touched
        // NOTE: verbDep is actually the target dep. so, we're not including it in questionDeps.
        // questionDeps.add(verbDep);
        final Optional<ResolvedDependency> subjDepOpt = chosenArgDeps.get(1);
        subjDepOpt.ifPresent(questionDeps::add);

        // then we locate the subj in the sentence
        final Optional<Integer> subjIndexOpt = subjDepOpt.map(ResolvedDependency::getArgument);
        final Category subjCategory = argCategories.get(1); // this is always NP anyway

        // then we generate the text, again logging the dependencies touched.

        // first, the subject
        List<String> subj = new ArrayList<>();
        if(!subjIndexOpt.isPresent()) {
            TextWithDependencies subjWithDeps = TextGenerationHelper
                .getRepresentativePhrases(subjIndexOpt, subjCategory, parse).get(0);
            questionDeps.addAll(subjWithDeps.dependencies);
            subj = subjWithDeps.tokens;
        } else {
            int subjIndex = subjIndexOpt.get();
            // replace the word with a pronoun of the proper case, if necessary
            Optional<Pronoun> pronounOpt = Pronoun.fromString(words.get(subjIndex));
            Optional<String> fixedPronounString = pronounOpt.map(pron -> pron.withCase(Pronoun.Case.NOMINATIVE).toString());
            TextWithDependencies subjWithDeps = TextGenerationHelper
                .getRepresentativePhrases(subjIndexOpt, subjCategory, parse, fixedPronounString).get(0);
            questionDeps.addAll(subjWithDeps.dependencies);
            subj = subjWithDeps.tokens;
        }

        // and now we construct the queryPrompt
        final List<String> questionWords = new ArrayList<>();
        questionWords.addAll(wh);
        questionWords.addAll(auxiliaries);
        questionWords.addAll(subj);
        questionWords.addAll(verb);
        final List<String> question = questionWords
            .stream()
            .filter(s -> s != null && !s.isEmpty()) // to mitigate oversights. harmless anyway
            .collect(Collectors.toList());
        // and the answer is our PP.
        final ResolvedDependency targetDep = verbDep;
        // TODO get all of them
        final TextWithDependencies answer = TextGenerationHelper
            .getRepresentativePhrases(Optional.of(predicateIndex), Category.valueOf("(S\\NP)\\(S\\NP)"), parse).get(0);
        return new BasicQuestionAnswerPair(sentenceId,
                                           parseId,
                                           parse,
                                           predicateIndex,
                                           predicateCategory,
                                           targetArgNum,
                                           verbIndex,
                                           new BasicQuestionAnswerPair.SupersenseQuestionType(supersense),
                                           questionDeps,
                                           question,
                                           targetDep,
                                           answer);
    }

    private ImmutableList<TextWithDependencies> getAllCandidateNounTWDs(int npIndex) {
        ImmutableList<TextWithDependencies> justNounPhrases = ImmutableList
            .copyOf(TextGenerationHelper.getRepresentativePhrases(Optional.of(npIndex), Category.NP, parse));
        ImmutableList<TextWithDependencies> modifiedNounPhrases = justNounPhrases.stream()
            .flatMap(nounAnswerTWD -> parse.dependencies.stream()
            .filter(dep -> categories.get(dep.getHead()).isFunctionInto(Category.valueOf("NP\\NP")) &&
                    dep.getArgument() == npIndex &&
                    dep.getArgument() != dep.getHead() &&
                    dep.getArgNumber() == 1 &&
                    !(words.get(dep.getHead()).equalsIgnoreCase("of")))
            .flatMap(modifierDep -> TextGenerationHelper.getRepresentativePhrases(Optional.of(modifierDep.getHead()), Category.valueOf("NP\\NP"), parse).stream()
            .map(modifierTWD -> nounAnswerTWD.concatWithDep(modifierTWD, modifierDep))))
            .collect(toImmutableList());
        return new ImmutableList.Builder<TextWithDependencies>()
            .addAll(justNounPhrases)
            .addAll(modifiedNounPhrases)
            .build();
    }

    public ImmutableList<BasicQuestionAnswerPair> getPPAttachmentQAPairs(Map<Integer, Optional<ResolvedDependency>> chosenArgDeps,
                                                                         boolean subjectPronoun) {
        // verb must be our predicate.
        if(type != QuestionType.VERB ||
           VerbHelper.isCopulaVerb(words.get(predicateIndex).toLowerCase())) {
            return ImmutableList.of();
        } else if(cantAskQuestion(1, chosenArgDeps)) { // TODO maybe get rid of this check
            return ImmutableList.of();
        }
        final ImmutableList<String> auxiliaries = ImmutableList.copyOf(getAuxiliariesForArgVerb(predicateIndex));
        final ImmutableList<String> verbPlaceholder = ImmutableList.copyOf(getTargetMainVerbPlaceholder(predicateIndex));
        final ImmutableList<String> verbForAnswer = ImmutableList.copyOf(getAnswerVerb(predicateIndex));
        final ImmutableList<String> verbForQuestion = ImmutableList.copyOf(getNonTargetBareArgumentVerb(predicateIndex));
        final Optional<ResolvedDependency> subjDependencyOpt;
        final Optional<Integer> subjIndexOpt;
        final ImmutableList<TextWithDependencies> subjTWDs;
        if(subjectPronoun) {
            String subjWord = chosenArgDeps.get(1)
                .map(ResolvedDependency::getArgument)
                .map(words::get)
                .flatMap(Pronoun::fromString)
                .filter(Pronoun::isAnimate)
                .map(x -> "someone")
                .orElse("something");
            subjDependencyOpt = Optional.empty();
            subjIndexOpt = Optional.empty();
            subjTWDs = ImmutableList.of(TextWithDependencies.fromWord(subjWord));
        } else {
            subjDependencyOpt = chosenArgDeps.get(1);
            subjIndexOpt = subjDependencyOpt.map(ResolvedDependency::getArgument);
            subjTWDs = ImmutableList.copyOf(TextGenerationHelper.getRepresentativePhrases(subjIndexOpt, Category.NP, parse));
        }

        TextWithDependencies verbAnswerTWD = new TextWithDependencies(verbForAnswer, new HashSet<>());
        final ImmutableList<TextWithDependencies> verbArgumentTWDs = IntStream
            .range(2, predicateCategory.getNumberOfArguments() + 1) // skip the subject
            .boxed()
            .flatMap(argNum -> {
                    Category argCat = predicateCategory.getArgument(argNum);
                    Optional<ResolvedDependency> argDepOpt = chosenArgDeps.get(argNum);
                    Optional<Integer> argIndexOpt = argDepOpt.map(ResolvedDependency::getArgument);
                    return TextGenerationHelper.getRepresentativePhrases(argIndexOpt, argCat, parse).stream()
                    .map(argTWD -> verbAnswerTWD.concatWithDep(argTWD, argDepOpt));
                })
            .collect(toImmutableList());

        ImmutableList<TextWithDependencies> verbAdjunctTWDs = parse.dependencies.stream()
            .filter(dep -> categories.get(dep.getHead()).isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)")) &&
                    dep.getArgument() == predicateIndex &&
                    dep.getArgument() != dep.getHead() &&
                    dep.getArgNumber() == 2)
            .flatMap(targetDep -> TextGenerationHelper
                     .getRepresentativePhrases(Optional.of(targetDep.getHead()), Category.valueOf("(S\\NP)\\(S\\NP)"), parse).stream()
            .map(adverbTWD ->  verbAnswerTWD.concatWithDep(adverbTWD, targetDep)))
            .collect(toImmutableList());

        return subjTWDs.stream().flatMap(subjTWD -> {

        ImmutableList<String> verbAttachmentQuestion = new ImmutableList.Builder<String>()
            .add("What")
            .add(auxiliaries.get(0))
            .addAll(subjTWD.tokens)
            .addAll(auxiliaries.subList(1, auxiliaries.size()))
            .addAll(verbPlaceholder)
            .build();

        ImmutableList<BasicQuestionAnswerPair> verbQAPairs = Stream.concat(verbArgumentTWDs.stream(), verbAdjunctTWDs.stream())
            .map(answerTWD -> new BasicQuestionAnswerPair(sentenceId, parseId, parse,
                                                          predicateIndex, predicateCategory, 1,
                                                          predicateIndex, null, // maybe should get rid of QuestionType?
                                                          ImmutableSet.copyOf(subjTWD.dependencies), verbAttachmentQuestion,
                                                          subjDependencyOpt.orElse(null), answerTWD))
            .collect(toImmutableList());


        final TextWithDependencies nounQuestionTWD =
            new TextWithDependencies(new ImmutableList.Builder<String>()
                                     .add("What").add("is").add("it").add("that")
                                     .addAll(subjTWD.tokens)
                                     .addAll(auxiliaries)
                                     .addAll(verbForQuestion)
                                     .build(),
                                     new ImmutableSet.Builder<ResolvedDependency>()
                                     .addAll(subjTWD.dependencies)
                                     .addAll(subjDependencyOpt.isPresent() ? ImmutableList.of(subjDependencyOpt.get()) : ImmutableList.of())
                                     .build());

        final ImmutableList<BasicQuestionAnswerPair> nounQAPairs = IntStream
            .range(2, predicateCategory.getNumberOfArguments() + 1) // skip the subject
            .boxed()
            .flatMap(argNum -> {
                    Category argCat = predicateCategory.getArgument(argNum);
                    Optional<ResolvedDependency> argDepOpt = chosenArgDeps.get(argNum);
                    if(!argDepOpt.isPresent()) {
                        return Stream.empty();
                    } else {
                        ResolvedDependency argDep = argDepOpt.get();
                        int argIndex = argDep.getArgument();
                        if(Category.NP.matches(argCat)) {
                            return getAllCandidateNounTWDs(argIndex).stream()
                                .map(answerTWD -> new BasicQuestionAnswerPair(sentenceId, parseId, parse,
                                                                              predicateIndex, predicateCategory, argNum,
                                                                              predicateIndex, nounQuestionTWD,
                                                                              argDep, answerTWD));
                        } else if(Category.PP.matches(argCat) && Category.valueOf("PP/NP").matches(categories.get(argIndex))) {
                            String ppWord = words.get(argIndex);
                            return parse.dependencies.stream()
                            .filter(dep -> dep.getHead() == argIndex &&
                                    dep.getArgument() != argIndex &&
                                    dep.getArgNumber() == 1)
                            .flatMap(ppObjDep -> getAllCandidateNounTWDs(ppObjDep.getArgument()).stream()
                            .map(ppObjTWD -> {
                                    int ppObjIndex = ppObjDep.getArgument();
                                    TextWithDependencies nounQuestionWithPrepositionTWD = nounQuestionTWD
                                        .concatWithDep(TextWithDependencies.fromWord(ppWord), argDep);
                                    return new BasicQuestionAnswerPair(sentenceId, parseId, parse,
                                                                       predicateIndex, predicateCategory, argNum,
                                                                       predicateIndex, nounQuestionWithPrepositionTWD,
                                                                       ppObjDep, ppObjTWD);
                                }));
                        } else {
                            return Stream.empty();
                        }
                    }
                })
            .collect(toImmutableList());
        return Stream.concat(nounQAPairs.stream(), verbQAPairs.stream());
        }).collect(toImmutableList());
    }

    public List<BasicQuestionAnswerPair> instantiateForArgument(int targetArgNum,
                                                                Map<Integer, Optional<ResolvedDependency>> chosenArgDeps,
                                                                boolean shouldRecoverMissingSubject) {
        if(cantAskQuestion(targetArgNum, chosenArgDeps)) {
            return new LinkedList<BasicQuestionAnswerPair>();
        }
        // we should never ask about unrealized arguments
        final ResolvedDependency targetDep = chosenArgDeps.get(targetArgNum).get();
        final int targetIndex = targetDep.getArgument();
        final String wh = getWhWordByIndex(targetIndex);
        final List<String> auxiliaries = new ArrayList<>();
        // we need the verb of the clause our predicate appears in,
        // which we will use to determine the auxiliaries we'll be using
        final Optional<Integer> verbIndexOpt;
        if(type == QuestionType.VERB) {
            verbIndexOpt = Optional.of(predicateIndex);
        } else if(type == QuestionType.VERB_ADJUNCT) {
            verbIndexOpt = chosenArgDeps.get(2).map(ResolvedDependency::getArgument);
        } else if(type == QuestionType.RELATIVIZER) {
            verbIndexOpt = chosenArgDeps.get(2).map(ResolvedDependency::getArgument);
        } else {
            verbIndexOpt = Optional.empty();
        }
        // for now, this seems to be a sufficient criterion...
        final boolean shouldSplitVerb = targetArgNum != 1;

        List<String> pred = new ArrayList<>();
        // but if there is no verb, we just put "would be" in there. This works for NP adjuncts.
        if(!verbIndexOpt.isPresent()) {
            auxiliaries.add("would");
            pred.add("be");
            pred.add(words.get(predicateIndex));
        } else {
            int verbIndex = verbIndexOpt.get();
            if(verbIndex == predicateIndex) {
                if(shouldSplitVerb) {
                    // XXX temporary hack to generate the same questions for PP arguments and PP adjuncts
                    if(type == QuestionType.VERB && argCategories.get(targetArgNum).matches(Category.valueOf("PP"))) {
                        auxiliaries.addAll(getAuxiliariesForArgVerb(predicateIndex));
                        pred.addAll(getNonTargetBareArgumentVerb(predicateIndex));
                    } else {
                        auxiliaries.addAll(getAuxiliariesForPredVerb(predicateIndex));
                        pred.addAll(getBarePredVerb(predicateIndex));
                    }
                } else {
                    pred.addAll(getUnsplitPredVerb(predicateIndex));
                }
            } else {
                // do this whether we split the verb or not. whatever.
                auxiliaries.addAll(getAuxiliariesForArgVerb(verbIndex));
                // unless our pred is a relativizer,
                if(type != QuestionType.RELATIVIZER) {
                    // use the non-verb pred.
                    pred.add(words.get(predicateIndex));
                }
            }
        }

        // Add arguments on either side until done, according to CCG category.
        List<TextWithDependencies> lefts = new LinkedList<>();
        lefts.add(new TextWithDependencies(new LinkedList<String>(), new HashSet<ResolvedDependency>()));

        List<TextWithDependencies> rights = new LinkedList<>();
        rights.add(new TextWithDependencies(new LinkedList<String>(), new HashSet<ResolvedDependency>()));

        Category currentCategory = predicateCategory;

        for(int currentArgNum = predicateCategory.getNumberOfArguments(); currentArgNum > 0; currentArgNum--) {
            // get the surface form of the argument in queryPrompt
            final List<TextWithDependencies> argTWDs;
            // TODO: restructure/simplify this, we have lots of things only working because of guarantees established earlier in the code...
            if(currentArgNum == targetArgNum) { // if we're asking about the target, we have to put in placeholder words
                // here we know target dep and target index will be the arg dep and index.
                if(verbIndexOpt.isPresent() && (targetIndex == verbIndexOpt.get())) {
                    // since we're asking about the verb, we're going to include the subject, so we'll split the verb.
                    argTWDs = new LinkedList<TextWithDependencies>();
                    argTWDs.add(new TextWithDependencies(getTargetMainVerbPlaceholder(targetIndex), new HashSet<>()));
                } else {
                    argTWDs = new LinkedList<TextWithDependencies>();
                    argTWDs.add(new TextWithDependencies(getTargetArgumentPlaceholder(currentArgNum, targetIndex), new HashSet<>()));
                }
            } else {
                // this is complicated... consider simplifying.
                final Category argCategory = argCategories.get(currentArgNum);
                Optional<ResolvedDependency> argDepOpt = chosenArgDeps.get(currentArgNum);
                // and now, we have an XXX HACK workaround to get subjects to show up when using adverbs!
                if(!argDepOpt.isPresent() && type == QuestionType.VERB_ADJUNCT &&
                   currentArgNum == 1 && shouldRecoverMissingSubject) {

                    argDepOpt = parse.dependencies
                        .stream()
                        .filter(dep -> dep.getHead() == verbIndexOpt.get() &&
                                dep.getArgument() != dep.getHead() &&
                                dep.getArgNumber() == 1)
                        .findFirst();

                    // add the hacked-in dep to question dependencies (on the left, though doesn't actually matter which side).
                    if(argDepOpt.isPresent()) {
                        Set<ResolvedDependency> extraSubjDep = new HashSet<>();
                        extraSubjDep.add(argDepOpt.get());
                        lefts = lefts.stream()
                            .map(left -> left.concat(new TextWithDependencies(new LinkedList<String>(), extraSubjDep)))
                            .collect(toList());
                    }
                }
                final Optional<Integer> argIndexOpt = argDepOpt.map(ResolvedDependency::getArgument);

                // then we generate the text for that argument, again logging the dependencies touched.
                if(!argIndexOpt.isPresent()) {
                    argTWDs = TextGenerationHelper.getRepresentativePhrases(argIndexOpt, argCategory, parse);
                } else {
                    int argIndex = argIndexOpt.get();
                    if(!verbIndexOpt.isPresent() || (verbIndexOpt.isPresent() && argIndex != verbIndexOpt.get())) {
                        // replace the word with a pronoun of the proper case, if necessary
                        Optional<Pronoun> pronounOpt = Pronoun.fromString(words.get(argIndex));
                        Optional<String> fixedPronounString;
                        if(currentArgNum == 1) { // heuristic for whether we want nominative case.
                            fixedPronounString = pronounOpt.map(pron -> pron.withCase(Pronoun.Case.NOMINATIVE).toString());
                        } else {
                            fixedPronounString = pronounOpt.map(pron -> pron.withCase(Pronoun.Case.ACCUSATIVE).toString());
                        }
                        argTWDs = TextGenerationHelper.getRepresentativePhrases(argIndexOpt, argCategory, parse, fixedPronounString);
                    } else if(verbIndexOpt.isPresent() && argIndex == verbIndexOpt.get()) {
                        String verbArgReplacement = TextGenerationHelper.renderString(getNonTargetBareArgumentVerb(verbIndexOpt.get()));
                        argTWDs = TextGenerationHelper.getRepresentativePhrases(Optional.of(argIndex), argCategory, parse, verbArgReplacement);
                    } else {
                        argTWDs = TextGenerationHelper.getRepresentativePhrases(Optional.of(argIndex), argCategory, parse);
                    }
                }
            }

            // add the argument on the left or right side, depending on the slash
            final Slash slash = currentCategory.getSlash();
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
                assert false : "Undirected slash appeared in supertagged data";
                break;
            }

            // proceed to the next argument
            currentCategory = currentCategory.getLeft();
        }

        final List<BasicQuestionAnswerPair> results = new LinkedList<>();

        for(TextWithDependencies left : lefts) {
            for(TextWithDependencies right : rights) {
                final List<String> questionWords = new ArrayList<>();
                questionWords.add(wh);
                questionWords.addAll(auxiliaries);
                questionWords.addAll(left.tokens);
                questionWords.addAll(pred);
                questionWords.addAll(right.tokens);

                final Set<ResolvedDependency> questionDeps = new HashSet<>();
                for(int argNum : chosenArgDeps.keySet()) {
                    chosenArgDeps.get(argNum).ifPresent(questionDeps::add);
                }
                questionDeps.addAll(left.dependencies);
                questionDeps.addAll(right.dependencies);

                final List<String> question = questionWords
                    .stream()
                    .filter(s -> s != null && !s.isEmpty()) // to mitigate oversights. harmless anyway
                    .collect(Collectors.toList());

                final Category targetCategory = argCategories.get(targetArgNum);
                Optional<String> replaceOpt;
                if(targetCategory.isFunctionInto(Category.valueOf("S\\NP"))) {
                    replaceOpt = Optional.of(TextGenerationHelper.renderString(getBarePredVerb(targetIndex)));
                } else if(targetCategory.isFunctionInto(Category.valueOf("PP"))) {
                    // we don't want to include the preposition in the answer,
                    // since we already included it in the queryPrompt.
                    replaceOpt = Optional.of("");
                } else {
                    replaceOpt = Optional.empty();
                }

                List<TextWithDependencies> answers = TextGenerationHelper
                    .getRepresentativePhrases(Optional.of(targetIndex), targetCategory, parse, replaceOpt);
                for(TextWithDependencies answer : answers) {
                    final BasicQuestionAnswerPair qaPair = new BasicQuestionAnswerPair(sentenceId,
                                                                                       parseId,
                                                                                       parse,
                                                                                       predicateIndex,
                                                                                       predicateCategory,
                                                                                       targetArgNum,
                                                                                       predicateIndex,
                                                                                       new BasicQuestionAnswerPair.StandardQuestionType(type),
                                                                                       questionDeps,
                                                                                       question,
                                                                                       targetDep,
                                                                                       answer);
                    results.add(qaPair);
                }
            }
        }
        return results;
    }

    /**
     * Get the wh-word (and extra words to append to the queryPrompt) associated with
     * the expected answer to a queryPrompt about argument argNum.
     * extra words e.g. in "what did someone he do X for?" "what did someone want X to do?"
     * @return a 2-element array of { "wh-word", "extra words" } where extra words may be empty
     */
    public String getWhWordByIndex(int index) {
        return Optional.of(index)
            .map(words::get)
            .flatMap(Pronoun::fromString)
            .filter(Pronoun::isAnimate)
            .map(x -> "who")
            .orElse("what");
    }

    public List<String> getAuxiliariesForArgVerb(int verbIndex) {
        List<String> result = new ArrayList<>();
        result.add("would");
        return result;
    }

    public List<String> getTargetMainVerbPlaceholder(int argIndex) {
        // category of actual arg as it appears in the sentence.
        Category argCategory = categories.get(argIndex);
        List<String> result = new ArrayList<>();
        // "... X to V" -> "what would X do? -- V"
        if (argCategory.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            result.add("do");
        // "... X verbs Ving adverbly" -> "what would X be doing? -- Ving" <-- let's stick to this
        // "... X is Ving adverbly" -> "what is X doing? -- Ving"
        } else if (argCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
            result.add("be doing");
        // "... X has Ved adverbly" -> "what would X have done? -- Ved" <--
        // "... X is Ving adverbly" -> "what is X doing? -- V"
        } else if (argCategory.isFunctionInto(Category.valueOf("S[pt]\\NP"))) {
            result.add("have done");
        } else if (argCategory.isFunctionInto(Category.valueOf("S[dcl]\\NP"))) {
            result.add("do");
        } else if (argCategory.isFunctionInto(Category.valueOf("S\\NP"))) { // catch-all for verbs
            result.add("do");
        }
        return result;
    }

    /**
     * Assumes the argument at argNum is present.
     */
    public List<String> getTargetArgumentPlaceholder(int argNum, int argIndex) {
        ArrayList<String> result = new ArrayList<>();
        if(type == QuestionType.NOUN_ADJUNCT) {
            return result;
        }
        // only add the placeholder if we expect something verbal.
        if(argCategories.get(argNum).isFunctionInto(Category.valueOf("(S\\NP)"))) {
            // category of actual arg as it appears in the sentence.
            Category argCategory = categories.get(argIndex);
            if (argCategory.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
                result.add("to do");
            } else if (argCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
                result.add("doing");
            } else if (argCategory.isFunctionInto(Category.valueOf("S[pt]\\NP"))) {
                result.add("done");
            } else if (argCategory.isFunctionInto(Category.valueOf("S[dcl]\\NP"))) {
                result.add("do");
            } else if (argCategory.isFunctionInto(Category.valueOf("S\\NP"))) { // catch-all for verbs
                result.add("do");
            }
        } else if(argCategories.get(argNum).isFunctionInto(Category.valueOf("PP"))) {
            // if it's a PP argument, we'll put the PP in the queryPrompt.
            result.add(words.get(argIndex));
        }
        // otherwise maybe it's an S[dcl] or something, in which case we don't want a placeholder.
        return result;
    }

    public List<String> getAnswerVerb(int argIndex) {
        ArrayList<String> result = new ArrayList<>();
        if(type == QuestionType.NOUN_ADJUNCT) {
            return result;
        }
        SyntaxTreeNode verbLeaf = tree.getLeaves().get(argIndex);
        String verb = TextGenerationHelper.getNodeWords(verbLeaf, Optional.empty(), Optional.empty()).get(0);
        String uninflectedVerb = verbHelper.getUninflected(verb).orElse(verb);
        // category of actual arg as it appears in the sentence.
        Category argCategory = categories.get(argIndex);
        if (argCategory.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            result.add(verb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
            result.add(verb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S[pss]\\NP")) ||
                   argCategory.isFunctionInto(Category.valueOf("S[adj]\\NP"))) {
            result.add("be");
            result.add(verb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S[pt]\\NP"))) {
            result.add(verb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S[dcl]\\NP"))) {
            result.add(uninflectedVerb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S\\NP"))) { // catch-all for verbs
            result.add(uninflectedVerb);
        }
        // TODO maybe add preposition
        return result;
    }

    public List<String> getNonTargetBareArgumentVerb(int argIndex) {
        ArrayList<String> result = new ArrayList<>();
        if(type == QuestionType.NOUN_ADJUNCT) {
            return result;
        }
        SyntaxTreeNode verbLeaf = tree.getLeaves().get(argIndex);
        String verb = TextGenerationHelper.getNodeWords(verbLeaf, Optional.empty(), Optional.empty()).get(0);
        String uninflectedVerb = verbHelper.getUninflected(verb).orElse(verb);
        // category of actual arg as it appears in the sentence.
        Category argCategory = categories.get(argIndex);
        if (argCategory.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
            result.add(verb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S[ng]\\NP")) ||
                   argCategory.isFunctionInto(Category.valueOf("S[pss]\\NP")) ||
                   argCategory.isFunctionInto(Category.valueOf("S[adj]\\NP"))) {
            result.add("be");
            result.add(verb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S[pt]\\NP"))) {
            result.add("have");
            result.add(verb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S[dcl]\\NP"))) {
            result.add(uninflectedVerb);
        } else if (argCategory.isFunctionInto(Category.valueOf("S\\NP"))) { // catch-all for verbs
            result.add(uninflectedVerb);
        }
        // TODO maybe add preposition
        return result;
    }


    /**
     * Create the pred as it should be realized in a queryPrompt, possibly with a modal.
     * We try to keep in in the tense/aspect/voice/etc. of the clause it appeared in.
     * @return a 2-element array of { "modal", "verb" } where modal may be empty
     */
    public List<String> getUnsplitPredVerb(int index) {
        String verbStr = words.get(index);
        Category verbCategory = categories.get(index);
        List<Integer> auxiliaries = verbHelper.getAuxiliaryChain(words, categories, index);
        List<String> result = new ArrayList<>();
        // if (predicateCategory.isFunctionInto(Category.valueOf("S[b]\\NP"))) {
        // If we have the infinitive such as "to allow", change it to would allow.
        // TODO more robust might be to do it based on clause type S[to]
        if (auxiliaries.size() > 0 && words.get(auxiliaries.get(0)).equalsIgnoreCase("to")) {
            result.add("would");
        } else if (auxiliaries.size() > 0) {
            String aux = "";
            for (int id : auxiliaries) {
                result.add(words.get(id));
            }
        } else if (verbHelper.isCopulaVerb(words.get(index))) {
            result.add(verbStr);
            Optional<String> negOpt = verbHelper.getCopulaNegation(words, categories, index);
            negOpt.ifPresent(result::add);
            return result;
        } else if (verbCategory.isFunctionInto(Category.valueOf("S[adj]\\NP")) ||
            verbCategory.isFunctionInto(Category.valueOf("S[pss]\\NP")) ||
            verbCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
            result.add("would");
            result.add("be");
        } else if (verbHelper.isUninflected(words, categories, index)) {
            result.add("would");
        }
        result.add(verbStr);
        return result;
    }

    public List<String> getAuxiliariesForPredVerb(int index) {
        List<String> result = new ArrayList<>();
        result.addAll(getSplitPredVerb(index)[0]);
        return result;
    }

    public List<String> getBarePredVerb(int index) {
        List<String> result = new ArrayList<>();
        result.addAll(getSplitPredVerb(index)[1]);
        return result;
    }

    /**
     * If the argument in queryPrompt is not the subject,
     * we will need to split the pred from its auxiliary,
     * e.g., "built" -> {"did", "build"}
     * TODO is the below description correct?
     * @return a 2-element array of { "aux", "pred" }
     */
    public List<String>[] getSplitPredVerb(int index) {
        String verbStr = words.get(index);
        Category verbCategory = categories.get(index);
        List<Integer> auxiliaries = verbHelper.getAuxiliaryChain(words, categories, index);
        List<String>[] result = new ArrayList[2];
        result[0] = new ArrayList<String>();
        result[1] = new ArrayList<String>();
        if (auxiliaries.size() == 0 ) {
            if (verbCategory.isFunctionInto(Category.valueOf("S[adj]\\NP")) || // predicative adjectives
                verbCategory.isFunctionInto(Category.valueOf("S[pss]\\NP")) || // passive verbs
                verbCategory.isFunctionInto(Category.valueOf("S[ng]\\NP"))) { // progressive verbs
                result[0].add("would");
                result[1].add("be");
                result[1].add(verbStr);
                return result;
            } else if (verbCategory.isFunctionInto(Category.valueOf("S[pt]\\NP"))) {
                result[0].add("would");
                result[1].add("have");
                result[1].add(verbStr);
                return result;
            } else if (verbHelper.isCopulaVerb(words.get(index))) {
                result[0].add(verbStr);
                Optional<String> negOpt = verbHelper.getCopulaNegation(words, categories, index);
                negOpt.ifPresent(neg -> {
                        if(neg.equals("not")) {
                            result[1].add(neg);
                        } else if(neg.equals("n't")) {
                            result[0].add(neg);
                        }
                    });
                return result;
            } else {
                Optional<String[]> auxAndVerbStringsOpt = verbHelper.getAuxiliaryAndVerbStrings(words, categories, index);
                if(auxAndVerbStringsOpt.isPresent()) {
                    String[] strs = auxAndVerbStringsOpt.get();
                    result[0].add(strs[0]);
                    result[1].add(strs[1]);
                    return result;
                } else {
                    result[1].add(verbStr);
                    return result;
                }
            }
        } else {
            List<String> rw = getUnsplitPredVerb(index);
            result[0].add(rw.get(0));
            // i.e. What {does n't} someone say ?
            //      What {is n't} someone going to say ?
            if (rw.size() > 1 && VerbHelper.isNegationWord(rw.get(1))) {
                result[0].add(rw.get(1));
                for (int i = 2; i < rw.size(); i++) {
                    result[1].add(rw.get(i));
                }
            } else { // i.e. What {is} someone going to say?
                for (int i = 1; i < rw.size(); i++) {
                    result[1].add(rw.get(i));
                }
            }
        }
        return result;
    }
}
