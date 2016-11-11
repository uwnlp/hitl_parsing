package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.dependencies.*;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.corpora.ParseDataLoader;
import edu.uw.easysrl.qasrl.util.CountDictionary;
import edu.uw.easysrl.qasrl.util.PropertyUtil;
import edu.uw.easysrl.syntax.evaluation.CCGBankEvaluation;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.model.Constraint;
import edu.uw.easysrl.syntax.parser.*;
import edu.uw.easysrl.syntax.parser.ConstrainedParserAStar;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;
import edu.uw.easysrl.util.Util;
import edu.uw.easysrl.util.Util.Scored;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Calls a parser. Input: List<InputWord>, Output: List<Category>, Set<ResolvedDependency>
 * Created by luheng on 1/5/16.
 */
public abstract class BaseCcgParser {
    public final static String modelFolder = PropertyUtil.resourcesProperties.getProperty("base_model");
    private static HashSet<String> frequentDependenciesSet;
    private static final int minDependencyCount = 10;
    static {
        initializeFilter();
    }

    private static void initializeFilter() {
        final CountDictionary dependencyDict = new CountDictionary();
        final ParseData parseData = ParseDataLoader.loadFromTrainingPool().get();
        parseData.getGoldParses()
                .forEach(parse -> parse.dependencies
                        .forEach(dep -> dependencyDict.addString(dep.getCategory() + "." + dep.getArgNumber())));
        frequentDependenciesSet = IntStream.range(0, dependencyDict.size())
                .filter(i -> dependencyDict.getCount(i) >= minDependencyCount)
                .mapToObj(dependencyDict::getString)
                .collect(Collectors.toCollection(HashSet::new));
        System.out.println("Initialized frequent dependency set:\t" + frequentDependenciesSet.size());
    }

    protected Parse getParse(final List<InputReader.InputWord> sentence, final Scored<SyntaxTreeNode> scoredParse,
                             DependencyGenerator dependencyGenerator) {
        SyntaxTreeNode ccgParse = scoredParse.getObject();
        List<Category> categories = ccgParse.getLeaves().stream().map(SyntaxTreeNode::getCategory)
                .collect(Collectors.toList());
        Set<UnlabelledDependency> unlabelledDeps = new HashSet<>();
        dependencyGenerator.generateDependencies(ccgParse, unlabelledDeps);
        Set<ResolvedDependency> dependencies = CCGBankEvaluation.convertDeps(sentence, unlabelledDeps)
                        .stream()
                        .filter(x -> x.getHead() != x.getArgument())
                        .filter(x -> frequentDependenciesSet.contains(x.getCategory() + "." +  x.getArgNumber()))
                        .collect(Collectors.toSet());
        return new Parse(scoredParse.getObject(), categories, dependencies, scoredParse.getScore());
    }

    public abstract Parse parse(List<InputReader.InputWord> sentence);

    public Parse parse(int sentenceId, List<InputReader.InputWord> sentence) {
        return parse(sentence);
    }

    public abstract List<Parse> parseNBest(List<InputReader.InputWord> sentence);

    public List<Parse> parseNBest(int sentenceId, List<InputReader.InputWord> sentence) {
        return parseNBest(sentence);
    }

    public static class AStarParser extends BaseCcgParser {
        private DependencyGenerator dependencyGenerator;
        private Parser parser;
        private Tagger batchTagger = null;
        private ImmutableList<List<List<Tagger.ScoredCategory>>> taggedSentences = null;

        public AStarParser(String modelFolderPath, int nBest)  {
            this(modelFolderPath, nBest, 1e-4, 1e-6, 1000000, 70);
        }

        public Parser getParser() {
            return parser;
        }

        public AStarParser(String modelFolderPath, int nBest, double supertaggerBeam, double nbestBeam,
                           int maxChartSize, int maxSentenceLength)  {
            final File modelFolder = Util.getFile(modelFolderPath);
            if (!modelFolder.exists()) {
                throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
            }
            System.err.println("====Starting loading model====");
            parser = new ParserAStar.Builder(modelFolder)
                    .supertaggerBeam(supertaggerBeam)
                    .maxChartSize(maxChartSize)
                    .maximumSentenceLength(maxSentenceLength)
                    .nBest(nBest)
                    .nbestBeam(nbestBeam)
                    .build();
            try {
                dependencyGenerator = new DependencyGenerator(modelFolder);
            } catch (Exception e) {
                System.err.println("Parser initialization failed.");
                e.printStackTrace();
            }
            try {
                batchTagger = Tagger.make(modelFolder, supertaggerBeam, 50, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cacheSupertags(ParseData corpus) {
            if (batchTagger != null) {
                System.err.println("Batch tagging " + corpus.getSentences().size() + " sentences ...");
                taggedSentences = batchTagger.tagBatch(corpus.getSentenceInputWords().parallelStream()
                        .map(s -> s.stream().collect(Collectors.toList())))
                        .collect(GuavaCollectors.toImmutableList());
            }
        }

        public void cacheSupertags(ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences) {
            if (batchTagger != null) {
                System.err.println("Batch tagging " + inputSentences.size() + " sentences ...");
                taggedSentences = batchTagger.tagBatch(inputSentences.parallelStream()
                        .map(s -> s.stream().collect(Collectors.toList())))
                        .collect(GuavaCollectors.toImmutableList());
            }
        }

        @Override
        public Parse parse(List<InputReader.InputWord> sentence) {
            List<Scored<SyntaxTreeNode>> parses = parser.doParsing(
                    new InputReader.InputToParser(sentence, null, null, false));
            if (parses == null || parses.size() == 0) {
                return null;
            }
            return getParse(sentence, parses.get(0), dependencyGenerator);
        }

        @Override
        public List<Parse> parseNBest(List<InputReader.InputWord> sentence) {
            List<Scored<SyntaxTreeNode>> parses = parser.doParsing(
                    new InputReader.InputToParser(sentence, null, null, false));
            if (parses == null || parses.size() == 0) {
                return null;
            }
            return parses.stream().map(p -> getParse(sentence, p, dependencyGenerator)).collect(Collectors.toList());
        }

        @Override
        public Parse parse(int sentenceId, List<InputReader.InputWord> sentence) {
            final InputReader.InputToParser input = taggedSentences == null ?
                    new InputReader.InputToParser(sentence, null, null, false) :
                    new InputReader.InputToParser(sentence, null, taggedSentences.get(sentenceId), true);
            List<Scored<SyntaxTreeNode>> parses = parser.doParsing(input);
            if (parses == null || parses.size() == 0) {
                System.err.println("Unable to parse:\t" + taggedSentences);
                return null;
            }
            return getParse(sentence, parses.get(0), dependencyGenerator);
        }

        @Override
        public List<Parse> parseNBest(int sentenceId, List<InputReader.InputWord> sentence) {
            final InputReader.InputToParser input = taggedSentences == null ?
                    new InputReader.InputToParser(sentence, null, null, false) :
                    new InputReader.InputToParser(sentence, null, taggedSentences.get(sentenceId), true);
            if (taggedSentences.get(sentenceId).size() == 0) {
                System.err.println("Untagged sentence:\t" + sentenceId);
                return null;
            }
            List<Scored<SyntaxTreeNode>> parses = parser.doParsing(input);
            if (parses == null || parses.size() == 0) {
                return null;
            }
            return parses.stream().map(p -> getParse(sentence, p, dependencyGenerator)).collect(Collectors.toList());
        }
    }

    public static class ConstrainedCcgParser extends BaseCcgParser {
        private DependencyGenerator dependencyGenerator;
        private ConstrainedParserAStar parser;
        private Tagger batchTagger = null;
        private ImmutableList<List<List<Tagger.ScoredCategory>>> taggedSentences = null;
        private final double supertaggerBeam = 1e-6; // 0.000001;
        private final int maxChartSize = 1000000;
        private final int maxSentenceLength = 70;

        public ConstrainedCcgParser(String modelFolderPath, int nBest) {
            final File modelFolder = Util.getFile(modelFolderPath);
            if (!modelFolder.exists()) {
                throw new InputMismatchException("Couldn't load model from from: " + modelFolder);
            }
            System.err.println("====Starting loading model====");
            parser = new ConstrainedParserAStar.Builder(modelFolder)
                    .supertaggerBeam(supertaggerBeam)
                    .nBest(nBest)
                    .maxChartSize(maxChartSize)
                    .maximumSentenceLength(maxSentenceLength)
                    .build();
            try {
                dependencyGenerator = new DependencyGenerator(modelFolder);
            } catch (Exception e) {
                System.err.println("Parser initialization failed.");
                e.printStackTrace();
            }
            try {
                batchTagger = Tagger.make(modelFolder, supertaggerBeam, 50, null);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void cacheSupertags(ParseData corpus) {
            if (batchTagger != null) {
                System.err.println("Batch tagging " + corpus.getSentences().size() + " sentences ...");
                taggedSentences = batchTagger.tagBatch(corpus.getSentenceInputWords().parallelStream()
                        .map(s -> s.stream().collect(Collectors.toList())))
                        .collect(GuavaCollectors.toImmutableList());
            }
        }

        public void cacheSupertags(ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences) {
            if (batchTagger != null) {
                System.err.println("Batch tagging " + inputSentences.size() + " sentences ...");
                taggedSentences = batchTagger.tagBatch(inputSentences.parallelStream()
                        .map(s -> s.stream().collect(Collectors.toList())))
                        .collect(GuavaCollectors.toImmutableList());
            }
        }

        // TODO: change this.
        @Override
        public Parse parse(List<InputReader.InputWord> sentence) {
            return null;
        }

        @Override
        public List<Parse> parseNBest(List<InputReader.InputWord> sentence) {
            return null;
        }

        @Override
        public Parse parse(int sentenceId, List<InputReader.InputWord> sentence) {
            return parseWithConstraint(sentenceId, sentence, new HashSet<>());
        }

        @Override
        public List<Parse> parseNBest(int sentenceId, List<InputReader.InputWord> sentence) {
            return parseNBestWithConstraint(sentenceId, sentence, new HashSet<>());
        }

        public Parse parseWithConstraint(int sentenceId, List<InputReader.InputWord> sentence,
                                         Set<Constraint> constraintSet) {
            if (sentence.size() > maxSentenceLength) {
                System.err.println("Skipping sentence of length " + sentence.size());
                return null;
            }
            final InputReader.InputToParser input = taggedSentences == null ?
                    new InputReader.InputToParser(sentence, null, null, false) :
                    new InputReader.InputToParser(sentence, null, taggedSentences.get(sentenceId), true);
            List<Scored<SyntaxTreeNode>> parses = parser.parseWithConstraints(input, constraintSet);
            return (parses == null || parses.size() == 0) ? null :
                    getParse(sentence, parses.get(0), dependencyGenerator);
        }

        public List<Parse> parseNBestWithConstraint(int sentenceId, List<InputReader.InputWord> sentence,
                                                    Set<Constraint> constraintSet) {
            if (sentence.size() > maxSentenceLength) {
                System.err.println("Skipping sentence of length " + sentence.size());
                return null;
            }
            final InputReader.InputToParser input = taggedSentences == null ?
                    new InputReader.InputToParser(sentence, null, null, false) :
                    new InputReader.InputToParser(sentence, null, taggedSentences.get(sentenceId), true);
            List<Scored<SyntaxTreeNode>> parses = parser.parseWithConstraints(input, constraintSet);
            return (parses == null || parses.size() == 0) ? null :
                    parses.stream().map(p -> getParse(sentence, p, dependencyGenerator)).collect(Collectors.toList());

        }
    }
}
