package edu.uw.easysrl.qasrl.qg.util;

import com.google.common.collect.ImmutableSet;
import edu.uw.easysrl.syntax.grammar.Category;


/**
 * Created by luheng on 4/11/16.
 */
public class Prepositions {
    public static final ImmutableSet<Category> prepositionCategories = ImmutableSet.of(
            Category.valueOf("((S\\NP)\\(S\\NP))/NP"),
            Category.valueOf("(NP\\NP)/NP"),
            Category.valueOf("((S\\NP)\\(S\\NP))/S[dcl]"),
            Category.valueOf("PP\\NP")
    );

    public static final ImmutableSet<String> prepositionWords = ImmutableSet.of(
            "aboard", "about", "above", "across", "afore", "after", "against", "ahead", "along", "alongside", "amid",
            "amidst", "among", "amongst", "around", "as", "aside", "astride", "at", "atop", "before", "behind",
            "below", "beneath", "beside", "besides", "between", "beyond", "by", "despite", "down", "during", "except",
            "for", "from", "given", "in", "inside", "into", "near", "next", "of", "off", "on", "onto", "opposite",
            "out", "outside", "over", "pace", "per", "round", "since", "than", "through", "throughout", "till", "times",
            "to", "toward", "towards", "under", "underneath", "until", "unto", "up", "upon", "versus", "via", "with",
            "within", "without"
    );
}
