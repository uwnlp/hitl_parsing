package edu.uw.easysrl.qasrl.qg.util;

import java.util.Optional;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.TextGenerationHelper;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public abstract class Noun extends Predication {

    /* useful grammar enums */

    public static enum Case {
        NOMINATIVE, ACCUSATIVE
    }

    public static enum Number {
        SINGULAR, PLURAL
    }

    public static enum Gender {
        FEMALE, MALE, ANIMATE, INANIMATE;

        public boolean isAnimate() {
            return this != INANIMATE;
        }
    }

    public static enum Person {
        FIRST, SECOND, THIRD
    }

    public static enum Definiteness {
        DEFINITE, INDEFINITE, FOCAL
    }

    /* factory methods */
    public static Noun getFromParse(Integer headIndex, Parse parse) {
        return getFromParse(headIndex, parse, Optional.empty());
    }

    public static Noun getFromParse(Integer headIndex, Parse parse, Optional<SyntaxTreeNode> presetNPNodeOpt) {
        final SyntaxTreeNode tree = parse.syntaxTree;
        final SyntaxTreeNodeLeaf headLeaf = tree.getLeaves().get(headIndex);

        /* recover the whole noun phrase. */
        final Optional<SyntaxTreeNode> npNodeOpt = TextGenerationHelper
            .getLowestAncestorSatisfyingPredicate(headLeaf, node -> Category.NP.matches(node.getCategory()), tree);
        final SyntaxTreeNode phraseNode;
        if(presetNPNodeOpt.isPresent()) {
            phraseNode = presetNPNodeOpt.get();
        } else if(!npNodeOpt.isPresent() || npNodeOpt.get().getHeadIndex() != headIndex) {
            // should always be present because leaf's head index is its own
            final SyntaxTreeNode candidatePhraseNode = TextGenerationHelper
                .getHighestAncestorStillSatisfyingPredicate(headLeaf, node -> node.getHeadIndex() == headIndex, tree).get();
            // this is a hack to get around the fact that when "and" combines with a "that"-CP, it leaves the right guy as the head,
            // so we pick up the undesired "and". now we wish to shed it.
            if(candidatePhraseNode instanceof SyntaxTreeNode.SyntaxTreeNodeBinary &&
               candidatePhraseNode.getChild(0) instanceof SyntaxTreeNode.SyntaxTreeNodeLeaf &&
               ((SyntaxTreeNode.SyntaxTreeNodeLeaf) candidatePhraseNode.getChild(0)).getPos().equals("CC")) {
                phraseNode = candidatePhraseNode.getChild(1);
            } else {
                Optional<SyntaxTreeNode> parentOpt = TextGenerationHelper.getParent(candidatePhraseNode, tree);
                if(parentOpt.isPresent() &&
                   parentOpt.get().getChild(0) instanceof SyntaxTreeNode.SyntaxTreeNodeLeaf &&
                   ((SyntaxTreeNode.SyntaxTreeNodeLeaf) parentOpt.get().getChild(0)).getPos().equals("DT")) {
                    phraseNode = parentOpt.get();
                }
                else {
                    phraseNode = candidatePhraseNode;
                }
            }

        } else {
            final SyntaxTreeNode npNode = npNodeOpt.get();
            final Category npNodeCategory = npNode.getCategory();
            if(Category.valueOf("NP[thr]").matches(npNodeCategory)) {
                return ExpletiveNoun.there;
            } else if(Category.valueOf("NP[expl]").matches(npNodeCategory)) {
                return ExpletiveNoun.it;
            } else {
                phraseNode = npNode;
            }
        }

        final String predicate = TextGenerationHelper.renderString(TextGenerationHelper.getNodeWords(headLeaf, Optional.empty(), Optional.empty()));

        // if it's a pronoun, we're done
        final Optional<Pronoun> pronounOpt = Pronoun.fromString(predicate);
        if(pronounOpt.isPresent()) {
            return pronounOpt.get();
        }
        // otherwise, we have a lot more work to do.

        /* extract grammatical features. */
        // this is fine because none of our nouns take arguments, right?
        final ImmutableMap<Integer, ImmutableList<Argument>> args = ImmutableMap.of();

        // only pronouns are case marked
        final Optional<Case> caseMarking = Optional.empty();

        final String nounPOS = headLeaf.getPos();
        final Optional<Number> number;
        if(nounPOS.equals("NN") || nounPOS.equals("NNP") || nounPOS.equals("VBG") || nounPOS.equals("$")) {
            number = Optional.of(Number.SINGULAR);
        } else if(nounPOS.equals("NNS") || nounPOS.equals("NNPS")) {
            number = Optional.of(Number.PLURAL);
        } else if(nounPOS.equals("CD")) {
            if(predicate.equalsIgnoreCase("one") || predicate.equalsIgnoreCase("1")) {
                number = Optional.of(Number.SINGULAR);
            } else {
                number = Optional.of(Number.PLURAL);
            }
        } else {
            // TODO take care of more cases, perhaps by adding more pronouns
            // System.err.println(String.format("noun %s has mysterious POS %s", headLeaf.getWord(), nounPOS));
            number = Optional.empty();
        }

        // TODO we could try and predict this... not clear how though.
        // heuristic: proper nouns animate, all others inanimate
        final Optional<Gender> gender;
        if(nounPOS.equals("NNP") || nounPOS.equals("NNPS")) {
            gender = Optional.of(Gender.ANIMATE);
        } else {
            gender = Optional.of(Gender.INANIMATE);
        }

        // only pronouns can be non-third person
        final Person person = Person.THIRD;

        final Optional<Definiteness> definitenessOpt = phraseNode.getLeaves().stream()
            .filter(leaf -> leaf.getPos().equals("DT") || leaf.getPos().equals("WDT"))
            .findFirst()
            .flatMap(leaf -> {
                    if(leaf.getWord().equalsIgnoreCase("the")) {
                        return Optional.of(Definiteness.DEFINITE);
                    } else if(leaf.getWord().equalsIgnoreCase("a") || leaf.getWord().equalsIgnoreCase("an")) {
                        return Optional.of(Definiteness.INDEFINITE);
                    } else if(leaf.getPos().equals("WDT")) {
                        return Optional.of(Definiteness.FOCAL);
                    } else {
                        return Optional.empty();
                    }
                });
        final Definiteness definiteness;
        // heuristics: if it's proper, assume definite. otherwise if it's plural, assume indefinite.
        if(definitenessOpt.isPresent()) {
            definiteness = definitenessOpt.get();
        } else if(headLeaf.getPos().equals("NNP") || headLeaf.getPos().equals("NNPS")) {
            definiteness = Definiteness.DEFINITE;
        } else if(headLeaf.getPos().equals("NNS")) {
            definiteness = Definiteness.INDEFINITE;
        } else {
            // System.err.println("couldn't establish definiteness for [" + phraseNode.getWord() + "]");
            definiteness = Definiteness.INDEFINITE;
        }

        /* include an of-phrase if necessary. */
        final ImmutableList<String> words;
        final ImmutableSet<ResolvedDependency> deps;
        if(phraseNode.getEndIndex() < tree.getEndIndex() &&
           tree.getLeaves().get(phraseNode.getEndIndex()).getWord().equals("of") &&
           tree.getLeaves().get(phraseNode.getEndIndex()).getCategory().isFunctionInto(Category.valueOf("NP\\NP"))) {
            final SyntaxTreeNode ofNode = tree.getLeaves().get(phraseNode.getEndIndex());
            final Optional<SyntaxTreeNode> phraseNodeWithOfOpt = TextGenerationHelper.getLowestAncestorOfNodes(phraseNode, ofNode, tree);
            if(phraseNodeWithOfOpt.isPresent()) {
                words = ImmutableList.copyOf(TextGenerationHelper.getNodeWords(phraseNodeWithOfOpt.get(), Optional.empty(), Optional.empty()));
                deps = TextGenerationHelper.getContainedDependencies(phraseNodeWithOfOpt.get(), parse);
            } else {
                words = ImmutableList.copyOf(TextGenerationHelper.getNodeWords(phraseNode, Optional.empty(), Optional.empty()));
                deps = TextGenerationHelper.getContainedDependencies(phraseNode, parse);
            }
        } else {
            words = ImmutableList.copyOf(TextGenerationHelper.getNodeWords(phraseNode, Optional.empty(), Optional.empty()));
            deps = TextGenerationHelper.getContainedDependencies(phraseNode, parse);
        }
        return new BasicNoun(predicate, Category.NP,
                             ImmutableMap.of(),
                             caseMarking, number, gender, person, definiteness,
                             words, deps, false);
    }

    /* public API */

    // overrides

    // transformArgs is left abstract
    // getPhrase is left abstract

    // getters

    public Optional<Case> getCase() {
        return caseMarking;
    }

    public Optional<Number> getNumber() {
        return number;
    }

    public Optional<Gender> getGender() {
        return gender;
    }

    public Person getPerson() {
        return person;
    }

    public Definiteness getDefiniteness() {
        return definiteness;
    }

    // convenience methods

    public final boolean isExpletive() {
        return getPredicate().equals(ExpletiveNoun.PRED);
    }

    public final boolean isAnimate() {
        return gender.map(Gender::isAnimate).orElse(false);
    }

    public final boolean isFocal() {
        return definiteness == Definiteness.FOCAL;
    }

    public final boolean isElided() {
        return isElided;
    }

    // transformers -- subclasses need to override

    public abstract Noun withCase(Case caseMarking);
    public abstract Noun withCase(Optional<Case> caseMarking);

    public abstract Noun withNumber(Number number);
    public abstract Noun withNumber(Optional<Number> number);

    public abstract Noun withGender(Gender gender);
    public abstract Noun withGender(Optional<Gender> gender);

    public abstract Noun withPerson(Person person);

    public abstract Noun withDefiniteness(Definiteness definiteness);

    public abstract boolean isPronoun();
    public abstract Pronoun getPronoun();

    public abstract Noun getPronounOrExpletive();
    public abstract Noun withElision(boolean shouldElide);

    /* protected methods and fields */

    protected Noun(String predicate,
                   Category predicateCategory,
                   ImmutableMap<Integer, ImmutableList<Argument>> args,
                   Optional<Case> caseMarking,
                   Optional<Number> number,
                   Optional<Gender> gender,
                   Person person,
                   Definiteness definiteness,
                   boolean isElided) {
        super(predicate, predicateCategory, args);
        this.caseMarking = caseMarking;
        this.number = number;
        this.gender = gender;
        this.person = person;
        this.definiteness = definiteness;
        this.isElided = isElided;
    }

    /* private fields */

    private final Optional<Case> caseMarking;
    private final Optional<Number> number;
    private final Optional<Gender> gender;
    private final Person person;
    private final Definiteness definiteness;

    private boolean isElided;
}
