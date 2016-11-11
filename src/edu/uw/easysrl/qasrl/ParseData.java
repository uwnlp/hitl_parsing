package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import static edu.uw.easysrl.qasrl.util.GuavaCollectors.*;

import edu.uw.easysrl.main.InputReader;

/**
 * Data structure to hold information about the sentences and gold parses.
 * This will make it easier to pass this data around and use it for evaluating QA Pairs.
 * It also comes with convenience methods to load the data, taken from DataLoader
 * (which is now deprecated).
 * Created by julianmichael on 3/17/2016.
 */
public final class ParseData {
    private final ImmutableList<ImmutableList<InputReader.InputWord>> sentenceInputWords;
    private final ImmutableList<ImmutableList<String>> sentences;
    private final ImmutableList<Parse> goldParses;

    public ImmutableList<ImmutableList<InputReader.InputWord>> getSentenceInputWords() {
        return sentenceInputWords;
    }

    public ImmutableList<ImmutableList<String>> getSentences() {
        return sentences;
    }

    public ImmutableList<Parse> getGoldParses() {
        return goldParses;
    }

    public ParseData(ImmutableList<ImmutableList<InputReader.InputWord>> sentenceInputWords,
                     ImmutableList<Parse> goldParses) {
        this.sentenceInputWords = sentenceInputWords;
        this.goldParses = goldParses;
        this.sentences = sentenceInputWords
            .stream()
            .map(sentenceIWs -> sentenceIWs
                 .stream()
                 .map(iw -> iw.word)
                 .collect(toImmutableList()))
            .collect(toImmutableList());
    }
}
