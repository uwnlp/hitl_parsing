package edu.uw.easysrl.qasrl.reparsing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.corpora.ParseDataLoader;
import edu.uw.easysrl.qasrl.qg.QAPairAggregators;
import edu.uw.easysrl.qasrl.qg.QuestionGenerator;
import edu.uw.easysrl.qasrl.query.QueryFilters;
import edu.uw.easysrl.qasrl.query.QueryGenerators;
import edu.uw.easysrl.qasrl.qg.QuestionGenerationPipeline;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.VerbHelper;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;
import edu.uw.easysrl.syntax.model.Constraint;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Contains data and convenient interface for HITL experiments.
 * Created by luheng on 3/25/16.
 */
public class HITLParser {
    private int nBest = 100;
    private ParseData parseData;
    private ImmutableList<ImmutableList<String>> sentences;
    private ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences;
    private ImmutableList<Parse> goldParses;
    private Map<Integer, NBestList> nbestLists;

    // Query pruning parameters.
    private QueryPruningParameters queryPruningParameters = new QueryPruningParameters();
    public void setQueryPruningParameters(QueryPruningParameters queryPruningParameters) {
        this.queryPruningParameters = queryPruningParameters;
    }

    private ReparsingParameters reparsingParameters = new ReparsingParameters();
    public void setReparsingParameters(ReparsingParameters reparsingParameters) {
        this.reparsingParameters = reparsingParameters;
    }

    private BaseCcgParser.ConstrainedCcgParser reparser;
    private ResponseSimulatorGold goldSimulator;


    public HITLParser(int nBest) {
        this.nBest = nBest;
        parseData = ParseDataLoader.loadFromDevPool().get();
        sentences = parseData.getSentences();
        inputSentences = parseData.getSentenceInputWords();
        goldParses = parseData.getGoldParses();
        System.out.println(String.format("Read %d sentences from the dev set.", sentences.size()));

        String preparsedFile = "parses.tagged.dev.100best.out";
        nbestLists = NBestList.loadNBestListsFromFile(preparsedFile, nBest).get();
        System.out.println(String.format("Load pre-parsed %d-best lists for %d sentences from %s.",
                nBest, nbestLists.size(), preparsedFile));

        reparser = new BaseCcgParser.ConstrainedCcgParser(BaseCcgParser.modelFolder, 1 /* nbest */);
        reparser.cacheSupertags(parseData);
        goldSimulator = new ResponseSimulatorGold(parseData);

        // Cache results.
        nbestLists.entrySet().forEach(e -> e.getValue().cacheResults(goldParses.get(e.getKey())));

        // Print nbest stats
        /* Print stats */
        System.out.println(String.format("Read nBest lists for %d sentences", nbestLists.size()));
        System.out.println(String.format("Average-N:\t%.3f", nbestLists.values().stream()
                .mapToDouble(NBestList::getN).sum() / nbestLists.size()));
        Results baseline = new Results(), oracle = new Results();
        nbestLists.values().forEach(nb -> {
            baseline.add(nb.getResults(0));
            oracle.add(nb.getResults(nb.getOracleId()));
        });
        System.out.println(String.format("Baseline F1:\t%.5f%%\tOracle F1:\t%.5f%%", 100.0 * baseline.getF1(),
                100.0 * oracle.getF1()));
    }

    public HITLParser(ParseData parseData, Map<Integer, NBestList> nbestLists) {
        this.parseData = parseData;
        this.nbestLists = nbestLists;
        sentences = parseData.getSentences();
        inputSentences = parseData.getSentenceInputWords();
        goldParses = parseData.getGoldParses();
        System.out.println(String.format("Read %d sentences from the dev set.", sentences.size()));
        System.out.println(String.format("Load pre-parsed %d-best lists for %d sentences.", nBest, nbestLists.size()));

        reparser = new BaseCcgParser.ConstrainedCcgParser(BaseCcgParser.modelFolder, 1 /* nbest */);
        reparser.cacheSupertags(parseData);
        goldSimulator = new ResponseSimulatorGold(parseData);

        // Cache results, if gold parses are given.
        System.out.println(String.format("Read nBest lists for %d sentences", nbestLists.size()));
        System.out.println(String.format("Average-N:\t%.3f", nbestLists.values().stream()
                .mapToDouble(NBestList::getN).sum() / nbestLists.size()));
        if (!goldParses.isEmpty()) {
            nbestLists.entrySet().forEach(e -> e.getValue().cacheResults(goldParses.get(e.getKey())));
            /* Print stats */
            Results baseline = new Results(), oracle = new Results();
            nbestLists.values().forEach(nb -> {
                baseline.add(nb.getResults(0));
                oracle.add(nb.getResults(nb.getOracleId()));
            });
            System.out.println(String.format("Baseline F1:\t%.5f%%\tOracle F1:\t%.5f%%", 100.0 * baseline.getF1(),
                    100.0 * oracle.getF1()));
        }
    }

    public ImmutableList<Integer> getAllSentenceIds() {
        return nbestLists.keySet().stream().sorted().collect(GuavaCollectors.toImmutableList());
    }

    public ImmutableList<String> getSentence(int sentenceId) {
        return sentences.get(sentenceId);
    }

    public ImmutableList<InputReader.InputWord> getInputSentence(int sentenceId) {
        return inputSentences.get(sentenceId);
    }

    public ParseData getParseData() { return parseData; }

    public NBestList getNBestList(int sentenceId) {
        return nbestLists.get(sentenceId);
    }

    public Parse getGoldParse(int sentenceId) {
        return goldParses.get(sentenceId);
    }

    public Parse getParse(int sentenceId, int parseId) {
        return parseId < 0 ? getGoldParse(sentenceId) : nbestLists.get(sentenceId).getParse(parseId);
    }

    // For older dev questions.
    public ImmutableList<ScoredQuery<QAStructureSurfaceForm>> getPronounCoreArgQueriesForSentence(int sentenceId) {
        final QueryPruningParameters queryPruningParams = new QueryPruningParameters(queryPruningParameters);
        queryPruningParams.skipPPQuestions = true;
        final ImmutableList<String> sentence = sentences.get(sentenceId);
        if(nbestLists.get(sentenceId) == null) {
            return ImmutableList.of();
        }
        ImmutableList<ScoredQuery<QAStructureSurfaceForm>> copulaQueries = generateAllQueries(
                    sentenceId, sentence, nbestLists.get(sentenceId),  false /*usePronouns */, queryPruningParams)
                .stream().filter(query -> {
                    final int predicateId = query.getPredicateId().getAsInt();
                    return VerbHelper.isCopulaVerb(sentence.get(predicateId));
                }).collect(GuavaCollectors.toImmutableList());
        List<ScoredQuery<QAStructureSurfaceForm>> queryList = generateAllQueries(
                        sentenceId, sentence, nbestLists.get(sentenceId), true /*usePronouns */, queryPruningParams)
                .stream().filter(query -> {
                    final int predicateId = query.getPredicateId().getAsInt();
                    return !VerbHelper.isCopulaVerb(sentence.get(predicateId));
                }).collect(Collectors.toList());
        queryList.addAll(copulaQueries);
        queryList.addAll(getNewCoreArgQueriesForSentence(sentenceId));
        // Assign query ids.
        IntStream.range(0, queryList.size()).forEach(i -> queryList.get(i).setQueryId(i));
        return ImmutableList.copyOf(queryList);
    }

    public ImmutableList<ScoredQuery<QAStructureSurfaceForm>> getNewCoreArgQueriesForSentence(int sentenceId) {
        // Skip sentences with an empty n-best list.
        if(nbestLists.get(sentenceId) == null) {
            return ImmutableList.of();
        }
        List<ScoredQuery<QAStructureSurfaceForm>> queryList = QuestionGenerationPipeline.coreArgQGPipeline
                .setQueryPruningParameters(queryPruningParameters)
                .generateAllQueries(sentenceId, nbestLists.get(sentenceId));
        // Assign query ids.
        IntStream.range(0, queryList.size()).forEach(i -> queryList.get(i).setQueryId(i));
        return ImmutableList.copyOf(queryList);
    }

    private static ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateAllQueries(
            final int sentenceId,
            final ImmutableList<String> sentence,
            final NBestList nBestList,
            final boolean usePronouns,
            final QueryPruningParameters queryPruningParameters) {
        QuestionGenerator.setAskPPAttachmentQuestions(false);
        QuestionGenerator.setIndefinitesOnly(usePronouns);
        try {
            return QueryFilters.scoredQueryFilter().filter(
                    QueryGenerators.checkboxQueryGenerator().generate(
                            QAPairAggregators.aggregateForMultipleChoiceQA().aggregate(
                                    QuestionGenerator.generateAllQAPairs(sentenceId, sentence, nBestList))),
                    nBestList, queryPruningParameters);
        } catch (NoSuchElementException e) {
            return ImmutableList.of();
        }
    }

    public static Optional<ScoredQuery<QAStructureSurfaceForm>> getBestAlignedQuery(
            AnnotatedQuery annotation, List<ScoredQuery<QAStructureSurfaceForm>> queries) {
        ScoredQuery<QAStructureSurfaceForm> bestAligned = null;
        int maxNumOverlappingOptions = 2;
        for (ScoredQuery<QAStructureSurfaceForm> query : queries) {
            if (query.getPredicateId().getAsInt() == annotation.predicateId
                    && query.getPrompt().equals(annotation.questionString)) {
                int numOverlappingOptions = (int) annotation.optionStrings.stream()
                        .filter(op -> query.getOptions().contains(op))
                        .count();
                if (numOverlappingOptions > maxNumOverlappingOptions) {
                    bestAligned = query;
                    maxNumOverlappingOptions = numOverlappingOptions;
                }
            }
        }
        return bestAligned != null ? Optional.of(bestAligned) : Optional.empty();
    }

    public ImmutableSet<Constraint> getConstraints(final ScoredQuery<QAStructureSurfaceForm> query,
                                                   final ImmutableList<ImmutableList<Integer>> matchedResponses) {
        return ReparsingHelper.getConstraints(query, matchedResponses, reparsingParameters);
    }


    public Parse getReparsed(int sentenceId, Set<Constraint> constraintSet) {
        if (constraintSet == null || constraintSet.isEmpty()) {
            return nbestLists.get(sentenceId).getParse(0);
        }
        final Parse reparsed = reparser.parseWithConstraint(sentenceId, inputSentences.get(sentenceId), constraintSet);
        if (reparsed == null) {
            System.err.println(String.format("Unable to parse sentence %d with constraints: %s", sentenceId,
                    constraintSet.stream()
                            .map(c -> c.toString(getSentence(sentenceId)))
                            .collect(Collectors.joining("\n"))));
            return nbestLists.get(sentenceId).getParse(0);
        }
        return reparsed;
    }

    public int getRerankedParseId(int sentenceId, Set<Constraint> constraintSet) {
        int rerankedId = 0;
        double bestScore = Double.MIN_VALUE;
        final NBestList nBestList = nbestLists.get(sentenceId);
        for (int i = 0; i < nBestList.getN(); i++) {
            final Parse parse = nBestList.getParse(i);
            final double rerankScore = parse.score + constraintSet.stream()
                    .filter(ev -> ConstraintHelper.isSatisfiedBy(ev, parse))
                    .mapToDouble(ev -> ev.isPositive() ? ev.getStrength() : -ev.getStrength())
                    .sum();
            if (rerankScore > bestScore + 1e-6) {
                rerankedId = i;
                bestScore = rerankScore;
            }
        }
        return rerankedId;
    }

    public ImmutableList<Integer> getGoldOptions(final ScoredQuery<QAStructureSurfaceForm> query) {
        return goldSimulator.respondToQuery(query);
    }

    public ImmutableList<Integer> getOneBestOptions(final ScoredQuery<QAStructureSurfaceForm> query) {
        return IntStream.range(0, query.getOptions().size())
                .filter(i -> query.getOptionToParseIds().get(i).contains(0 /* onebest parse id */))
                .boxed()
                .collect(GuavaCollectors.toImmutableList());
    }

    public ImmutableList<Integer> getOracleOptions(final ScoredQuery<QAStructureSurfaceForm> query) {
        final int oracleParseId = nbestLists.get(query.getSentenceId()).getOracleId();
        return IntStream.range(0, query.getOptions().size())
                .filter(i -> query.getOptionToParseIds().get(i).contains(oracleParseId))
                .boxed()
                .collect(GuavaCollectors.toImmutableList());
    }

    /*
    public ImmutableList<Integer> getUserOptions(final ScoredQuery<QAStructureSurfaceForm> query,
                                                 final int[] optionDist) {
        return IntStream.range(0, query.getOptions().size())
                .filter(i -> (!query.isJeopardyStyle()
                        && optionDist[i] > reparsingParameters.negativeConstraintMaxAgreement) ||
                        (query.isJeopardyStyle()
                                && optionDist[i] >= reparsingParameters.jeopardyQuestionMinAgreement))
                .boxed()
                .collect(GuavaCollectors.toImmutableList());
    }*/

    /*
    public ImmutableSet<Constraint> getConstraints(final ScoredQuery<QAStructureSurfaceForm> query,
                                                   final ImmutableList<Integer> options) {

    }*/

    /*
    public ImmutableSet<Constraint> getOracleConstraints(final ScoredQuery<QAStructureSurfaceForm> query) {
        final Set<Constraint> constraints = new HashSet<>();
        final Parse gold = getGoldParse(query.getSentenceId());
        final ImmutableList<String> sentence = sentences.get(query.getSentenceId());

        for (int i = 0; i < sentence.size(); i++) {
            final int headId = i;
            for (int j = 0; j < sentence.size(); j++) {
                final int argId = j;
                //final DependencyInstanceType dtype = DependencyInstanceHelper.getDependencyType(query, headId, argId);
                final boolean inGold = gold.dependencies.stream()
                        .anyMatch(dep -> dep.getHead() == headId && dep.getArgument() == argId);
                constraints.add(inGold ?
                        new Constraint.AttachmentConstraint(headId, argId, true, reparsingParameters.oraclePenaltyWeight) :
                        new Constraint.AttachmentConstraint(headId, argId, false, reparsingParameters.oraclePenaltyWeight));
            }
        }
        return constraints.stream()
                .distinct()
                .collect(GuavaCollectors.toImmutableSet());
    }*/
}
