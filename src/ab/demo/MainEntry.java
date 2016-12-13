package ab.demo;

import ab.demo.logging.LoggingHandler;
import ab.planner.abTrajectory;
import ab.server.Proxy;
import ab.utils.GameImageRecorder;
import ab.vision.ShowSeg;
import org.apache.commons.cli.*;
import org.apache.log4j.Level;


import org.apache.commons.cli.*;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;


import java.io.FileNotFoundException;



/*****************************************************************************
 ** ANGRYBIRDS AI AGENT FRAMEWORK
 ** Copyright (c) 2014, XiaoYu (Gary) Ge, Jochen Renz,Stephen Gould,
 **  Sahan Abeyasinghe,Jim Keys,  Andrew Wang, Peng Zhang
 ** All rights reserved.
 **This work is licensed under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 **To view a copy of this license, visit http://www.gnu.org/licenses/
 *****************************************************************************/

public class MainEntry {

    private static Logger logger = Logger.getLogger(MainEntry.class);

    public static void main(String args[]) {

       // args = new String[] {"-s"};
        Options options = new Options();
        options.addOption("s", "standalone", false, "runs the reinforcement learning agent in standalone mode");
        options.addOption("p", "proxyPort", true, "the port which is to be used by the proxy");
        options.addOption("h", "help", false, "displays this help");
        options.addOption("n", "naiveAgent", false, "runs the naive agent in standalone mode");
        options.addOption("c", "competition", false, "runs the naive agent in the server/client competition mode");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;
        Agent agent;

        LoggingHandler.initConsoleLog();

        try {
            cmd = parser.parse(options, args);
            if (cmd.hasOption("help")) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("help", options);
                return;
            }

            int proxyPort = 9000;
            if(cmd.hasOption("proxyPort")) {
                proxyPort = Integer.parseInt(cmd.getOptionValue("proxyPort"));
                logger.info("Set proxy port to " + proxyPort);
            }
            Proxy.setPort(proxyPort);
            LoggingHandler.initFileLog();


            if (cmd.hasOption("standalone")) {
                agent = new ReinforcementLearningAgentStandalone();
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

        } catch (UnrecognizedOptionException e) {
            System.out.println("Unrecognized commandline option: " + e.getOption());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("help", options);
            return;
        }
        catch (ParseException e) {
            System.out.println("There was an error while parsing your command line input. Did you rechecked your syntax before running?");
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("help", options);
            return;
        }

        agent.run();

        /*
        String command = "";
        if (args.length > 0) {
            command = args[0];
            if (args.length == 1 && command.equalsIgnoreCase("-na")) {
                NaiveAgent na = new NaiveAgent();
                na.run();
            } else if (command.equalsIgnoreCase("-cshoot")) {
                ShootingAgent.shoot(args, true);
            } else if (command.equalsIgnoreCase("-pshoot")) {
                ShootingAgent.shoot(args, false);
            } else if (args.length == 1 && command.equalsIgnoreCase("-nasc")) {
                ReinforcementLearningAgentClient na = new ReinforcementLearningAgentClient();
                na.run();
            } else if (args.length == 2 && command.equalsIgnoreCase("-nasc")) {
                ReinforcementLearningAgentClient na = new ReinforcementLearningAgentClient(args[1]);
                na.run();
            } else if (args.length == 3 && command.equalsIgnoreCase("-nasc")) {
                int id = Integer.parseInt(args[2]);
                ReinforcementLearningAgentClient na = new ReinforcementLearningAgentClient(args[1], id);
                na.run();
            } else if (args.length == 2 && command.equalsIgnoreCase("-na")) {
                ReinforcementLearningAgentStandalone na = new ReinforcementLearningAgentStandalone();
                if (!(args[1].equalsIgnoreCase("-showMBR") || args[1].equals("-showReal"))) {
                    int initialLevel = 1;
                    try {
                        initialLevel = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        System.out.println("wrong level number, will use the default one");
                    }
                    na.currentLevel = initialLevel;
                    na.run();
                } else {
                    Thread nathre = new Thread(na);
                    nathre.start();
                    if (args[1].equalsIgnoreCase("-showReal"))
                        ShowSeg.useRealshape = true;
                    Thread thre = new Thread(new ShowSeg());
                    thre.start();
                }
            } else if (args.length == 3 && (args[2].equalsIgnoreCase("-showMBR") || args[2].equalsIgnoreCase("-showReal")) && command.equalsIgnoreCase("-na")) {
                ReinforcementLearningAgentStandalone na = new ReinforcementLearningAgentStandalone();
                int initialLevel = 1;
                try {
                    initialLevel = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    System.out.println("wrong level number, will use the default one");
                }
                na.currentLevel = initialLevel;
                Thread nathre = new Thread(na);
                nathre.start();
                if (args[2].equalsIgnoreCase("-showReal"))
                    ShowSeg.useRealshape = true;
                Thread thre = new Thread(new ShowSeg());
                thre.start();

            } else if (command.equalsIgnoreCase("-showMBR")) {
                ShowSeg showseg = new ShowSeg();
                showseg.run();
            } else if (command.equalsIgnoreCase("-showReal")) {
                ShowSeg showseg = new ShowSeg();
                ShowSeg.useRealshape = true;
                showseg.run();
            } else if (command.equalsIgnoreCase("-showTraj")) {
                String[] param = {};
                abTrajectory.main(param);
            } else if (command.equalsIgnoreCase("-recordImg")) {

                if (args.length < 2)
                    System.out.println("please specify the directory");
                else {
                    String[] param = {args[1]};

                    GameImageRecorder.main(param);
                }
            } else
                System.out.println("Please input the correct command");
        } else
            System.out.println("Please input the correct command");
        */

    }
}
