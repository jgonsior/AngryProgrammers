/*****************************************************************************
 ** ANGRYBIRDS AI AGENT FRAMEWORK
 ** Copyright (c) 2014, XiaoYu (Gary) Ge, Stephen Gould, Jochen Renz
 **  Sahan Abeyasinghe,Jim Keys,  Andrew Wang, Peng Zhang
 ** All rights reserved.
 **This work is licensed under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 **To view a copy of this license, visit http://www.gnu.org/licenses/
 *****************************************************************************/
package ab.demo;

import ab.demo.other.ClientActionRobot;
import ab.demo.other.ClientActionRobotJava;
import ab.demo.qlearning.QValuesDAO;
import ab.demo.qlearning.State;
import ab.planner.TrajectoryPlanner;
import ab.vision.ABObject;
import ab.vision.GameStateExtractor;
import ab.vision.GameStateExtractor.GameState;
import ab.vision.Vision;
import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.Query;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.List;

/**
 * Server/Client Version of our agent which tries to play Angry Birds while learning using Reinforcement Learning
 */
public class ReinforcementLearningAgent implements Runnable {


    private byte currentLevel = -1;
    private int failedCounter = 0;
    private int[] solved;
    private TrajectoryPlanner trajectoryPlanner;
    //Wrapper of the communicating messages
    private ClientActionRobotJava clientActionRobotJava;
    private double discountFactor = 0.9;
    private double learningRate = 0.1;
    private double explorationRate = 0.1;
    private int id = 28888;
    private boolean firstShot;
    private Point prevTarget;
    private Random randomGenerator;
    private GameStateExtractor gameStateExtractor;
    private QValuesDAO qValuesDAO;
    private String dbUser;
    private String dbPath;
    private String dbPass;
    private String dbName;


    /**
     * Constructor using the default IP
     */
    public ReinforcementLearningAgent() {
        this("127.0.0.1");
    }

    /**
     * Constructor with a specified IP
     */
    public ReinforcementLearningAgent(String ip) {
        this(ip, 0);
    }

    public ReinforcementLearningAgent(String ip, int id) {
        clientActionRobotJava = new ClientActionRobotJava(ip);
        trajectoryPlanner = new TrajectoryPlanner();
        randomGenerator = new Random();
        prevTarget = null;
        firstShot = true;
        this.id = id;

        Properties properties = new Properties();
        InputStream input = null;

        try {
            //parse our configuration file
            input = new FileInputStream("config.properties");

            properties.load(input);

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

            State s = new State("state1");
            getNextAction(s);

        } catch (IOException exception) {
            exception.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
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

        ReinforcementLearningAgent reinforcementLearningAgent;
        if (args.length > 0) {
            reinforcementLearningAgent = new ReinforcementLearningAgent(args[0]);
        } else {
            reinforcementLearningAgent = new ReinforcementLearningAgent();
        }
        reinforcementLearningAgent.run();

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
        System.out.println(" My score: ");
        int level = 1;
        for (int i : scores) {
            System.out.println(" level " + level + "  " + i);
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
        //clientActionRobotJava.loadLevel((byte)9);
        GameState state;
        while (true) {

            state = solve();
            //If the level is solved , go to the next level
            if (state == GameState.WON) {

                ///System.out.println(" loading the level " + (currentLevel + 1) );
                checkMyScore();
                System.out.println();

                currentLevel = (byte) getNextLevel();
                clientActionRobotJava.loadLevel(currentLevel);
                //clientActionRobotJava.loadLevel((byte)9);
                //display the global best scores
                int[] scores = clientActionRobotJava.checkScore();
                System.out.println("Global best score: ");
                for (int i = 0; i < scores.length; i++) {

                    System.out.print(" level " + (i + 1) + ": " + scores[i]);
                }
                System.out.println();

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

                        //clientActionRobotJava.loadLevel((byte)9);
                    } else {
                        System.out.println("restart");
                        clientActionRobotJava.restartLevel();
                    }

                } else if (state == GameState.LEVEL_SELECTION) {
                    System.out.println("unexpected level selection page, go to the last current level : "
                            + currentLevel);
                    clientActionRobotJava.loadLevel(currentLevel);
                } else if (state == GameState.MAIN_MENU) {
                    System.out
                            .println("unexpected main menu page, reload the level : "
                                    + currentLevel);
                    clientActionRobotJava.loadLevel(currentLevel);
                } else if (state == GameState.EPISODE_MENU) {
                    System.out.println("unexpected episode menu page, reload the level: "
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
    private GameState solve() {

        // capture Image
        BufferedImage screenshot = clientActionRobotJava.doScreenShot();

        // GameStateExtractor for currentScore inGame
        gameStateExtractor = new GameStateExtractor();

        // process images
        Vision vision = new Vision(screenshot);

        Rectangle sling = vision.findSlingshotMBR();

        //If the level is loaded (in PLAYING　state)but no slingshot detected, then the agent will request to fully zoom out.
        while (sling == null && clientActionRobotJava.checkState() == GameState.PLAYING) {
            System.out.println("no slingshot detected. Please remove pop up or zoom out");

            try {
                //@todo 50, 500, 5000?!
                Thread.sleep(50);
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
                // random pick up a pig
                ABObject pig = pigs.get(randomGenerator.nextInt(pigs.size()));

                Point _tpt = pig.getCenter();

                // if the target is very close to before, randomly choose a
                // point near it
                if (prevTarget != null && calculateDistance(prevTarget, _tpt) < 10) {
                    double _angle = randomGenerator.nextDouble() * Math.PI * 2;
                    _tpt.x = _tpt.x + (int) (Math.cos(_angle) * 10);
                    _tpt.y = _tpt.y + (int) (Math.sin(_angle) * 10);
                    System.out.println("Randomly changing to " + _tpt);
                }

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
                    System.out.println("Release Point: " + releasePoint);
                    System.out.println("Release Angle: "
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
                            System.out.println(getReward());
                            System.out.println("It takes " + (System.currentTimeMillis() - timer) + " ms to take a shot");
                            state = clientActionRobotJava.checkState();
                            if (state == GameState.PLAYING) {
                                screenshot = clientActionRobotJava.doScreenShot();
                                vision = new Vision(screenshot);
                                List<Point> traj = vision.findTrajPoints();
                                trajectoryPlanner.adjustTrajectory(traj, sling, releasePoint);
                                firstShot = false;
                            }
                        }
                    } else
                        System.out.println("Scale is changed, can not execute the shot, will re-segement the image");
                } else
                    System.out.println("no sling detected, can not execute the shot, will re-segement the image");

            }
        }
        return state;
    }

    private double getQValue(State s, String action) {
        return qValuesDAO.getQValue(s.toString(),action);
    }

    /*
    returns reward as highscore difference
     */
    private double getReward() {
        BufferedImage scoreScreenshot = clientActionRobotJava.doScreenShot();
        double reward = gameStateExtractor.getScoreEndGame(scoreScreenshot);
        //sometimes not correct so he interprets that he got 0 or 1
        if (reward < 10) {
            return 0.0;
        }
        return reward;
    }

    /*
    updates q-value in database when new information comes in
     */
    private void updateQValue(State from, String action, State to) {
        double oldValue = getQValue(from, action);
        double newValue = oldValue + learningRate * (getReward() + discountFactor * getMaxQValue(to) - oldValue);
        qValuesDAO.updateQValue(newValue, from.toString(), action);
    }

    /*
    looks for best action,value pair with highest value
     */
    private double getMaxQValue(State s) { return qValuesDAO.getHighestQValue(s.toString()); }

    /*
    returns action with maximum q-value for a given state
     */
    private String getBestAction(State s) { return qValuesDAO.getBestAction(s.toString()); }


    /*
    Returns next action, with explorationrate as probability of taking a random action
     and else look for the so far best action
     */
    private String getNextAction(State s) {
        int randomValue = randomGenerator.nextInt(100);
        if (randomValue < explorationRate * 100) {
            return qValuesDAO.getRandomAction(s.toString());
        } else {
            return getBestAction(s);
        }
    }

    private double calculateDistance(Point p1, Point p2) {
        return Math.sqrt((double) ((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y)));
    }
}