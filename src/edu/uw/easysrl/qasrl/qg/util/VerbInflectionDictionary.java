package edu.uw.easysrl.qasrl.qg.util;

import edu.uw.easysrl.corpora.ParallelCorpusReader;
import edu.uw.easysrl.qasrl.util.CountDictionary;
import edu.uw.easysrl.qasrl.util.PropertyUtil;

import java.io.*;
import java.util.*;

public class VerbInflectionDictionary implements Serializable {
	private CountDictionary wordDict;
	private ArrayList<String[]> inflections;
	private int[] inflCount;
	private HashMap<String, ArrayList<Integer>> inflMap;
	
	public VerbInflectionDictionary(CountDictionary wordDict) {
		this.wordDict = wordDict;
		inflections = new ArrayList<>();
		inflMap = new HashMap<>();
	}
	
	public void loadDictionaryFromFile(String filePath) throws IOException {
		BufferedReader reader;
		reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath)));
		String line;
		while ((line = reader.readLine()) != null) {
			if (!line.trim().isEmpty()) {
				String[] strs = line.split("\t");
				String[] infl = new String[5];
				boolean inCorpus = false;
				for (int i = 0; i < 5; i++) {
					infl[i] = strs[i];
					if (!inCorpus && wordDict.contains(infl[i])) {
						inCorpus = true;
					}
				}
				int inflId = inflections.size();
				inflections.add(infl);
				for (int i = 0; i < infl.length; i++) {
					String v = infl[i];
					if (v.equals("_") || v.equals("-")) {
						continue;
					}
					if (!inflMap.containsKey(v)) {
						inflMap.put(v, new ArrayList<>());
					}
					ArrayList<Integer> inflIds = inflMap.get(v);
					if (!inflIds.contains(inflId)) {
						inflIds.add(inflId);
					}
				}
			}
		}
		reader.close();
		countInflections();
	}
	
	public int getBestInflectionId(String verb) {
		ArrayList<Integer> inflIds = inflMap.get(verb);
		if (inflIds == null) {
			return -1;
		}
		int bestId = -1, bestCount = -1;
		for (int i = 0; i < inflIds.size(); i++) {
			int count = inflCount[inflIds.get(i)];
			if (count > bestCount) {
				bestId = inflIds.get(i);
				bestCount = count;
			}
		}
		return bestId;
	}
	
	public String[] getBestInflections(String verb) {
		String verbPrefix = "";
		if (verb.contains("-")) {
			int idx = verb.indexOf('-');
			verbPrefix = verb.substring(0, idx + 1);
			verb = verb.substring(idx + 1);
		}
		ArrayList<Integer> inflIds = inflMap.get(verb);
		if (inflIds == null) {
			return null;
		}
		int bestId = -1, bestCount = -1;
		for (int i = 0; i < inflIds.size(); i++) {
			int count = inflCount[inflIds.get(i)];
			if (count > bestCount) {
				bestId = inflIds.get(i);
				bestCount = count;
			}
		}
		String[] infl = new String[5];
		for (int i = 0; i < 5; i++) {
			infl[i] = verbPrefix + inflections.get(bestId)[i];
		}
		return infl;
	}
	
	public String getBestBaseVerb(String verb) {
		int bestId = getBestInflectionId(verb);
		return bestId < 0 ? verb : inflections.get(bestId)[0];
	}
	
	private void countInflections() {
		inflCount = new int[inflections.size()];
		Arrays.fill(inflCount, 0);
		for (String word : wordDict.getStrings()) {
			int wordCount = wordDict.getCount(word);
			if (inflMap.containsKey(word)) {
				for (int inflId : inflMap.get(word)) {
					inflCount[inflId] += wordCount;
				}
			}
		}
	}

	public static VerbInflectionDictionary buildFromPropBankTraining() {
		CountDictionary wordDict = new CountDictionary();
		Iterator<ParallelCorpusReader.Sentence> sentenceIterator;
		try {
			sentenceIterator = ParallelCorpusReader.READER.readCorpus(false);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		while (sentenceIterator.hasNext()) {
			ParallelCorpusReader.Sentence sentence = sentenceIterator.next();
			sentence.getWords().forEach(wordDict::addString);
		}
		VerbInflectionDictionary inflDict = new VerbInflectionDictionary(wordDict);
		try {
			inflDict.loadDictionaryFromFile(PropertyUtil.resourcesProperties.getProperty("wiktionary_verbs"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Loaded inflections:\t" + inflDict.inflections.size());
		return inflDict;
	}

	public static void main() {
		// Serialize dictionary.
	}

}
