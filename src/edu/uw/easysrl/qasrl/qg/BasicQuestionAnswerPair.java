package edu.uw.easysrl.qasrl.qg;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;

import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.qg.TextGenerationHelper.TextWithDependencies;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;

public class BasicQuestionAnswerPair implements QuestionAnswerPair {

    public int getSentenceId() {
        return sentenceId;
    }

    public int getParseId() {
        return parseId;
    }

    public int getPredicateIndex() {
        return predicateIndex;
    }

    public Category getPredicateCategory() {
        return predicateCategory;
    }

    public int getArgumentNumber() { return argumentNumber; }

    // A hack for getting the true argument head for PP arguments.
    // This would break if there is coordination under the PP.
    public int getArgumentIndex() {
        // return getPredicateCategory().getArgument(getArgumentNumber()) == Category.PP ?
        //         answerDeps.stream()
        //             .filter(dep -> dep.getCategory() == Category.valueOf("PP/NP"))
        //             .map(ResolvedDependency::getArgument)
        //             .findFirst()
        //             .orElse(targetDep.getArgumentIndex()) :
        //         targetDep.getArgumentIndex();
        return targetDep.getArgumentIndex();
    }

    // TODO: store the immutable sets as fields
    public ImmutableSet<ResolvedDependency> getQuestionDependencies() {
        return ImmutableSet.copyOf(questionDeps);
    }

    public ResolvedDependency getTargetDependency() {
        return targetDep;
    }

    public ImmutableSet<ResolvedDependency> getAnswerDependencies() {
        return ImmutableSet.copyOf(answerDeps);
    }

    public String getQuestion() {
        return renderQuestion();
    }

    public String getAnswer() {
        return renderAnswer();
    }

    public Parse getParse() {
        return parse;
    }

    private final int sentenceId;

    private final int parseId;
    private final Parse parse;

    public final int predicateIndex;
    public final Category predicateCategory;
    public final int argumentNumber;
    public final int questionMainIndex;
    public final QuestionType questionType;
    public final List<String> question;
    public final Set<ResolvedDependency> questionDeps;
    public final ResolvedDependency targetDep;
    public final List<String> answer;
    public final Set<ResolvedDependency> answerDeps;

    // these are lazily loaded by the render methods
    private String questionString = null;
    private String answerString = null;

    // questionMainIndex will be the predicate if we're asking a normal-style queryPrompt,
    // and will be the argument if we're asking a flipped-style queryPrompt.
    public BasicQuestionAnswerPair(int sentenceId, int parseId, Parse parse,
                              int predicateIndex, Category predicateCategory, int argumentNumber,
                              int questionMainIndex, QuestionType questionType,
                              Set<ResolvedDependency> questionDeps, List<String> question,
                              ResolvedDependency targetDep, TextWithDependencies answer) {
        this.predicateIndex = predicateIndex;
        this.predicateCategory = predicateCategory;
        this.argumentNumber = argumentNumber;
        this.questionMainIndex = questionMainIndex;
        this.questionType = questionType;
        this.questionDeps = questionDeps;
        this.question = question;
        this.targetDep = targetDep;
        this.answer = answer.tokens;
        this.answerDeps = answer.dependencies;
        this.parseId = parseId;
        this.sentenceId = sentenceId;
        this.parse = parse;
    }

    public BasicQuestionAnswerPair(int sentenceId, int parseId, Parse parse,
                                   int predicateIndex, Category predicateCategory, int argumentNumber,
                                   int questionMainIndex, TextWithDependencies question,
                                   ResolvedDependency targetDep, TextWithDependencies answer) {
        this.predicateIndex = predicateIndex;
        this.predicateCategory = predicateCategory;
        this.argumentNumber = argumentNumber;
        this.questionMainIndex = questionMainIndex;
        this.questionType = null;
        this.questionDeps = question.dependencies;
        this.question = question.tokens;
        this.targetDep = targetDep;
        this.answer = answer.tokens;
        this.answerDeps = answer.dependencies;
        this.parseId = parseId;
        this.sentenceId = sentenceId;
        this.parse = parse;
    }

    public String renderQuestion() {
        if(questionString == null) {
            String str = TextGenerationHelper.renderString(question);
            if(!str.isEmpty()) {
                str = Character.toUpperCase(str.charAt(0)) + str.substring(1) + "?";
            }
            questionString = str;
        }
        return questionString;
    }

    public String renderAnswer() {
        if(answerString == null) {
            answerString = TextGenerationHelper.renderString(answer);
        }
        return answerString;
    }

    // sheesh, so much effort just for a sane ADT...
    public static interface QuestionType {}

    public static class SupersenseQuestionType implements QuestionType {
        public final MultiQuestionTemplate.Supersense supersense;
        public SupersenseQuestionType(MultiQuestionTemplate.Supersense supersense) {
            this.supersense = supersense;
        }
        public String toString() {
            return supersense.toString();
        }

        public boolean equals(Object o) {
            if(!(o instanceof SupersenseQuestionType)) {
                return false;
            } else {
                return this.toString().equals(o.toString());
            }
        }

        public int hashCode() {
            return toString().hashCode();
        }
    }

    public static class StandardQuestionType implements QuestionType {
        public final MultiQuestionTemplate.QuestionType type;
        public StandardQuestionType(MultiQuestionTemplate.QuestionType type) {
            this.type = type;
        }
        public String toString() {
            return type.toString();
        }
        public boolean equals(Object o) {
            if(!(o instanceof StandardQuestionType)) {
                return false;
            } else {
                return this.toString().equals(o.toString());
            }
        }

        public int hashCode() {
            return toString().hashCode();
        }
    }
}
