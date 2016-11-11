package edu.uw.easysrl.qasrl.annotation;

import com.google.common.collect.ImmutableList;
import edu.uw.easysrl.qasrl.annotation.AnnotatedQuery;
import edu.uw.easysrl.qasrl.util.GuavaCollectors;
import edu.uw.easysrl.qasrl.util.PropertyUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * Load .tsv file from ccg.qa.
 * Created by luheng on 5/26/16.
 */
public class AnnotationFileLoader {
    private static final String devAnnotationFilePath = PropertyUtil.resourcesProperties.getProperty("ccg_dev_qa");
    private static final String testAnnotationFilePath = PropertyUtil.resourcesProperties.getProperty("ccg_test_qa");
    private static final String bioinferAnnotationFilePath = PropertyUtil.resourcesProperties.getProperty("bioinfer_qa");

    public static Map<Integer, List<AnnotatedQuery>> loadCCGDev() {
        return loadFromFile(devAnnotationFilePath);
    }

    public static Map<Integer, List<AnnotatedQuery>> loadCCGTest() {
        return loadFromFile(testAnnotationFilePath);
    }

    public static Map<Integer, List<AnnotatedQuery>> loadBioinfer() {
        return loadFromFile(bioinferAnnotationFilePath);
    }

    // TODO: It doesn't seem to use the sentence words anyways.
    private static Map<Integer, List<AnnotatedQuery>> loadFromFile(final String annotationFilePath) {
        Map<Integer, List<AnnotatedQuery>> annotations = new HashMap<>();
        int numAnnotationRecords = 0;
        try {
            BufferedReader reader = new BufferedReader(new FileReader(new File(annotationFilePath)));
            String line;
            AnnotatedQuery curr;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                String[] info = line.split("\\t");

                if (info.length > 0 && info[0].startsWith("SID=")) {
                    int sentId = Integer.parseInt(info[0].split("=")[1]);
                    if (!annotations.containsKey(sentId)) {
                        annotations.put(sentId, new ArrayList<>());
                    }
                    annotations.get(sentId).add(new AnnotatedQuery());
                    curr = annotations.get(sentId).get(annotations.get(sentId).size() - 1);
                    curr.sentenceId = sentId;
                    //curr.sentenceString = info[1];

                    line = reader.readLine().trim();
                    info = line.split("\\t");
                    curr.questionString = info[3];
                    final String qkey = info[4];
                    curr.predicateId = Integer.parseInt(qkey.split(":")[0]);

                    Map<Character, Set<Integer>> responses = new HashMap<>();
                    List<String> optionStrings = new ArrayList<>();
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) {
                            break;
                        }
                        info = line.trim().split("\\t");
                        for (char c : info[1].trim().toCharArray()) {
                            if (!responses.containsKey(c)) {
                                responses.put(c, new HashSet<>());
                            }
                            responses.get(c).add(optionStrings.size());
                        }
                        optionStrings.add(info[3]);
                    }
                    curr.optionStrings = ImmutableList.copyOf(optionStrings);
                    curr.responses = responses.values().stream()
                            .map(r -> r.stream().sorted().collect(GuavaCollectors.toImmutableList()))
                            .collect(GuavaCollectors.toImmutableList());
                    numAnnotationRecords ++;
                }
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(String.format("Loaded %d annotation records, covering %d sentences from file: %s.",
                numAnnotationRecords, annotations.size(), annotationFilePath));
        return annotations;
    }

    public static void main(String[] args) {
        final Map<Integer, List<AnnotatedQuery>> annotations = loadCCGDev();
        annotations.values().forEach(annot -> annot.forEach(System.out::println));
    }
}
