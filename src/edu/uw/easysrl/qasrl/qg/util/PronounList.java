package edu.uw.easysrl.qasrl.qg.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Found by google search ...
 * Created by luheng on 3/7/16.
 */
public class PronounList {
    public static Set<String> englishPronounSet = new HashSet<>();
    public static Set<String> nonPossessivePronouns = new HashSet<>();
    static {
        Collections.addAll(englishPronounSet,
                "all","another","any","anybody","anyone","anything",
                "both","each","each other","either","everybody","everyone",
                "everything","few","he","her","hers","herself",
                "him","himself","his","i","it","its",
                "itself","little","many","me","mine","more",
                "most","much","my","myself","neither","no one",
                "nobody","none","nothing","one","one another","other",
                "others","our","ours","ourselves","several","she",
                "some","somebody","someone","something","that","their",
                "theirs","them","themselves","these","they","this",
                "those","us","we","what","whatever","which",
                "whichever","who","whoever","whom","whomever","whose",
                "you","your","yours","yourself","yourselves");

        Collections.addAll(nonPossessivePronouns,
                "all","another","any","anybody","anyone","anything",
                "both","each","each other","either","everybody","everyone",
                "everything","few","he","hers","herself",
                "him","himself","i","it",
                "itself","little","many","me","more",
                "most","much","myself","neither","no one",
                "nobody","none","nothing","one","one another","other",
                "others","ours","ourselves","several","she",
                "some","somebody","someone","something","that",
                "theirs","them","themselves","these","they","this",
                "those","us","we","what","whatever","which",
                "whichever","who","whoever","whom","whomever","whose",
                "you","yours","yourself","yourselves");
    }
}