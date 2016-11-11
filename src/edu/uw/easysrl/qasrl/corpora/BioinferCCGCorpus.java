package edu.uw.easysrl.qasrl.corpora;

import java.io.*;
import java.util.*;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.BaseCcgParser;
import edu.uw.easysrl.qasrl.util.PropertyUtil;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.Util;

/**
 * Hacky reader..
 * Created by luheng on 5/23/16.
 */
public class BioinferCCGCorpus {
    static final String BioinferDevFile = PropertyUtil.corporaProperties.getProperty("bioinfer") + "GENIA1000.staggedGold";
    static final String BioinferTestFile = PropertyUtil.corporaProperties.getProperty("bioinfer") + "gold.raw";

    final ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences;
    final ImmutableList<ImmutableList<String>> sentences, postags;
    final ImmutableList<ImmutableList<Category>> goldCategories;

    private BioinferCCGCorpus(final ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences,
                              final ImmutableList<ImmutableList<String>> sentences,
                              final ImmutableList<ImmutableList<String>> postags,
                              final ImmutableList<ImmutableList<Category>> goldCategories) {
        this.inputSentences = inputSentences;
        this.sentences = sentences;
        this.postags = postags;
        this.goldCategories = goldCategories;
    }

    public ImmutableList<String> getSentence(int sentenceId) {
        return sentences.get(sentenceId);
    }

    public ImmutableList<ImmutableList<InputReader.InputWord>> getInputSentences() {
        return inputSentences;
    }

    public ImmutableList<InputReader.InputWord> getInputSentence(int sentenceId) {
        return inputSentences.get(sentenceId);
    }

    public static Optional<BioinferCCGCorpus> readDev() {
        POSTagger postagger = POSTagger.getStanfordTagger(Util.getFile(BaseCcgParser.modelFolder + "/posTagger"));
        List<ImmutableList<InputReader.InputWord>> inputSentences = new ArrayList<>();
        List<ImmutableList<String>> sentences = new ArrayList<>(), postags = new ArrayList<>();
        List<ImmutableList<Category>> goldCategories = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(BioinferDevFile)));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] segments = line.split("\\s+");
                List<InputReader.InputWord> inputs = new ArrayList<>();
                List<String> words = new ArrayList<>(), pos = new ArrayList<>();
                List<Category> categories = new ArrayList<>();
                for (String seg : segments) {
                    String[] info = seg.split("\\|");
                    words.add(info[0]);
                    pos.add(info[1]);
                    categories.add(Category.valueOf(info[2]));
                    inputs.add(new InputReader.InputWord(info[0], "", ""));
                }
                if (words.size() > 0) {
                    List<InputReader.InputWord> taggedInputs = postagger.tag(inputs);
                    //System.out.println(taggedInputs.stream().map(InputReader.InputWord::toString).collect(Collectors.joining(" ")));
                    inputSentences.add(ImmutableList.copyOf(taggedInputs));
                    sentences.add(ImmutableList.copyOf(words));
                    postags.add(ImmutableList.copyOf(pos));
                    goldCategories.add(ImmutableList.copyOf(categories));
                }
            }

        } catch (IOException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
        System.out.println(String.format("Read %d sentences from %s.", sentences.size(), BioinferDevFile));
        return Optional.of(new BioinferCCGCorpus(ImmutableList.copyOf(inputSentences), ImmutableList.copyOf(sentences),
                ImmutableList.copyOf(postags), ImmutableList.copyOf(goldCategories)));
    }

    public static Optional<BioinferCCGCorpus> readTest() {
        POSTagger postagger = POSTagger.getStanfordTagger(Util.getFile(BaseCcgParser.modelFolder + "/posTagger"));
        List<ImmutableList<InputReader.InputWord>> inputSentences = new ArrayList<>();
        List<ImmutableList<String>> sentences = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(BioinferTestFile)));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] tokens = line.split("\\s+");
                List<InputReader.InputWord> inputs = new ArrayList<>();
                List<String> words = new ArrayList<>();
                for (String tok : tokens) {
                    words.add(tok);
                    inputs.add(new InputReader.InputWord(tok, "", ""));
                }
                if (words.size() > 0) {
                    List<InputReader.InputWord> taggedInputs = postagger.tag(inputs);
                    inputSentences.add(ImmutableList.copyOf(taggedInputs));
                    sentences.add(ImmutableList.copyOf(words));
                }
            }

        } catch (IOException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
        System.out.println(String.format("Read %d sentences from %s.", sentences.size(), BioinferTestFile));
        return Optional.of(new BioinferCCGCorpus(ImmutableList.copyOf(inputSentences), ImmutableList.copyOf(sentences),
                ImmutableList.of(), ImmutableList.of()));
    }
}