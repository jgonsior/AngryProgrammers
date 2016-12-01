package ab.demo.logging;

import org.apache.log4j.FileAppender;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author adrian
 *         <p>
 *         shamelessely stolen from the https://github.com/Wikidata/QueryAnalysis repo
 */

public class TimestampFileAppender extends FileAppender {

    @Override
    public final void setFile(String file) {
        Date d = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        file.replaceAll("%timestamp", "");
        super.setFile(file.replaceAll("%timestamp", format.format(d)));
    }
}