package edu.uw.easysrl.qasrl.util;

import edu.uw.easysrl.qasrl.qg.util.VerbInflectionDictionary;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

/**
 * Created by luheng on 11/10/16.
 */
public class WiktionaryUtil {
    public static void main(String args[]) {
        try {
            final VerbInflectionDictionary infl = VerbInflectionDictionary.buildFromPropBankTraining();
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream("verb_inflection_dictionary.out"));
            oos.writeObject(infl);
            oos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
