package ab.demo;

import ab.demo.DAO.GamesDAO;
import ab.demo.DAO.MovesDAO;
import ab.demo.DAO.ProblemStatesDAO;
import ab.demo.DAO.QValuesDAO;
import ab.demo.agents.ManualGamePlayAgent;
import ab.demo.agents.NaiveStandaloneAgent;
import ab.demo.agents.ReinforcementLearningAgent;
import ab.demo.agents.StandaloneAgent;
import ab.demo.logging.LoggingHandler;
import ab.server.Proxy;
import ab.vision.ShowSeg;
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

        args = new String[]{"-mu"};
        Options options = new Options();
        options.addOption("s", "standalone", false, "runs the reinforcement learning agent in standalone mode");
        options.addOption("p", "proxyPort", true, "the port which is to be used by the proxy");
        options.addOption("h", "help", false, "displays this help");
        options.addOption("n", "naiveAgent", false, "runs the naive agent in standalone mode");
        options.addOption("c", "competition", false, "runs the naive agent in the server/client competition mode");
        options.addOption("u", "updateDatabaseTables", false, "executes CREATE TABLE IF NOT EXIST commands");
        options.addOption("l", "level", true, "if set the agent is playing only in this one level");
        options.addOption("m", "manual", false, "runs the empirical threshold determination agent in standalone mode");
        options.addOption("r", "real", false, "shows the recognized shapes in a new frame");


        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        StandaloneAgent agent;

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
        GamesDAO gamesDAO = dbi.open(GamesDAO.class);
        MovesDAO movesDAO = dbi.open(MovesDAO.class);
        ProblemStatesDAO stateIdDAO = dbi.open(ProblemStatesDAO.class);
        ProblemStatesDAO problemStatesDAO = dbi.open(ProblemStatesDAO.class);

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
                agent = new ReinforcementLearningAgent(gamesDAO, movesDAO, problemStatesDAO, qValuesDAO);
            } else if (cmd.hasOption("naiveAgent")) {
                agent = new NaiveStandaloneAgent();
            } else if (cmd.hasOption("manual")) {
                agent = new ManualGamePlayAgent(gamesDAO, movesDAO, problemStatesDAO);
            } else if (cmd.hasOption("competition")) {
                System.out.println("We haven't implemented a competition ready agent yet.");
                return;
            } else {
                System.out.println("Please specify which solving strategy we should be using.");
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("help", options);
                return;
            }

            if (cmd.hasOption("updateDatabaseTables")) {
                qValuesDAO.createTable();
                gamesDAO.createTable();
                movesDAO.createTable();
                stateIdDAO.createTable();
                problemStatesDAO.createTable();
            }

            if (cmd.hasOption("level")) {
                agent.setFixedLevel(Integer.parseInt(cmd.getOptionValue("level")));
            }

            if (cmd.hasOption("real")) {
                ShowSeg.useRealshape = true;
                Thread thread = new Thread(new ShowSeg());
                thread.start();
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