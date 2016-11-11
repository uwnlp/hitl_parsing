package edu.uw.easysrl.qasrl.main;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import edu.uw.easysrl.qasrl.*;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.annotation.AnnotationFileLoader;
import edu.uw.easysrl.qasrl.corpora.ParseDataLoader;
import edu.uw.easysrl.qasrl.evaluation.CcgEvaluation;
import edu.uw.easysrl.qasrl.reparsing.ReparsingParameters;
import edu.uw.easysrl.qasrl.reparsing.*;
import edu.uw.easysrl.qasrl.util.PropertyUtil;
import edu.uw.easysrl.syntax.model.Constraint;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import edu.uw.easysrl.syntax.evaluation.Results;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.*;

public class CcgReparsingExperiment {

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.maxNumOptionsPerQuery = 6;
        queryPruningParameters.skipPPQuestions = true;
        queryPruningParameters.skipSAdjQuestions = true;
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = -1;
        queryPruningParameters.minPromptConfidence = 0.1;
    }

    private static ReparsingParameters reparsingParameters;
    private static ExperimentConfig experimentConfig;
    private static ParseData corpus;
    private static Map<Integer, NBestList> nbestLists;
    private static Map<Integer, List<AnnotatedQuery>> annotations;

    private static final String ccgDevNBestFile = PropertyUtil.resourcesProperties.getProperty("ccg_dev_nbest");
    private static final String ccgTestNBestFile = PropertyUtil.resourcesProperties.getProperty("ccg_test_nbest");

    public static void main(String[] args) {
        experimentConfig = new ExperimentConfig(args);
        reparsingParameters = new ReparsingParameters(args);
        System.out.println(reparsingParameters.toString());

        if (experimentConfig.runBioinfer) {
            throw new NotImplementedException();
        } else if (!experimentConfig.runCcgTest) {
            corpus = ParseDataLoader.loadFromDevPool().get();
            nbestLists = NBestList.loadNBestListsFromFile(ccgDevNBestFile, 100).get();
            annotations = AnnotationFileLoader.loadCCGDev();
        } else {
            corpus = ParseDataLoader.loadFromTestPool(true).get();
            nbestLists = NBestList.loadNBestListsFromFile(ccgTestNBestFile, 100).get();
            annotations = AnnotationFileLoader.loadCCGTest();
        }
        final HITLParser parser = new HITLParser(corpus, nbestLists);
        parser.setQueryPruningParameters(queryPruningParameters);
        parser.setReparsingParameters(reparsingParameters);
        // TODO: Supertagger got called multiple times.
        int numChangedSentence = 0;
        Results avgChange = new Results();
        BaseCcgParser.AStarParser baseParser = new BaseCcgParser.AStarParser(BaseCcgParser.modelFolder, 1, /* onebest*/
               1e-6, 1e-6, 1000000, 70);
        baseParser.cacheSupertags(parser.getParseData());
        BaseCcgParser.ConstrainedCcgParser reParser = new BaseCcgParser.ConstrainedCcgParser(BaseCcgParser.modelFolder,
                1 /* one best */);
        reParser.cacheSupertags(parser.getParseData());

        Results avgBaseline = new Results(),
                avgReparsed = new Results(),
                avgUnlabeledBaseline = new Results(),
                avgUnlabeledReparsed = new Results(),
                avgBaselineOnChanged = new Results(),
                avgReparsedOnChanged = new Results();

        int numMatchedAnnotations = 0;
        int sentenceCounter = 0;
        final boolean runDev = !experimentConfig.runCcgTest && !experimentConfig.runBioinfer;

        for  (int sentenceId : parser.getAllSentenceIds()) {
            if (runDev && !nbestLists.containsKey(sentenceId)) {
                continue;
            }
            if (++sentenceCounter % 100 == 0) {
                System.out.println("Parsed " + sentenceCounter + " sentences ...");
            }
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = null;
            if (annotations.containsKey(sentenceId)) {
                queries = runDev ? parser.getPronounCoreArgQueriesForSentence(sentenceId) :
                                    parser.getNewCoreArgQueriesForSentence(sentenceId);
            }
            final Parse goldParse = parser.getGoldParse(sentenceId);
            final Parse baselineParse = baseParser.parse(sentenceId, parser.getInputSentence(sentenceId));
            Preconditions.checkArgument(baselineParse != null);

            final Results baselineF1 = CcgEvaluation.evaluate(baselineParse.dependencies, goldParse.dependencies);
            final Results unlabeledBaselineF1 = CcgEvaluation.evaluateUnlabeled(baselineParse.dependencies, goldParse.dependencies);
            avgBaseline.add(baselineF1);
            avgUnlabeledBaseline.add(unlabeledBaselineF1);
            if (queries == null || queries.isEmpty() || !annotations.containsKey(sentenceId)) {
                avgReparsed.add(baselineF1);
                avgUnlabeledReparsed.add(unlabeledBaselineF1);
                avgChange.add(CcgEvaluation.evaluate(baselineParse.dependencies, baselineParse.dependencies));
                continue;
            }
            final Set<Constraint> allConstraintsForSentence = new HashSet<>();
            for (AnnotatedQuery annotation : annotations.get(sentenceId)) {
                final Optional<ScoredQuery<QAStructureSurfaceForm>> matchQueryOpt =
                        HITLParser.getBestAlignedQuery(annotation, queries);
                if (!matchQueryOpt.isPresent()) {
                    continue;
                }
                final ScoredQuery<QAStructureSurfaceForm> query = matchQueryOpt.get();
                final ImmutableList<ImmutableList<Integer>> matchedResponses = annotation.getResponses(query);
                // Skip queries with more or less than 5 responses.
                if (matchedResponses.stream().filter(r -> r.size() > 0).count() != 5) {
                    continue;
                }
                numMatchedAnnotations ++;
                // Get constraints.
                final ImmutableSet<Constraint> constraints = parser.getConstraints(query, matchedResponses);
                allConstraintsForSentence.addAll(constraints);
            }
            // Skip re-parsing if no constraint is extracted.
            if (allConstraintsForSentence.isEmpty()) {
                avgReparsed.add(baselineF1);
                avgUnlabeledReparsed.add(unlabeledBaselineF1);
            } else {
                Parse reparsed = reParser.parseWithConstraint(sentenceId,
                        corpus.getSentenceInputWords().get(sentenceId),
                        allConstraintsForSentence);
                if (reparsed == null) {
                    System.err.println("Reparsing failed, using baseline.");
                    reparsed = baselineParse;
                }
                Results reparsedF1 = CcgEvaluation.evaluate(reparsed.dependencies, goldParse.dependencies);
                Results unlabeledReparsedF1 = CcgEvaluation.evaluateUnlabeled(reparsed.dependencies,
                        goldParse.dependencies);
                boolean parseChanged = CcgEvaluation.evaluate(reparsed.dependencies, baselineParse.dependencies)
                        .getF1() < 0.999;
                avgReparsed.add(reparsedF1);
                avgUnlabeledReparsed.add(unlabeledReparsedF1);
                if (parseChanged) {
                    numChangedSentence++;
                    avgBaselineOnChanged.add(baselineF1);
                    avgReparsedOnChanged.add(reparsedF1);
                }
            }
        }
        System.out.println("Sentence count:\t" + sentenceCounter);
        System.out.println("Num. matched annotations:\t" + numMatchedAnnotations);
        System.out.println(reparsingParameters.toString());
        System.out.println("Num. changed sentences:\t" + numChangedSentence);
        System.out.println("On changed baseline:\n" + avgBaselineOnChanged);
        System.out.println("On changed reparsed:\n" + avgReparsedOnChanged);
        System.out.println("\nLabeled baseline:\n" + avgBaseline);
        System.out.println("Labeled reparsed:\n" + avgReparsed);
        System.out.println("\nUnlabeled baseline:\n" + avgUnlabeledBaseline);
        System.out.println("Unlabeled reparsed:\n" + avgUnlabeledReparsed);
    }
}
