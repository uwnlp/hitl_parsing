package edu.uw.easysrl.qasrl.qg.util;

import com.google.common.collect.ImmutableList;

public class QuestionData {
    private final ImmutableList<String> wh;
    private final Predication placeholder;
    private final Predication answer;

    public ImmutableList<String> getWhWords() {
        return wh;
    }

    public Predication getPlaceholder() {
        return placeholder;
    }

    public Predication getAnswer() {
        return answer;
    }

    public QuestionData(ImmutableList<String> wh, Predication placeholder, Predication answer) {
        this.wh = wh;
        this.placeholder = placeholder;
        this.answer = answer;
    }
}
