package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.reparsing.ReparsingHistory;
import edu.uw.easysrl.qasrl.reparsing.HITLParser;
import edu.uw.easysrl.qasrl.qg.surfaceform.QAStructureSurfaceForm;
import edu.uw.easysrl.qasrl.query.QueryPruningParameters;
import edu.uw.easysrl.qasrl.query.ScoredQuery;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Create Crowdflower data for pronoun-style core questions.
 *
 * 1. Sample sentences (or reuse previous sentences)
 * 2. Sample test sentences or (reuse previous sentences)
 * 3. Write candidate questions to csv.
 * 4. Write candidate test questions to csv.
 * Created by luheng on 3/25/16.
 */
public class CrowdFlowerDataWriterNewCore {
    static final int nBest = 100;
    static final int maxNumQueriesPerFile = 500;
    static final int numSentences = 2000;
    static final int randomSeed = 12345;

    private final static HITLParser hitlParser = new HITLParser(nBest);
    private final static ReparsingHistory history = new ReparsingHistory(hitlParser);

    private static final String csvOutputFilePrefix =
            "./Crowdflower_temp/pronoun_core_r6_100best";

    private static final String outputSentenceIdsFile =
            "./Crowdflower_temp/pronoun_core_r6_100best.sent_ids.txt";

    private static final String[] reviewedTestQuestionFiles = new String[] {
            "./Crowdflower_unannotated/test_questions/test_question_core_pronoun_r04.tsv",
            "./Crowdflower_unannotated/test_questions/auto_test_questions_r345.tsv",
    };

    private static QueryPruningParameters queryPruningParameters;
    static {
        queryPruningParameters = new QueryPruningParameters();
        queryPruningParameters.skipPPQuestions = true;
        queryPruningParameters.skipSAdjQuestions = true;  // R5: false // R4: true.
        queryPruningParameters.minOptionConfidence = 0.05;
        queryPruningParameters.minOptionEntropy = -1;   // R4: unspecified.
        queryPruningParameters.minPromptConfidence = 0.1;
    }

    /*
    private static HITLParsingParameters reparsingParameters;
    static {
        reparsingParameters = new HITLParsingParameters();
        reparsingParameters.attachmentPenaltyWeight = 5.0;
        reparsingParameters.supertagPenaltyWeight = 5.0;
    }*/

    private static void printQuestionsToAnnotate() throws IOException {
        final ImmutableList<Integer> sentenceIds =  hitlParser.getAllSentenceIds();
        AtomicInteger lineCounter = new AtomicInteger(0);
        AtomicInteger fileCounter = new AtomicInteger(0);
        System.out.println("Num. sentences:\t" + sentenceIds.size());
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outputSentenceIdsFile)));
        for (int id : sentenceIds) {
            writer.write(id + "\n");
        }
        writer.close();
        hitlParser.setQueryPruningParameters(queryPruningParameters);
        //hitlParser.setReparsingParameters(reparsingParameters);
        CSVPrinter csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter.getAndAdd(1)))),
                CSVFormat.EXCEL.withRecordSeparator("\n"));
        csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeaderNew);
        for (int sid : sentenceIds) {
            ImmutableList<ScoredQuery<QAStructureSurfaceForm>> queries = hitlParser.getNewCoreArgQueriesForSentence(sid);
            history.addSentence(sid);
            for (ScoredQuery<QAStructureSurfaceForm> query : queries) {
                final ImmutableList<String> sentence = hitlParser.getSentence(sid);
                final ImmutableList<Integer> goldOptionIds = hitlParser.getGoldOptions(query);
                CrowdFlowerDataUtils.printQueryToCSVFile(
                        query,
                        sentence,
                        null, // gold options
                        lineCounter.getAndAdd(1),
                        true, // highlight predicate
                        "",
                        csvPrinter);
               // history.addEntry(sid, query, goldOptionIds, hitlParser.getConstraints(query, goldOptionIds));
                history.printLatestHistory();
                if (lineCounter.get() % maxNumQueriesPerFile == 0) {
                    csvPrinter.close();
                    csvPrinter = new CSVPrinter(new BufferedWriter(new FileWriter(
                            String.format("%s_%03d.csv", csvOutputFilePrefix, fileCounter.getAndAdd(1)))),
                            CSVFormat.EXCEL.withRecordSeparator("\n"));
                    csvPrinter.printRecord((Object[]) CrowdFlowerDataUtils.csvHeaderNew);
                }
            }
        }
        csvPrinter.close();
        history.printSummary();
    }

    public static void main(String[] args) throws IOException {
        printQuestionsToAnnotate();
    }
}
