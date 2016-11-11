package edu.uw.easysrl.qasrl.qg.util;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Category.Slash;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import static edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.TextGenerationHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import static edu.uw.easysrl.qasrl.util.GuavaCollectors.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.function.Supplier;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.NoSuchElementException;

public final class Verb extends Predication {

    /* useful grammar enums */

    // not really a tense... but the thing that we need in order to reproduce the verb form.
    public static enum Tense {
        BARE_VERB, TO, MODAL, BARE, PAST, PRESENT, FUTURE
    }

    // not really the voice... but these are exclusive of each other in the right way.
    public static enum Voice {
        ACTIVE, PASSIVE, ADJECTIVE
    }

    /* factory methods */

    public static Verb getFromParse(Integer possiblyAuxHeadIndex, PredicateCache preds, Parse parse) {
        final SyntaxTreeNode tree = parse.syntaxTree;
        final ImmutableList<String> words = tree.getLeaves().stream()
            .map(leaf -> leaf.getWord())
            .collect(toImmutableList());
        int shiftForwardIndex = possiblyAuxHeadIndex;
        if(VerbHelper.isAuxiliaryVerb(words.get(possiblyAuxHeadIndex), parse.categories.get(possiblyAuxHeadIndex))) {
            while(VerbHelper.isAuxiliaryVerb(words.get(shiftForwardIndex), parse.categories.get(shiftForwardIndex)) ||
                  parse.categories.get(shiftForwardIndex).isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)")) ||
                  VerbHelper.isNegationWord(words.get(shiftForwardIndex))) {
                shiftForwardIndex++;
            }
            while(((!parse.categories.get(shiftForwardIndex).isFunctionInto(Category.valueOf("S\\NP")) &&
                    !Category.valueOf("(S[dcl]|S[dcl])|NP").matches(parse.categories.get(shiftForwardIndex))) ||
                   parse.categories.get(shiftForwardIndex).isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)"))) &&
                  shiftForwardIndex > possiblyAuxHeadIndex) {
                shiftForwardIndex--;
            }
        }
        final int headIndex = shiftForwardIndex;
        final SyntaxTreeNodeLeaf headLeaf = tree.getLeaves().get(headIndex);
        final Category initCategory = parse.categories.get(headIndex);

        // final ImmutableMap<Integer, ImmutableList<Argument>> initArgs = Predication.extractArgs(headIndex, initCategory, parse, preds);
        final ImmutableMap<Integer, ImmutableList<Argument>> initArgs = IntStream
            .range(1, initCategory.getNumberOfArguments() + 1)
            .boxed()
            .collect(toImmutableMap(argNum -> argNum, argNum -> parse.dependencies.stream()
                                    .filter(dep -> dep.getHead() == headIndex && dep.getArgument() != headIndex && argNum == dep.getArgNumber())
                                    .flatMap(argDep -> Stream.of(Predication.Type.getTypeForArgCategory(initCategory.getArgument(argDep.getArgNumber())))
                                             .filter(Optional::isPresent).map(Optional::get)
                                             .map(predType -> new Argument(Optional.of(argDep), preds.getPredication(argDep.getArgument(), predType))))
                                    .collect(toImmutableList())));
        final Category predicateCategory;
        final ImmutableMap<Integer, ImmutableList<Argument>> args;
        if(Category.valueOf("(S[dcl]|S[dcl])|NP").matches(initCategory)) { // "...", said one stupid, stupid man.
            Category npCat = initCategory.getArgument(2);
            predicateCategory = Category.valueOf("(S[dcl]\\" + npCat + ")/S[dcl]");
            Function<Integer, Integer> permutation = i -> i == 1 ? 2 : (i == 2 ? 1 : i); // switch 1 and 2
            args = PredicationUtils.permuteArgs(initArgs, permutation);
        } else {
            predicateCategory = initCategory;
            args = initArgs;
        }

        final String predicate;
        if(predicateCategory.isFunctionInto(Category.valueOf("S[adj]\\NP"))) {
            predicate = TextGenerationHelper
                .renderString(TextGenerationHelper
                .getNodeWords(headLeaf, Optional.empty(), Optional.empty()));
        } else {
            // stem the verb if not an adjective
            predicate = VerbHelper
                .getStem(TextGenerationHelper
                .renderString(TextGenerationHelper
                .getNodeWords(headLeaf, Optional.empty(), Optional.empty())));
        }

        if(!predicateCategory.isFunctionInto(Category.valueOf("S\\NP"))) {
            throw new IllegalArgumentException("verb must be a S\\NP (or other known verb); bad word " +
                                               predicate + " (" + predicateCategory + ") in sentence\n" +
                                               TextGenerationHelper.renderString(words));
        }

        // final ImmutableList<Noun> subjects = args.get(1).stream()
        //     .map(arg -> (Noun) arg.getPredication())
        //     .collect(toImmutableList());

        Tense tense = Tense.BARE_VERB;
        Optional<String> modal = Optional.empty();
        boolean isPerfect = false;
        boolean isProgressive = false;
        boolean isNegated = false;
        // Optional<String> negationWord = Optional.empty();
        Voice voice = Voice.ACTIVE;

        final Optional<String> particle;
        if(headIndex + 1 < parse.categories.size() &&
           Category.valueOf("(S\\NP)\\(S\\NP)").matches(parse.categories.get(headIndex + 1)) &&
           !VerbHelper.isNegationWord(words.get(headIndex + 1)) &&
           Preposition.prepositionWords.contains(words.get(headIndex + 1))) {
            particle = Optional.of(words.get(headIndex + 1));
        } else {
            particle = Optional.empty();
        }

        for(int curAuxIndex = headIndex; curAuxIndex >= 0; curAuxIndex--) {
            String aux = words.get(curAuxIndex).toLowerCase();
            Category cat = parse.categories.get(curAuxIndex);

            // negation generally follows the copula; check for it if our main guy is a copula.
            if(curAuxIndex == headIndex && VerbHelper.isCopulaVerb(aux)) {
                String possibleNegation = words.get(curAuxIndex + 1);
                // we need to exclude the case of a "no" because in this position it is probably the determiner of the object or something
                if(VerbHelper.isNegationWord(possibleNegation) && !possibleNegation.equalsIgnoreCase("no")) {
                    isNegated = true;
                }
            }

            if(!VerbHelper.isAuxiliaryVerb(aux, cat) && !VerbHelper.isNegationWord(aux) &&
               cat.isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)"))) {
                // adverbs might be between auxiliaries. including (S\NP)/(S\NP) maybe.
                continue;
            } else if(cat.isFunctionInto(Category.valueOf("S\\NP")) &&
                      !cat.isFunctionInto(Category.valueOf("(S\\NP)|(S\\NP)")) &&
                      !VerbHelper.isAuxiliaryVerb(aux, cat) && !VerbHelper.isNegationWord(aux) &&
                      curAuxIndex != headIndex) {
                // stop at another verb, e.g., "possible to win"
                break;
            } else if(VerbHelper.isNegationWord(aux)) {
                isNegated = true;
            } else if(cat.isFunctionInto(Category.valueOf("S[adj]\\NP"))) {
                voice = Voice.ADJECTIVE;
                tense = Tense.BARE_VERB;
            } else if(cat.isFunctionInto(Category.valueOf("S[pss]\\NP"))) {
                voice = Voice.PASSIVE;
                tense = Tense.BARE_VERB;
            } else if(cat.isFunctionInto(Category.valueOf("S[b]\\NP"))) {
                tense = Tense.BARE_VERB;
            } else if (cat.isFunctionInto(Category.valueOf("S[to]\\NP"))) {
                tense = Tense.TO;
            } else if (cat.isFunctionInto(Category.valueOf("S[pt]\\NP"))) {
                isPerfect = true;
                tense = Tense.BARE_VERB;
            } else if (cat.isFunctionInto(Category.valueOf("S[ng]\\NP"))) {
                isProgressive = true;
                tense = Tense.BARE_VERB;
            } else if (cat.isFunctionInto(Category.valueOf("S[dcl]\\NP"))) {
                if(VerbHelper.isModal(aux)) {
                    tense = Tense.MODAL;
                    modal = Optional.of(VerbHelper.getNormalizedModal(aux));
                } else if(VerbHelper.isFutureTense(aux)) {
                    tense = Tense.FUTURE;
                } else if(VerbHelper.isPastTense(aux)) {
                    tense = Tense.PAST;
                } else if(VerbHelper.isPresentTense(aux)) {
                    tense = Tense.PRESENT;
                } else {
                    // System.err.println("error getting info from S[dcl] for " + aux + "(" + cat + ")");
                    break;
                }
            } else {
                break;
            }
        }

        if(tense != Tense.MODAL) {
            // in case we see another aux after the modal. shouldn't happen, but...with horrid parses, you never know.
            modal = Optional.empty();
        }

        return new Verb(predicate, predicateCategory, args,
                        tense, modal, voice,
                        isPerfect, isProgressive, isNegated,
                        particle);
    }

    // overrides

    @Override
    public ImmutableList<String> getPhrase(Category desiredCategory) {
        // assert getArgs().entrySet().stream().allMatch(e -> e.getValue().size() == 1)
        //     : "can only get phrase for predication with exactly one arg in each slot"; // do i want this?
        // assert getArgs().entrySet().stream().allMatch(e -> e.getValue().size() > 0)
        //     : "can only get phrase for predication with at least one arg in each slot"; // do i want this?
        ImmutableMap<Integer, Argument> args = getArgs().entrySet().stream()
            .collect(toImmutableMap(e -> e.getKey(), e -> e.getValue().size() > 0 ? e.getValue().get(0)
                                    : Argument.withNoDependency(Pronoun.fromString("something").get())));

        final ImmutableList<String> allVerbWords = getVerbWithoutSplit();
        final ImmutableList<String> auxChain = allVerbWords.subList(0, allVerbWords.size() - 1);
        final ImmutableList<String> verbWords = allVerbWords.subList(allVerbWords.size() - 1, allVerbWords.size());

        ImmutableList<String> leftArgs = ImmutableList.of();
        ImmutableList<String> rightArgs = ImmutableList.of();
        Category curCat = getPredicateCategory();

        // add the particle now if we're never going to drop into the while loop below.
        if(desiredCategory.dropFeatures().matches(curCat) && particle.isPresent()) {
            rightArgs = ImmutableList.of(particle.get());
        }

        // also, add the auxiliaries if we're never going to hit that point in the while loop below.
        if(Category.valueOf("(S\\NP)").matches(curCat)) {
            leftArgs = new ImmutableList.Builder<String>()
                .addAll(auxChain)
                .addAll(leftArgs)
                .build();
        }

        // in case there are disagreeing features (we want to ignore them)
        while(!desiredCategory.dropFeatures().matches(curCat)) {
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

            if(particle.isPresent()) {
                // if we just added the first argument, (this will happen exactly once if we're in this loop)
                if(curCat.matches(getPredicateCategory())) {
                    // and the argument is a pronoun or expletive noun,
                    if(curArg instanceof Noun && (((Noun) curArg).isPronoun() || ((Noun) curArg).isExpletive())) {
                        // then we should put the particle on the RIGHT of the rightArgs (e.g., "I showed him up")
                        rightArgs = new ImmutableList.Builder<String>()
                            .addAll(rightArgs)
                            .add(particle.get())
                            .build();
                    } else {
                        // otherwise the particle goes on the LEFT of the right args.
                        rightArgs = new ImmutableList.Builder<String>()
                            .add(particle.get())
                            .addAll(rightArgs)
                            .build();
                    }
                }
            }

            curCat = curCat.getLeft();
            if(Category.valueOf("(S\\NP)").matches(curCat)) {
                leftArgs = new ImmutableList.Builder<String>()
                    .addAll(auxChain)
                    .addAll(leftArgs)
                    .build();
            }
        }

        // ImmutableList<String> prefix;
        // if(Category.valueOf("S[em]").matches(desiredCategory)) {
        //     prefix = ImmutableList.of("that");
        // } else if(Category.valueOf("S[for]").matches(desiredCategory)) {
        //     prefix = ImmutableList.of("for");
        // } else {
        //     prefix = ImmutableList.of();
        // }

        return new ImmutableList.Builder<String>()
            .addAll(leftArgs)
            .addAll(verbWords)
            .addAll(rightArgs)
            .build();
    }

    @Override
    public ImmutableSet<ResolvedDependency> getLocalDependencies() {
        return ImmutableSet.of();
    }

    @Override
    public Verb transformArgs(BiFunction<Integer, ImmutableList<Argument>, ImmutableList<Argument>> transform) {
        return new Verb(getPredicate(), getPredicateCategory(), transformArgsAux(transform),
                        tense, modal, voice, isPerfect, isProgressive, isNegated, particle);
    }

    /* public API */

    // permutation(new arg number) = old arg number
    public Verb permuteArgs(Function<Integer, Integer> permutation) {
        final Category newCategory = PredicationUtils.permuteCategoryArgs(getPredicateCategory(), permutation);
        final ImmutableMap<Integer, ImmutableList<Argument>> newArgs = PredicationUtils.permuteArgs(getArgs(), permutation);
        return new Verb(getPredicate(), newCategory, newArgs,
                        tense, modal, voice, isPerfect, isProgressive, isNegated, particle);
    }

    public Noun getSubject() {
        return (Noun) getArgs().get(1).get(0).getPredication(); // TODO
    }

    public ImmutableList<String> getQuestionWords() {
        assert getArgs().entrySet().stream().allMatch(e -> e.getValue().size() == 1)
            : "can only get question words for predication with exactly one arg in each slot";
        ImmutableMap<Integer, Argument> args = getArgs().entrySet().stream()
            .collect(toImmutableMap(e -> e.getKey(), e -> e.getValue().get(0)));

        ImmutableList<String> leftInternalArgs = ImmutableList.of();
        ImmutableList<String> rightInternalArgs = ImmutableList.of();
        Category done = Category.valueOf("(S\\NP)");
        Category curCat = getPredicateCategory();

        // add the particle now if we're never going to drop into the while loop below.
        if(done.matches(curCat) && particle.isPresent()) {
            rightInternalArgs = ImmutableList.of(particle.get());
        }

        while(!done.matches(curCat)) { // we're not including the subject
            Predication curArg = args.get(curCat.getNumberOfArguments()).getPredication();
            Category curArgCat = curCat.getArgument(curCat.getNumberOfArguments());
            Slash slash = curCat.getSlash();
            switch(slash) {
            case BWD: leftInternalArgs = new ImmutableList.Builder<String>()
                    .addAll(curArg.getPhrase(curArgCat))
                    .addAll(leftInternalArgs)
                    .build();
                break;
            case FWD: rightInternalArgs = new ImmutableList.Builder<String>()
                    .addAll(rightInternalArgs)
                    .addAll(curArg.getPhrase(curArgCat))
                    .build();
                break;
            default: assert false;
            }

            if(particle.isPresent()) {
                // if we just added the first argument, (this will happen exactly once if we're in this loop)
                if(curCat.matches(getPredicateCategory())) {
                    // and the argument is a pronoun or expletive noun,
                    if(curArg instanceof Noun && (((Noun) curArg).isPronoun() || ((Noun) curArg).isExpletive())) {
                        // then we should put the particle on the RIGHT of the rightArgs (e.g., "I showed him up")
                        rightInternalArgs = new ImmutableList.Builder<String>()
                            .addAll(rightInternalArgs)
                            .add(particle.get())
                            .build();
                    } else {
                        // otherwise the particle goes on the LEFT of the right args.
                        rightInternalArgs = new ImmutableList.Builder<String>()
                            .add(particle.get())
                            .addAll(rightInternalArgs)
                            .build();
                    }
                }
            }
            curCat = curCat.getLeft();
        }

        Noun subject = (Noun) args.get(1).getPredication();
        ImmutableList<String> subjWords = subject.getPhrase(getPredicateCategory().getArgument(1));

        // we're going to flip the auxiliary only if the subject's string form is nonempty
        boolean flipAuxiliary = subjWords.size() > 0;

        ImmutableList<String> questionPrefix;
        ImmutableList<String> verbWords;
        if(!flipAuxiliary) { // if subject is not present
            ImmutableList<String> allVerbWords = getVerbWithoutSplit();
            ImmutableList<String> auxChain = allVerbWords.subList(0, allVerbWords.size() - 1);
            verbWords = allVerbWords.subList(allVerbWords.size() - 1, allVerbWords.size());
            questionPrefix = new ImmutableList.Builder<String>()
                .addAll(auxChain)
                .build();
        } else { // if we have a subject and need to flip the auxiliary
            // ImmutableList<String> wordsForFlip = getVerbWithSplit();
            Deque<String> wordsForFlip = new LinkedList<>(getVerbWithSplit());
            String flippedAux = wordsForFlip.removeFirst();
            try {
                verbWords = ImmutableList.of(wordsForFlip.removeLast());
            } catch(NoSuchElementException e) {
                verbWords = ImmutableList.of(); // no verb words if we flipped a copula
            }
            ImmutableList<String> remainingAuxChain = wordsForFlip.stream().collect(toImmutableList());
            questionPrefix = new ImmutableList.Builder<String>()
                .add(flippedAux)
                .addAll(subjWords)
                .addAll(remainingAuxChain)
                .build();
        }

        ImmutableList<String> result = new ImmutableList.Builder<String>()
            .addAll(questionPrefix)
            .addAll(leftInternalArgs)
            .addAll(verbWords)
            .addAll(rightInternalArgs)
            .build();

        return result;
    }

    public Tense getTense() {
        return tense;
    }

    public Optional<String> getModal() {
        return modal;
    }

    public Voice getVoice() {
        return voice;
    }

    public boolean isPerfect() {
        return isPerfect;
    }

    public boolean isProgressive() {
        return isProgressive;
    }

    public boolean isNegated() {
        return isNegated;
    }

    public Verb withTense(Tense newTense) {
        if(newTense == Tense.MODAL) {
            throw new IllegalArgumentException("must use withModal() to give modal tense");
        }
        return new Verb(getPredicate(), getPredicateCategory(), getArgs(),
                        newTense, Optional.empty(),
                        voice, isPerfect, isProgressive, isNegated, particle);
    }

    public Verb withModal(String modal) {
        return new Verb(getPredicate(), getPredicateCategory(), getArgs(),
                        Tense.MODAL, Optional.of(modal),
                        voice, isPerfect, isProgressive, isNegated, particle);
    }

    public Verb withPerfect(boolean newPerfect) {
        return new Verb(getPredicate(), getPredicateCategory(), getArgs(),
                        tense, modal,
                        voice, newPerfect, isProgressive, isNegated, particle);
    }

    public Verb withProgressive(boolean newProgressive) {
        return new Verb(getPredicate(), getPredicateCategory(), getArgs(),
                        tense, modal,
                        voice, isPerfect, newProgressive, isNegated, particle);
    }

    public Verb withNegation(boolean newNegated) {
        return new Verb(getPredicate(), getPredicateCategory(), getArgs(),
                        tense, modal,
                        voice, isPerfect, isProgressive, newNegated, particle);
    }

    /* other public methods */

    public boolean isCopular() {
        return getPredicate().equals("be");
    }

    /* protected methods */

    // TODO: make a pro-verb constructor so this can be protected again
    public Verb(String predicate,
                   Category predicateCategory,
                   ImmutableMap<Integer, ImmutableList<Argument>> args,
                   Tense tense,
                   Optional<String> modal,
                   Voice voice,
                   boolean isPerfect,
                   boolean isProgressive,
                   boolean isNegated,
                   Optional<String> particle) {
        super(predicate, predicateCategory, args);
        this.tense = tense;
        this.modal = modal;
        this.voice = voice;
        this.isPerfect = isPerfect;
        this.isProgressive = isProgressive;
        this.isNegated = isNegated;
        this.particle = particle;
        validate();
    }

    protected Verb(String predicate,
                   Category predicateCategory,
                   Supplier<ImmutableMap<Integer, ImmutableList<Argument>>> argSupplier,
                   Tense tense,
                   Optional<String> modal,
                   Voice voice,
                   boolean isPerfect,
                   boolean isProgressive,
                   boolean isNegated,
                   Optional<String> particle) {
        super(predicate, predicateCategory, argSupplier);
        this.tense = tense;
        this.modal = modal;
        this.voice = voice;
        this.isPerfect = isPerfect;
        this.isProgressive = isProgressive;
        this.isNegated = isNegated;
        this.particle = particle;
        validate();
    }

    /* private fields and methods */

    private final Tense tense;
    private final Optional<String> modal; // populated iff tense == MODAL
    private final Voice voice;
    private final boolean isPerfect;
    private final boolean isProgressive;
    private final boolean isNegated;
    private final Optional<String> particle; // read in as an adverb that directly follows the verb

    private void validate() {
        boolean isVerb = getPredicateCategory().isFunctionInto(Category.valueOf("S\\NP")) &&
            !getPredicateCategory().isFunctionInto(Category.valueOf("(S\\NP)\\(S\\NP)"));
        if(!isVerb) {
            throw new IllegalArgumentException("verb predication must be over a verb");
        }
        if((tense == Tense.MODAL) != modal.isPresent()) {
            throw new IllegalArgumentException("modal word must be present iff verb has a modal tense type");
        }
        if(getArgs().get(1).stream().anyMatch(arg -> !(arg.getPredication() instanceof Noun))) {
            throw new IllegalArgumentException("subject of verb predication must be a Noun");
        }
    }

    private ImmutableList<String> getVerbWithSplit() {
        Deque<String> verbStack = getVerbWordStack();
        assert verbStack.size() > 0;
        if(verbStack.size() == 1) {
            assert !isNegated : "aux flip should not cause changes when negated";
            splitVerb(verbStack);
        }
        assert verbStack.size() >= 2 || VerbHelper.isCopulaVerb(verbStack.getFirst())
            : "Verb words need to be allow for auxiliary flip";
        return ImmutableList.copyOf(verbStack);
    }

    private ImmutableList<String> getVerbWithoutSplit() {
        return ImmutableList.copyOf(getVerbWordStack());
    }

    private Deque<String> getVerbWordStack() {
        Deque<String> verbStack = new LinkedList<>();

        if(tense == Tense.BARE_VERB) {
            switch(voice) {
            case PASSIVE: verbStack.addFirst(VerbHelper.getPastParticiple(getPredicate())); break;
            case ADJECTIVE: verbStack.addFirst(getPredicate()); break;
            case ACTIVE:
                if(isPerfect) {
                    verbStack.addFirst(VerbHelper.getPastParticiple(getPredicate()));
                } else if(isProgressive) {
                    verbStack.addFirst(VerbHelper.getPresentParticiple(getPredicate()));
                } else {
                    verbStack.addFirst(getPredicate());
                }
                break;
            }
            return verbStack;
        } else {
            verbStack.addFirst(getPredicate()); // this should be the stem form of the verb.

            // both adjective and progressive: being proud of myself ... who would be proud?
            // both passive   and progressive: being used by someone ... who would be used?
            // I don't want to ask "who would be being proud" etc.; so, we forget progressive in these cases.
            if(voice == Voice.ADJECTIVE || voice == Voice.PASSIVE) {
                if(voice == Voice.PASSIVE) {
                    verbStack.addFirst(VerbHelper.getPastParticiple(verbStack.removeFirst()));
                }
                verbStack.addFirst("be");
            } else if(isProgressive) {
                String verbStem = verbStack.removeFirst();
                String verbProg = VerbHelper.getPresentParticiple(verbStem);
                verbStack.addFirst(verbProg);
                verbStack.addFirst("be");
            }

            if(isPerfect) {
                String verbStem = verbStack.removeFirst();
                String verbParticiple = VerbHelper.getPastParticiple(verbStem);
                verbStack.addFirst(verbParticiple);
                verbStack.addFirst("have");
            }

            switch(tense) {
            case BARE: break;
            case TO: verbStack.addFirst("to"); break;
            case MODAL: verbStack.addFirst(modal.get()); break;
            case PAST: verbStack.addFirst(VerbHelper.getPastTense(verbStack.removeFirst(), getSubject())); break;
            case PRESENT: verbStack.addFirst(VerbHelper.getPresentTense(verbStack.removeFirst(), getSubject())); break;
            case FUTURE: verbStack.addFirst("will"); break;
            default: assert false;
            }

            if(isNegated) {
                if(verbStack.size() == 1) {
                    splitVerb(verbStack);
                }
                String top = verbStack.removeFirst();
                verbStack.addFirst("not"); // let's not bother with contractions
                verbStack.addFirst(top);
            }

            return verbStack;
        }
    }

    private void splitVerb(Deque<String> verbStack) {
        // assert verbStack.size() == 1 &&
        //     voice == Voice.ACTIVE &&
        //     !isProgressive && !isPerfect &&
        //     (tense == Tense.PAST || tense == Tense.PRESENT)
        //     : "verb should only be split in very specific circumstances";
        if(verbStack.size() == 1 && VerbHelper.isCopulaVerb(verbStack.getFirst())) {
            // if all we are is a copula, we flip the whole thing to the front without splitting.
            return;
        } else {
            verbStack.addFirst(VerbHelper.getStem(verbStack.removeFirst()));
            switch(tense) {
            case PAST: verbStack.addFirst(VerbHelper.getPastTense("do", getSubject())); break;
            case PRESENT: verbStack.addFirst(VerbHelper.getPresentTense("do", getSubject())); break;
            default: assert false;
            }
            assert verbStack.size() > 1; // should have at least two words at the end in this case
        }
    }
}
