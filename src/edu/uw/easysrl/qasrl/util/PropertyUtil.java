package edu.uw.easysrl.qasrl.util;

import edu.uw.easysrl.util.Util;

import java.io.File;
import java.util.Properties;

/**
 * Created by luheng on 11/8/16.
 */
public class PropertyUtil {
    public static final Properties corporaProperties = Util.loadProperties(new File("corpora.properties"));
    public static final Properties resourcesProperties = Util.loadProperties(new File("resources.properties"));
}
