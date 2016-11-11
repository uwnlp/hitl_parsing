package edu.uw.easysrl.qasrl.corpora;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.BaseCcgParser;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.ParseData;
import edu.uw.easysrl.syntax.evaluation.CCGBankEvaluation;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.tagger.POSTagger;
import edu.uw.easysrl.util.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static edu.uw.easysrl.qasrl.util.GuavaCollectors.toImmutableList;

/**
 * Created by luheng on 5/24/16.
 */
public class ParseDataLoader {

    private static ParseData makeParseData(List<List<InputReader.InputWord>> sentenceInputWords,
                                           List<Parse> goldParses) {
        ImmutableList<ImmutableList<InputReader.InputWord>> thisSentences = sentenceInputWords
                .stream()
                .map(ImmutableList::copyOf)
                .collect(toImmutableList());
        ImmutableList<Parse> thisGoldParses = goldParses.stream().allMatch(p -> p != null) ?
                goldParses.stream().collect(toImmutableList()) :
                ImmutableList.of();
        return new ParseData(thisSentences, thisGoldParses);
    }

    public static Optional<ParseData> loadFromDevPool() {
        return loadFromPropBank(true);
    }

    public static Optional<ParseData> loadFromTestPool(boolean includeGold) {
        if (includeGold) {
            System.err.println("### Waring ### Reading test data with gold parses.");
        }
        POSTagger postagger = POSTagger.getStanfordTagger(Util.getFile(BaseCcgParser.modelFolder + "/posTagger"));
        List<List<InputReader.InputWord>> sentenceInputWords = new ArrayList<>();
        List<Parse> goldParses = new ArrayList<>();
        Iterator<ParallelCorpusReader.Sentence> sentenceIterator;
        try {
            sentenceIterator = ParallelCorpusReader.READER.readCcgTestSet();
        } catch (IOException e) {
            System.out.println(String.format("Failed to read %d sentences.", sentenceInputWords.size()));
            return Optional.empty();
        }
        while (sentenceIterator.hasNext()) {
            ParallelCorpusReader.Sentence sentence = sentenceIterator.next();
            List<InputReader.InputWord> taggedInput = postagger.tag(sentence.getInputWords());
            sentenceInputWords.add(taggedInput);
            Set<ResolvedDependency> goldDependencies = CCGBankEvaluation
                    .asResolvedDependencies(sentence.getCCGBankDependencyParse().getDependencies());
            if (includeGold) {
                goldParses.add(new Parse(sentence.getCcgbankParse(), sentence.getLexicalCategories(), goldDependencies));
            } else {
                goldParses.add(null);
            }
        }
        System.out.println(String.format("Read %d sentences.", sentenceInputWords.size()));
        return Optional.of(makeParseData(sentenceInputWords, goldParses));
    }

    public static Optional<ParseData> loadFromTrainingPool() {
        return loadFromPropBank(false);
    }

    private static Optional<ParseData> devData = null;
    private static Optional<ParseData> loadFromPropBank(final boolean readDev) {
        if(readDev && devData != null) {
            return devData;
        }
        POSTagger postagger = POSTagger.getStanfordTagger(Util.getFile(BaseCcgParser.modelFolder + "/posTagger"));
        List<List<InputReader.InputWord>> sentenceInputWords = new ArrayList<>();
        List<Parse> goldParses = new ArrayList<>();
        Iterator<ParallelCorpusReader.Sentence> sentenceIterator;
        try {
            sentenceIterator = ParallelCorpusReader.READER.readCcgCorpus(readDev);
        } catch (IOException e) {
            System.out.println(String.format("Failed to read %d sentences.", sentenceInputWords.size()));
            devData = Optional.empty();
            return devData;
        }
        while (sentenceIterator.hasNext()) {
            ParallelCorpusReader.Sentence sentence = sentenceIterator.next();
            List<InputReader.InputWord> taggedInput = postagger.tag(sentence.getInputWords());
            sentenceInputWords.add(taggedInput);
            Set<ResolvedDependency> goldDependencies = CCGBankEvaluation
                    .asResolvedDependencies(sentence.getCCGBankDependencyParse().getDependencies());
            goldParses.add(new Parse(sentence.getCcgbankParse(), sentence.getLexicalCategories(), goldDependencies));
        }
        System.out.println(String.format("Read %d sentences.", sentenceInputWords.size()));
        Optional<ParseData> data = Optional.of(makeParseData(sentenceInputWords, goldParses));
        if(readDev) {
            devData = data;
        }
        return data;
    }

    public static Optional<ParseData> loadFromBioinferDev() {
        POSTagger postagger = POSTagger.getStanfordTagger(Util.getFile(BaseCcgParser.modelFolder + "/posTagger"));
        List<List<InputReader.InputWord>> sentenceInputWords = new ArrayList<>();
        List<Parse> goldParses = new ArrayList<>();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(BioinferCCGCorpus.BioinferDevFile)));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] segments = line.split("\\s+");
                List<InputReader.InputWord> inputs = new ArrayList<>();
                List<String> words = new ArrayList<>(); //, pos = new ArrayList<>();
                List<Category> categories = new ArrayList<>();
                for (String seg : segments) {
                    String[] info = seg.split("\\|");
                    words.add(info[0]);
                    // pos.add(info[1]);
                    categories.add(Category.valueOf(info[2]));
                    inputs.add(new InputReader.InputWord(info[0], "", ""));

                }
                if (words.size() > 0) {
                    sentenceInputWords.add(ImmutableList.copyOf(postagger.tag(inputs)));
                    goldParses.add(new Parse(words, categories));
                }
            }

        } catch (IOException e) {
            return Optional.empty();
        }

        System.out.println(String.format("Read %d sentences from %s.", sentenceInputWords.size(),
                BioinferCCGCorpus.BioinferDevFile));
        return Optional.of(makeParseData(sentenceInputWords, goldParses));
    }
}
