package ab.demo.logging;

import ab.server.Proxy;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.varia.LevelRangeFilter;

/**
 * @author adrian
 *         <p>
 *         shamelessely stolen from the https://github.com/Wikidata/QueryAnalysis repo
 */
public final class LoggingHandler {
    /**
     * Since this is a utility class, it should not be instantiated.
     */
    private LoggingHandler() {
        throw new AssertionError("Instantiating utility class LoggingHandler");
    }

    /**
     * Method for initiating a file log with default parameters.
     */
    public static void initFileLog() {
        initFileLog("logs/" + Proxy.getProxyPort() + ".%timestamp.log", Level.ALL);
    }

    /**
     * Defines an appender that writes all log messages of level levelToWrite or higher to the file fileToWrite.
     *
     * @param fileToWrite File to write the log to.
     */
    public static void initFileLog(String fileToWrite, Level levelToWrite) {
        TimestampFileAppender fileAppender = new TimestampFileAppender();
        fileAppender.setName("FileLogger");
        fileAppender.setLayout(new PatternLayout("[%-5p]\t%d\t%c\t%x\t-\t%m%n"));
        fileAppender.setFile(fileToWrite);
        fileAppender.setThreshold(levelToWrite);
        fileAppender.activateOptions();
        Logger.getRootLogger().addAppender(fileAppender);
    }

    /**
     * Defines an appender that writes INFO log messages to the console.
     */
    public static void initConsoleLog() {
        ConsoleAppender consoleAppender = new ConsoleAppender();
        consoleAppender.setName("ConsoleLogger");
        consoleAppender.setLayout(new PatternLayout("[%-5p]\t%d\t%c\t%x\t-\t%m%n"));
        LevelRangeFilter levelRangeFilter = new LevelRangeFilter();
        consoleAppender.addFilter(levelRangeFilter);
        consoleAppender.activateOptions();
        Logger.getRootLogger().addAppender(consoleAppender);
    }
}