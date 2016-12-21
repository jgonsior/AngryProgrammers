package ab.demo;

import ab.demo.logging.LoggingHandler;
import ab.demo.qlearning.QValuesDAO;
import ab.server.Proxy;
import org.apache.commons.cli.*;
import org.apache.log4j.Logger;
import org.skife.jdbi.v2.DBI;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


/*****************************************************************************
 ** ANGRYBIRDS AI AGENT FRAMEWORK
 ** Copyright (c) 2014, XiaoYu (Gary) Ge, Jochen Renz,Stephen Gould,
 **  Sahan Abeyasinghe,Jim Keys,  Andrew Wang, Peng Zhang
 ** All rights reserved.
 **This work is licensed under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 **To view a copy of this license, visit http://www.gnu.org/licenses/
 *****************************************************************************/

public class MainEntry {

    private static final Logger logger = Logger.getLogger(MainEntry.class);

    public static void main(String args[]) {

        LoggingHandler.initConsoleLog();

        args = new String[]{"-su"};
        Options options = new Options();
        options.addOption("s", "standalone", false, "runs the reinforcement learning agent in standalone mode");
        options.addOption("p", "proxyPort", true, "the port which is to be used by the proxy");
        options.addOption("h", "help", false, "displays this help");
        options.addOption("n", "naiveAgent", false, "runs the naive agent in standalone mode");
        options.addOption("c", "competition", false, "runs the naive agent in the server/client competition mode");
        options.addOption("u", "updateDatabaseTables", false, "runs CREATE TABLE IF NOT EXIST commands");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        Agent agent;

        Properties properties = new Properties();
        InputStream configInputStream = null;

        try {
            Class.forName("org.sqlite.JDBC");
            //parse configuration file
            configInputStream = new FileInputStream("config.properties");

            properties.load(configInputStream);


        } catch (IOException exception) {
            exception.printStackTrace();
        } catch (ClassNotFoundException exception) {
            exception.printStackTrace();
        } finally {
            if (configInputStream != null) {
                try {
                    configInputStream.close();
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
        }

        String dbPath = properties.getProperty("db_path");
        String dbUser = properties.getProperty("db_user");
        String dbPass = properties.getProperty("db_pass");
        DBI dbi = new DBI(dbPath, dbUser, dbPass);

        QValuesDAO qValuesDAO = dbi.open(QValuesDAO.class);

        try {
            cmd = parser.parse(options, args);
            if (cmd.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("help", options);
                return;
            }

            int proxyPort = 9000;
            if (cmd.hasOption("proxyPort")) {
                proxyPort = Integer.parseInt(cmd.getOptionValue("proxyPort"));
                logger.info("Set proxy port to " + proxyPort);
            }
            Proxy.setPort(proxyPort);

            LoggingHandler.initFileLog();

            if (cmd.hasOption("standalone")) {
                agent = new ReinforcementLearningAgentStandalone(qValuesDAO);
            } else if (cmd.hasOption("naiveAgent")) {
                agent = new NaiveAgent();
            } else if (cmd.hasOption("competition")) {
                agent = new ReinforcementLearningAgentClient();
            } else {
                System.out.println("Please specify which agent we should be running.");
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("help", options);
                return;
            }

            if (cmd.hasOption("updateDatabaseTables")) {
                qValuesDAO.createQValuesTable();
                qValuesDAO.createAllGamesTable();
                qValuesDAO.createAllMovesTable();
            }

        } catch (UnrecognizedOptionException e) {
            System.out.println("Unrecognized commandline option: " + e.getOption());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("help", options);
            return;
        } catch (ParseException e) {
            System.out.println("There was an error while parsing your command line input. Did you rechecked your syntax before running?");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("help", options);
            return;
        }

        agent.run();
    }
}
