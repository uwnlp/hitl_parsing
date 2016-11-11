package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.NBestList;
import edu.uw.easysrl.qasrl.reparsing.HITLParser;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.qg.util.Prepositions;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Writes all the annotation on the dev set to a single tsv file.
 * Created by luheng on 5/24/16.
 */
public class AnnotationFileWriter {
    private static final int nBest = 100;
    private static HITLParser myHTILParser;
    private static Map<Integer, List<AlignedAnnotation>> annotations;

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

    public static void main(String[] args) {
        final String[] annotationFiles = {
                "./Crowdflower_data/f893900.csv",
                "./Crowdflower_data/f902142.csv",
                "./Crowdflower_data/f912533.csv",
                "./Crowdflower_data/f912675.csv",
                "./Crowdflower_data/f913098.csv",
        };

        final String outputFilePath = "ccgdev_temp.qa.tsv";
        myHTILParser = new HITLParser(nBest);
        myHTILParser.setQueryPruningParameters(queryPruningParameters);
        annotations = CrowdFlowerDataUtils.loadCrowdflowerAnnotation(annotationFiles);
        assert annotations != null;
        try {
            writeAggregatedAnnotation(outputFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeAggregatedAnnotation(final String outputFilePath) throws IOException {
        List<Integer> sentenceIds = myHTILParser.getAllSentenceIds();
        System.out.println(sentenceIds.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        System.out.println("Queried " + sentenceIds.size() + " sentences. Total number of questions:\t" +
                annotations.entrySet().stream().mapToInt(e -> e.getValue().size()).sum());
        int numMatchedAnnotations = 0;
        // Output file writer
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputFilePath)));
        int counter = 0, numWrittenAnnotations = 0;

        for (int sentenceId : sentenceIds) {
            if (++counter % 100 == 0) {
                System.out.println(String.format("Processed %d sentences ... ", counter));
            }
            final ImmutableList<String> sentence = myHTILParser.getSentence(sentenceId);
            final List<AlignedAnnotation> annotated = annotations.get(sentenceId);
            if (annotated == null || annotated.isEmpty()) {
                continue;
            }
            List<ScoredQuery<QAStructureSurfaceForm>> queryList = new ArrayList<>();
            queryList.addAll(myHTILParser.getNewCoreArgQueriesForSentence(sentenceId));

            for (AlignedAnnotation annotation : annotations.get(sentenceId)) {
                Optional<ScoredQuery<QAStructureSurfaceForm>> queryOpt =
                        AnnotationUtils.getQueryForAlignedAnnotation(annotation, queryList);
                if (!queryOpt.isPresent()) {
                    continue;
                }
                final ScoredQuery<QAStructureSurfaceForm> query = queryOpt.get();
                ImmutableList<ImmutableList<Integer>> responses = AnnotationUtils.getAllUserResponses(query, annotation);
                if (responses.size() != 5) {
                    continue;
                }
                numMatchedAnnotations ++;
                if (Prepositions.prepositionWords.contains(sentence.get(query.getPredicateId().getAsInt())
                        .toLowerCase())) {
                    continue;
                }
                // Write to output file.
                writer.write(query.toAnnotationString(sentence, responses) + "\n");
                numWrittenAnnotations ++;
            }
        }
        System.out.println("Num. matched annotations:\t" + numMatchedAnnotations);
        writer.close();
        System.out.println(String.format("Wrote %d annotations to file %s.", numWrittenAnnotations, outputFilePath));
    }
}
