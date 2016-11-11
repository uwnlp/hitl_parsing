package edu.uw.easysrl.qasrl.qg.util;

import edu.uw.easysrl.qasrl.Parse;
import com.google.common.collect.HashBasedTable;

// these objects are totes not thread-safe.
public final class PredicateCache {

    private enum Sentinel {
        INSTANCE;
    }
    private Sentinel sentinel = Sentinel.INSTANCE;
    private HashBasedTable<Integer, Predication.Type, Sentinel> currentlyConstructing = HashBasedTable.create();

    public Predication getPredication(int index, Predication.Type predType) {
        if(preds.contains(index, predType)) {
            return preds.get(index, predType);
        } else {
            if(currentlyConstructing.contains(index, predType)) {
                // System.err.println(String.format("Circular dependency at index %d, pred type %s; bailing out",
                //                                  index, predType.name()));
                return new Gap(predType.getTypicalCategory());
            }
            currentlyConstructing.put(index, predType, sentinel);
            final Predication result;
            switch(predType) {
            case VERB:
                // issues with auxiliaries... :(
                // final ImmutableList<String> words = parse.syntaxTree.getLeaves().stream()
                //     .map(leaf -> leaf.getWord())
                //     .collect(toImmutableList());
                // if(VerbHelper.isAuxiliaryVerb(words.get(index), parse.categories.get(index))) {
                //     int curIndex = index,
                //         lastAuxIndex = index;
                //     while(curIndex < words.size() &&
                //           (VerbHelper.isAuxiliaryVerb(words.get(curIndex), parse.categories.get(curIndex)) ||
                //            VerbHelper.isNegationWord(words.get(curIndex)) ||
                //            parse.categories.get(curIndex).isFunctionInto(Category.ADVERB))) {
                //         if(VerbHelper.isAuxiliaryVerb(words.get(curIndex), parse.categories.get(curIndex))) {
                //             lastAuxIndex = curIndex;
                //         }
                //         curIndex++;
                //     }
                //     final String pos = parse.syntaxTree.getLeaves().get(curIndex).getPos();
                //     if(pos.equals("VB") || pos.equals("VBD") || pos.equals("VBG") || pos.equals("VBN") || pos.equals("VBP") || pos.equals("VBZ")) {
                //         // if we discovered a verb by moving forward from the auxiliary we were at, great! use that instead.
                //         result = Verb.getFromParse(curIndex, this, parse);
                //     } else {
                //         // otherwise fall back to the last auxiliary we saw---this may be what happens when "be" or "do" is the main verb.
                //         result = Verb.getFromParse(lastAuxIndex, this, parse);
                //     }
                // } else {
                //     result = Verb.getFromParse(index, this, parse);
                // }
                result = Verb.getFromParse(index, this, parse);
                break;
            case NOUN: result = Noun.getFromParse(index, parse); break;
            case PREPOSITION: result = Preposition.getFromParse(index, this, parse); break;
            case ADVERB: result = Adverb.getFromParse(index, this, parse); break;
            case CLAUSE: result = Clause.getFromParse(index, this, parse); break;
            default: assert false; result = null;
            }
            currentlyConstructing.remove(index, predType);
            // nulls for debugging purposes
            if(result == null) {
                System.err.println("got null result for predication type " + predType.name());
                Predication gap = new Gap(predType.getTypicalCategory());
                preds.put(index, predType, gap);
                return gap;
            } else {
                preds.put(index, predType, result);
                return result;
            }
        }
    }

    public PredicateCache(Parse parse) {
        this.parse = parse;
        this.preds = HashBasedTable.create();
    }

    private final Parse parse;
    private final HashBasedTable<Integer, Predication.Type, Predication> preds;
}
