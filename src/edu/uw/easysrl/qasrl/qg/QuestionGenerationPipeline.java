package edu.uw.easysrl.qasrl.qg;

import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.Parse;
import edu.uw.easysrl.qasrl.reparsing.ResponseSimulator;
import edu.uw.easysrl.qasrl.query.*;

import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import static edu.uw.easysrl.qasrl.util.GuavaCollectors.*;

import java.util.*;
import java.util.stream.IntStream;

public abstract class QuestionGenerationPipeline {
    public static QuestionGenerationPipeline coreArgQGPipeline = new QuestionGenerationPipeline() {
        @Override
        public ImmutableList<QuestionAnswerPair> generateQAPairs(int sentenceId, int parseId, Parse parse) {
            return new ImmutableList.Builder<QuestionAnswerPair>()
                    .addAll(QuestionGenerator.newCoreNPArgQuestions(sentenceId, parseId, parse))
                    .addAll(QuestionGenerator.newCopulaQuestions(sentenceId, parseId, parse))
                    .build();
        }

        private QueryPruningParameters queryPruningParameters = null;

        public QueryPruningParameters getQueryPruningParameters() {
            return queryPruningParameters;
        }

        public QuestionGenerationPipeline setQueryPruningParameters(final QueryPruningParameters pruningParameters) {
            this.queryPruningParameters = pruningParameters;
            return this;
        }

        @Override
        public QAPairAggregator<QAStructureSurfaceForm> getQAPairAggregator() {
            return QAPairAggregators.aggregateForMultipleChoiceQA();
        }

        @Override
        public QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> getQueryGenerator() {
            return QueryGenerators.checkboxQueryGenerator();
        }

        @Override
        public Optional<QueryFilter<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>>> getQueryFilter() {
            return Optional.of(QueryFilters.scoredQueryFilter());
        }

        @Override
        public Optional<ResponseSimulator> getResponseSimulator(ImmutableMap<Integer, Parse> parses) {
            return Optional.empty();
        }
    };

    public abstract ImmutableList<QuestionAnswerPair> generateQAPairs(int sentenceId, int parseId, Parse parse);

    public abstract QAPairAggregator<QAStructureSurfaceForm> getQAPairAggregator();
    public abstract QueryGenerator<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>> getQueryGenerator();
    public Optional<QueryFilter<QAStructureSurfaceForm, ScoredQuery<QAStructureSurfaceForm>>> getQueryFilter() {
        return Optional.empty();
    }

    public QueryPruningParameters getQueryPruningParameters() {
        return new QueryPruningParameters();
    }

    public abstract QuestionGenerationPipeline setQueryPruningParameters(final QueryPruningParameters pruningParameters);

    public ImmutableList<ScoredQuery<QAStructureSurfaceForm>> generateAllQueries(int sentenceId, NBestList nBestList) {
        final ImmutableList<QuestionAnswerPair> allQAPairs = IntStream.range(0, nBestList.getN()).boxed()
            .flatMap(parseId -> this.generateQAPairs(sentenceId, parseId, nBestList.getParse(parseId)).stream())
            .collect(toImmutableList());

        ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries =
            getQueryGenerator().generate(getQAPairAggregator().aggregate(allQAPairs));
        for(ScoredQuery<QAStructureSurfaceForm> query : queries) {
            query.computeScores(nBestList);
        }
        if(getQueryFilter().isPresent()) {
            return getQueryFilter().get().filter(queries, nBestList, getQueryPruningParameters());
        } else {
            return queries;
        }
    }

    public abstract Optional<ResponseSimulator> getResponseSimulator(ImmutableMap<Integer, Parse> parses);
}
