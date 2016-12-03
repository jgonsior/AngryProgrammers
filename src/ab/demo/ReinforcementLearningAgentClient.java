/*****************************************************************************
 ** ANGRYBIRDS AI AGENT FRAMEWORK
 ** Copyright (c) 2014, XiaoYu (Gary) Ge, Stephen Gould, Jochen Renz
 **  Sahan Abeyasinghe,Jim Keys,  Andrew Wang, Peng Zhang
 ** All rights reserved.
 **This work is licensed under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 **To view a copy of this license, visit http://www.gnu.org/licenses/
 *****************************************************************************/
package ab.demo;

import ab.demo.logging.LoggingHandler;
import ab.demo.other.ClientActionRobot;
import ab.demo.other.ClientActionRobotJava;
import ab.demo.qlearning.ProblemState;
import ab.demo.qlearning.QValuesDAO;
import ab.planner.TrajectoryPlanner;
import ab.vision.ABObject;
import ab.vision.GameStateExtractor;
import ab.vision.GameStateExtractor.GameState;
import ab.vision.Vision;
import org.apache.log4j.Logger;
import org.skife.jdbi.v2.DBI;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Server/Client Version of our agent which tries to play Angry Birds while learning using Reinforcement Learning
 */
public class ReinforcementLearningAgentClient implements Runnable {

    private static Logger logger = Logger.getLogger(ReinforcementLearningAgentClient.class);

    private byte currentLevel = -1;
    private int failedCounter = 0;
    private int[] solved;
    private TrajectoryPlanner trajectoryPlanner;
    //Wrapper of the communicating messages
    private ClientActionRobotJava clientActionRobotJava;
    private double discountFactor = 0.9;
    private double learningRate = 0.1;
    private double explorationRate = 0.3;
    private int id = 28888;
    private boolean firstShot;
    private Point prevTarget;
    private Random randomGenerator;
    private QValuesDAO qValuesDAO;
    private String dbUser;
    private String dbPath;
    private String dbPass;
    private String dbName;
    private GameStateExtractor gameStateExtractor;


    /**
     * Constructor using the default IP
     */
    public ReinforcementLearningAgentClient() {
        this("127.0.0.1", 28888);
    }

    /**
     * Constructor with a specified IP
     */
    public ReinforcementLearningAgentClient(String ip) {
        this(ip, 28888);
    }

    public ReinforcementLearningAgentClient(String ip, int id) {
        LoggingHandler.initFileLog();
        LoggingHandler.initConsoleLog();

        clientActionRobotJava = new ClientActionRobotJava(ip);
        trajectoryPlanner = new TrajectoryPlanner();
        randomGenerator = new Random();
        prevTarget = null;
        firstShot = true;
        this.id = id;

        Properties properties = new Properties();
        InputStream configInputStream = null;

        try {
            Class.forName("org.sqlite.JDBC");
            //parse our configuration file
            configInputStream = new FileInputStream("config.properties");

            properties.load(configInputStream);

            dbPath = properties.getProperty("db_path");
            dbUser = properties.getProperty("db_user");
            dbPass = properties.getProperty("db_pass");
            dbName = properties.getProperty("db_name");

            DBI dbi = new DBI(dbPath, dbUser, dbPass);

            qValuesDAO = dbi.open(QValuesDAO.class);


            //@todo: create new command line argument for this parameter
            boolean createDatabaseTables = false;

            if (createDatabaseTables) {
                qValuesDAO.createQValuesTable();
            }

            //ProblemState s = new ProblemState("state1");
            //getNextAction(s);

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
    }

    /**
     * @param args
     * @todo move this method to another place -> it doesn't belong into this class
     */
    public static void main(String args[]) {

        ReinforcementLearningAgentClient reinforcementLearningAgentClient;
        if (args.length > 0) {
            reinforcementLearningAgentClient = new ReinforcementLearningAgentClient(args[0]);
        } else {
            reinforcementLearningAgentClient = new ReinforcementLearningAgentClient();
        }
        reinforcementLearningAgentClient.run();

    }

    private int getNextLevel() {
        int level = 0;
        boolean unsolved = false;

        //all the level have been solved, then get the first unsolved level
        for (int i = 0; i < solved.length; i++) {
            if (solved[i] == 0) {
                unsolved = true;
                level = i + 1;
                if (level <= currentLevel && currentLevel < solved.length) {
                    continue;
                } else {
                    return level;
                }
            }
        }
        if (unsolved) {
            return level;
        }
        level = (currentLevel + 1) % solved.length;
        if (level == 0) {
            level = solved.length;
        }
        return level;
    }

    private void checkMyScore() {
        int[] scores = clientActionRobotJava.checkMyScore();
        logger.info(" My score: ");
        int level = 1;
        for (int i : scores) {
            logger.info(" level " + level + "  " + i);
            if (i > 0) {
                solved[level - 1] = 1;
            }
            level++;
        }
    }

    public void run() {
        byte[] info = clientActionRobotJava.configure(ClientActionRobot.intToByteArray(id));
        solved = new int[info[2]];

        //load the initial level (default 1)
        //Check my score
        checkMyScore();

        currentLevel = (byte) getNextLevel();
        clientActionRobotJava.loadLevel(currentLevel);
        //ar.loadLevel((byte)9);
        GameState state;
        while (true) {

            state = solve();
            //If the level is solved , go to the next level
            if (state == GameState.WON) {

                ///System.out.println(" loading the level " + (currentLevel + 1) );
                checkMyScore();
                currentLevel = (byte) getNextLevel();
                clientActionRobotJava.loadLevel(currentLevel);
                //ar.loadLevel((byte)9);
                //display the global best scores
                int[] scores = clientActionRobotJava.checkScore();
                logger.info("Global best score: ");
                for (int i = 0; i < scores.length; i++) {

                    logger.info(" level " + (i + 1) + ": " + scores[i]);
                }

                // make a new trajectory planner whenever a new level is entered
                trajectoryPlanner = new TrajectoryPlanner();

                // first shot on this level, try high shot first
                firstShot = true;

            } else
                //If lost, then restart the level
                if (state == GameState.LOST) {
                    failedCounter++;
                    if (failedCounter > 3) {
                        failedCounter = 0;
                        currentLevel = (byte) getNextLevel();
                        clientActionRobotJava.loadLevel(currentLevel);

                        //ar.loadLevel((byte)9);
                    } else {
                        logger.info("restart");
                        clientActionRobotJava.restartLevel();
                    }

                } else if (state == GameState.LEVEL_SELECTION) {
                    logger.warn("unexpected level selection page, go to the last current level : "
                            + currentLevel);
                    clientActionRobotJava.loadLevel(currentLevel);
                } else if (state == GameState.MAIN_MENU) {
                    logger.warn("unexpected main menu page, reload the level : "
                            + currentLevel);
                    clientActionRobotJava.loadLevel(currentLevel);
                } else if (state == GameState.EPISODE_MENU) {
                    logger.warn("unexpected episode menu page, reload the level: "
                            + currentLevel);
                    clientActionRobotJava.loadLevel(currentLevel);
                }

        }

    }


    /**
     * Solve a particular level by shooting birds directly to pigs
     *
     * @return GameState: the game state after shots.
     */
    public GameState solve() {

        //gameStateExtractor = new GameStateExtractor();
        // capture Image
        BufferedImage screenshot = clientActionRobotJava.doScreenShot();
        // process image
        Vision vision = new Vision(screenshot);

        ProblemState problemTestState = new ProblemState(vision);
        logger.info(problemTestState);

        Rectangle sling = vision.findSlingshotMBR();

        //If the level is loaded (in PLAYING state)but no slingshot detected, then the agent will request to fully zoom out.
        while (sling == null && clientActionRobotJava.checkState() == GameState.PLAYING) {
            logger.info("no slingshot detected. Please remove pop up or zoom out");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

                e.printStackTrace();
            }
            clientActionRobotJava.fullyZoomOut();
            screenshot = clientActionRobotJava.doScreenShot();
            vision = new Vision(screenshot);
            sling = vision.findSlingshotMBR();
        }


        // get all the pigs
        List<ABObject> pigs = vision.findPigsMBR();

        GameState state = clientActionRobotJava.checkState();
        // if there is a sling, then play, otherwise skip.
        if (sling != null) {


            //If there are pigs, we pick up a pig randomly and shoot it.
            if (!pigs.isEmpty()) {
                Point releasePoint = null;


                //@todo is this right?
                ProblemState currentState = new ProblemState(vision);

                // get Next best Action
                int nextAction = getNextAction(currentState);
                ABObject obj = currentState.getShootableObjects().get(nextAction);
                System.out.println(obj);

                Point _tpt = obj.getCenter();

                prevTarget = new Point(_tpt.x, _tpt.y);

                // estimate the trajectory
                ArrayList<Point> pts = trajectoryPlanner.estimateLaunchPoint(sling, _tpt);

                // do a high shot when entering a level to find an accurate velocity
                if (firstShot && pts.size() > 1) {
                    releasePoint = pts.get(1);
                } else if (pts.size() == 1)
                    releasePoint = pts.get(0);
                else if (pts.size() == 2) {
                    // System.out.println("first shot " + firstShot);
                    // randomly choose between the trajectories, with a 1 in
                    // 6 chance of choosing the high one
                    if (randomGenerator.nextInt(6) == 0)
                        releasePoint = pts.get(1);
                    else
                        releasePoint = pts.get(0);
                }
                Point refPoint = trajectoryPlanner.getReferencePoint(sling);

                // Get the release point from the trajectory prediction module
                int tapTime = 0;
                if (releasePoint != null) {
                    double releaseAngle = trajectoryPlanner.getReleaseAngle(sling,
                            releasePoint);
                    logger.info("Release Point: " + releasePoint);
                    logger.info("Release Angle: "
                            + Math.toDegrees(releaseAngle));
                    int tapInterval = 0;
                    switch (clientActionRobotJava.getBirdTypeOnSling()) {

                        case RedBird:
                            tapInterval = 0;
                            break;               // start of trajectory
                        case YellowBird:
                            tapInterval = 65 + randomGenerator.nextInt(25);
                            break; // 65-90% of the way
                        case WhiteBird:
                            tapInterval = 50 + randomGenerator.nextInt(20);
                            break; // 50-70% of the way
                        case BlackBird:
                            tapInterval = 0;
                            break; // 70-90% of the way
                        case BlueBird:
                            tapInterval = 65 + randomGenerator.nextInt(20);
                            break; // 65-85% of the way
                        default:
                            tapInterval = 60;
                    }

                    tapTime = trajectoryPlanner.getTapTime(sling, releasePoint, _tpt, tapInterval);

                } else {
                    System.err.println("No Release Point Found");
                    return clientActionRobotJava.checkState();
                }


                // check whether the slingshot is changed. the change of the slingshot indicates a change in the scale.
                clientActionRobotJava.fullyZoomOut();
                screenshot = clientActionRobotJava.doScreenShot();
                vision = new Vision(screenshot);
                Rectangle _sling = vision.findSlingshotMBR();
                if (_sling != null) {
                    double scale_diff = Math.pow((sling.width - _sling.width), 2) + Math.pow((sling.height - _sling.height), 2);
                    if (scale_diff < 25) {
                        int dx = (int) releasePoint.getX() - refPoint.x;
                        int dy = (int) releasePoint.getY() - refPoint.y;
                        if (dx < 0) {
                            long timer = System.currentTimeMillis();
                            clientActionRobotJava.shoot(refPoint.x, refPoint.y, dx, dy, 0, tapTime, false);
                            logger.info("It takes " + (System.currentTimeMillis() - timer) + " ms to take a shot");
                            state = clientActionRobotJava.checkState();

                            double reward = getReward(state);
                            if (state == GameState.PLAYING) {
                                screenshot = clientActionRobotJava.doScreenShot();
                                vision = new Vision(screenshot);
                                List<Point> traj = vision.findTrajPoints();
                                trajectoryPlanner.adjustTrajectory(traj, sling, releasePoint);
                                firstShot = false;
                                // get our new "to" State to update old q_value
                                updateQValue(currentState, nextAction, new ProblemState(vision), reward, false);
                            } else if (state == GameState.WON || state == GameState.LOST) {
                                updateQValue(currentState, nextAction, currentState, reward, true);
                            }
                        }
                    } else
                        logger.warn("Scale is changed, can not execute the shot, will re-segement the image");
                } else
                    logger.warn("no sling detected, can not execute the shot, will re-segement the image");

            }
        }
        return state;
    }


    private double getQValue(ProblemState s, int action) {
        return qValuesDAO.getQValue(s.toString(), action);
    }

    /**
     * checks if highest q_value is 0.0 which means that we have never been in this state,
     * so we need to initialize all possible actions to 0.0
     */
    private void initProblemState(ProblemState s) {
        int counter = 0;
        // We have not been in this state then
        if (qValuesDAO.getActionAmount(s.toString()) == 0) {
            for (ABObject obj : s.getShootableObjects()) {
                qValuesDAO.insertNewAction(0.0, s.toString(), counter);
                counter += 1;
            }
        }
    }

    /*
    returns reward as highscore difference
     */
    private double getReward(GameState state) {
        System.out.println(state);
        if (state == GameState.WON) {
            GameStateExtractor gameStateExtractor = new GameStateExtractor();
            System.out.println(state);
            BufferedImage scoreScreenshot = clientActionRobotJava.doScreenShot();
            System.out.println(state);
            double reward = gameStateExtractor.getScoreEndGame(scoreScreenshot);
            System.out.println(reward);
            return reward;
        } else {
            return 0;
        }
    }

    /*
    updates q-value in database when new information comes in
     */
    private void updateQValue(ProblemState from, int action, ProblemState to, double reward, boolean end) {
        double oldValue = getQValue(from, action);
        double newValue;
        if (end) {
            newValue = oldValue + learningRate * (reward - oldValue);

        } else {
            newValue = oldValue + learningRate * (reward + discountFactor * getMaxQValue(to) - oldValue);
        }
        qValuesDAO.updateQValue(newValue, from.toString(), action);
    }

    /*
    looks for highest value
     */
    private double getMaxQValue(ProblemState s) {
        return qValuesDAO.getHighestQValue(s.toString());
    }

    /*
    returns action with maximum q-value for a given state
     */

    private int getBestAction(ProblemState s) {
        return qValuesDAO.getBestAction(s.toString());
    }

    /*
    Returns next action, with explorationrate as probability of taking a random action
     and else look for the so far best action
     */
    private int getNextAction(ProblemState problemState) {
        int randomValue = randomGenerator.nextInt(100);
        if (randomValue < explorationRate * 100) {
            return qValuesDAO.getRandomAction(problemState.toString());
        } else {
            return getBestAction(problemState);
        }
    }

    private double calculateDistance(Point p1, Point p2) {
        return Math.sqrt((double) ((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y)));
    }
}