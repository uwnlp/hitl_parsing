package edu.uw.easysrl.qasrl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import edu.uw.easysrl.qasrl.evaluation.Accuracy;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.syntax.evaluation.Results;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.IntStream;

import java.io.*;

import static edu.uw.easysrl.qasrl.util.GuavaCollectors.*;

/**
 * Holds an n-best list of parses.
 * Reranking/reparsing involves transforming one n-best list into another,
 * on the basis of annotations from crowd workers.
 *
 * Holds scores separate from the list of parses in case we want to re-score them as in reranking.
 *
 * Created by julianmichael on 3/18/16.
 */
public final class NBestList {
    private final ImmutableList<Parse> parses;
    private final ImmutableList<Double> scores;
    private ImmutableList<Results> results;
    private int oracleId;

    public int getN() {
        return parses.size();
    }

    public ImmutableList<Parse> getParses() {
        return parses;
    }

    public Parse getParse(final int parseId) {
        return parses.get(parseId);
    }

    public ImmutableList<Double> getScores() {
        return scores;
    }

    public Double getScore(int parseId) {
        return scores.get(parseId);
    }

    public int getOracleId() {
        return oracleId;
    }

    public Results getResults(int parseId) {
        return results.get(parseId);
    }


    public void cacheResults(final Parse goldParse) {
        if (goldParse.dependencies != null) {
            results = ImmutableList.copyOf(CcgEvaluation.evaluateNBest(parses, goldParse.dependencies));
        } else {
            results = parses.stream()
                    .map(parse -> {
                        final Accuracy acc = CcgEvaluation.evaluateTags(parse.categories, goldParse.categories);
                        return new Results(acc.getNumTotal(), acc.getNumCorrect(), acc.getNumTotal());
                    })
                    .collect(GuavaCollectors.toImmutableList());
        }
        oracleId = 0;
        for (int k = 1; k < parses.size(); k++) {
            if (results.get(k).getF1() > results.get(oracleId).getF1()) {
                oracleId = k;
            }
        }
    }

    public NBestList(ImmutableList<Parse> parses, ImmutableList<Double> scores) {
        this.parses = parses;
        this.scores = scores;
    }

    /**
     * uses the parser-assigned scores
     */
    public NBestList(ImmutableList<Parse> parses) {
        this.parses = parses;
        this.scores = parses.stream()
            .map(p -> p.score)
            .collect(toImmutableList());
    }

    /* Factory methods */

    public static Optional<NBestList> getNBestList(final BaseCcgParser parser, int sentenceId,
                                         final List<InputReader.InputWord> inputSentence) {
        return Optional
            .ofNullable(parser.parseNBest(sentenceId, inputSentence))
            .map(ImmutableList::copyOf)
            .map(NBestList::new);
    }

    public static ImmutableMap<Integer, NBestList> getAllNBestLists(
            final BaseCcgParser parser,
            final ImmutableList<ImmutableList<InputReader.InputWord>> inputSentences) {
        Map<Integer, NBestList> allParses = new HashMap<>();
        IntStream
            .range(0, inputSentences.size()).boxed()
            .forEach(sentenceId -> getNBestList(parser, sentenceId, inputSentences.get(sentenceId))
                     .ifPresent(nBestList -> allParses.put(sentenceId, nBestList)));
        return ImmutableMap.copyOf(allParses);
    }

    public static Optional<ImmutableMap<Integer, NBestList>> loadNBestListsFromFile(String filepath, int n) {
        Map<Integer, NBestList> allNBestLists = new HashMap<>();
        Map<Integer, List<Parse>> readParses;
        try {
            ObjectInputStream inputStream = new ObjectInputStream(new BufferedInputStream(new FileInputStream(filepath)));
            readParses = (Map<Integer, List<Parse>>) inputStream.readObject();
        }  catch(Exception e){
            e.printStackTrace();
            return Optional.empty();
        }
        readParses.forEach((sentIdx, parses) ->
                           allNBestLists.put(sentIdx,
                                             new NBestList(ImmutableList.copyOf((parses.size() <= n)
                                                                                ? parses
                                                                                : parses.subList(0, n)))));
        return Optional.of(ImmutableMap.copyOf(allNBestLists));
    }

}
