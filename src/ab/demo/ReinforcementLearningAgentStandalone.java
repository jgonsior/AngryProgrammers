package ab.demo;

import ab.demo.other.ActionRobot;
import ab.demo.logging.LoggingHandler;
import ab.demo.other.Shot;
import ab.demo.qlearning.QValuesDAO;
import ab.demo.qlearning.ProblemState;
import ab.planner.TrajectoryPlanner;
import ab.utils.StateUtil;
import ab.vision.ABObject;
import ab.vision.GameStateExtractor;
import ab.vision.Vision;
import org.apache.log4j.Logger;
import org.skife.jdbi.v2.DBI;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author jgonsior
 */
public class ReinforcementLearningAgentStandalone implements Runnable, Agent {

    private static Logger logger = Logger.getLogger(ReinforcementLearningAgentClient.class);
    public int currentLevel = 1;
    private TrajectoryPlanner trajectoryPlanner;
    //Wrapper of the communicating messages
    private ActionRobot actionRobot;
    private double discountFactor = 0.9;
    private double learningRate = 0.1;
    private double explorationRate = 0.7;
    private boolean firstShot;
    private Random randomGenerator;
    private QValuesDAO qValuesDAO;
    private String dbUser;
    private String dbPath;
    private String dbPass;

    private Map<Integer, Integer> scores = new LinkedHashMap<Integer, Integer>();

    // a standalone implementation of the Reinforcement Agent
    public ReinforcementLearningAgentStandalone() {
        LoggingHandler.initFileLog();
        LoggingHandler.initConsoleLog();

        actionRobot = new ActionRobot();
        trajectoryPlanner = new TrajectoryPlanner();
        randomGenerator = new Random();
        firstShot = true;

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

            DBI dbi = new DBI(dbPath, dbUser, dbPass);

            qValuesDAO = dbi.open(QValuesDAO.class);

            //@todo: create new command line argument for this parameter
            boolean createDatabaseTables = true;

            if (createDatabaseTables) {
                qValuesDAO.createQValuesTable();
                qValuesDAO.createAllGamesTable();
            }

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
        ActionRobot.GoFromMainMenuToLevelSelection();
    }

    // run the client
    public void run() {
        actionRobot.loadLevel(currentLevel);
        while (true) {
            GameStateExtractor.GameState state = solve();
            if (state == GameStateExtractor.GameState.WON) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int score = StateUtil.getScore(ActionRobot.proxy);
                if (!scores.containsKey(currentLevel))
                    scores.put(currentLevel, score);
                else {
                    if (scores.get(currentLevel) < score)
                        scores.put(currentLevel, score);
                }
                int totalScore = 0;
                for (Integer key : scores.keySet()) {

                    totalScore += scores.get(key);
                    logger.info(" Level " + key
                            + " Score: " + scores.get(key) + " ");
                }
                logger.info("Total Score: " + totalScore);
                actionRobot.loadLevel(++currentLevel);
                // make a new trajectory planner whenever a new level is entered
                trajectoryPlanner = new TrajectoryPlanner();

                // first shot on this level, try high shot first
                firstShot = true;
            } else if (state == GameStateExtractor.GameState.LOST) {
                logger.info("Restart");
                actionRobot.restartLevel();
            } else if (state == GameStateExtractor.GameState.LEVEL_SELECTION) {
                logger.warn("Unexpected level selection page, go to the last current level : "
                                + currentLevel);
                actionRobot.loadLevel(currentLevel);
            } else if (state == GameStateExtractor.GameState.MAIN_MENU) {
                logger.warn("Unexpected main menu page, go to the last current level : "
                                + currentLevel);
                ActionRobot.GoFromMainMenuToLevelSelection();
                actionRobot.loadLevel(currentLevel);
            } else if (state == GameStateExtractor.GameState.EPISODE_MENU) {
                logger.warn("Unexpected episode menu page, go to the last current level : "
                                + currentLevel);
                ActionRobot.GoFromMainMenuToLevelSelection();
                actionRobot.loadLevel(currentLevel);
            }

        }

    }

    public GameStateExtractor.GameState solve() {
        // capture Image
        BufferedImage screenshot = ActionRobot.doScreenShot();
        // process image
        Vision vision = new Vision(screenshot);
        // find the slingshot
        Rectangle sling = vision.findSlingshotMBR();

        // confirm the slingshot
        while (sling == null && actionRobot.getState() == GameStateExtractor.GameState.PLAYING) {
            logger.warn("No slingshot detected. Please remove pop up or zoom out");
            ActionRobot.fullyZoomOut();
            screenshot = ActionRobot.doScreenShot();
            vision = new Vision(screenshot);
            sling = vision.findSlingshotMBR();
            ActionRobot.skipPopUp();
        }
        // get all the pigs
        java.util.List<ABObject> pigs = vision.findPigsMBR();
        GameStateExtractor.GameState state = actionRobot.getState();

        // if there is a sling, then play, otherwise just skip.
        if (sling != null) {

            if (!pigs.isEmpty()) {

                Point releasePoint = null;
                Shot shot = new Shot();
                int dx, dy;

                ProblemState currentState = new ProblemState(vision);
                initProblemState(currentState);

                // get Next best Action
                int nextAction = getNextAction(currentState);
                ABObject obj = currentState.getShootableObjects().get(nextAction);
                Point _tpt = obj.getCenter();
                
                // estimate the trajectory
                ArrayList<Point> pts = trajectoryPlanner.estimateLaunchPoint(sling, _tpt);

                // do a high shot when entering a level to find an accurate velocity
                if (firstShot && pts.size() > 1) {
                    releasePoint = pts.get(1);
                } else if (pts.size() == 1)
                    releasePoint = pts.get(0);
                else if (pts.size() == 2) {
                    // randomly choose between the trajectories, with a 1 in
                    // 6 chance of choosing the high one
                    if (randomGenerator.nextInt(6) == 0)
                        releasePoint = pts.get(1);
                    else
                        releasePoint = pts.get(0);
                } else if (pts.isEmpty()) {
                    logger.info("No release point found for the target");
                    logger.info("Try a shot with 45 degree");
                    releasePoint = trajectoryPlanner.findReleasePoint(sling, Math.PI / 4);
                }

                // Get the reference point
                Point refPoint = trajectoryPlanner.getReferencePoint(sling);


                //Calculate the tapping time according the bird type
                if (releasePoint != null) {
                    double releaseAngle = trajectoryPlanner.getReleaseAngle(sling,
                            releasePoint);
                    logger.info("Release Point: " + releasePoint);
                    logger.info("Release Angle: "
                            + Math.toDegrees(releaseAngle));
                    int tapInterval = 0;
                    switch (actionRobot.getBirdTypeOnSling()) {

                        case RedBird:
                            tapInterval = 0;
                            break;               // start of trajectory
                        case YellowBird:
                            tapInterval = 65 + randomGenerator.nextInt(25);
                            break; // 65-90% of the way
                        case WhiteBird:
                            tapInterval = 70 + randomGenerator.nextInt(20);
                            break; // 70-90% of the way
                        case BlackBird:
                            tapInterval = 70 + randomGenerator.nextInt(20);
                            break; // 70-90% of the way
                        case BlueBird:
                            tapInterval = 65 + randomGenerator.nextInt(20);
                            break; // 65-85% of the way
                        default:
                            tapInterval = 60;
                    }

                    int tapTime = trajectoryPlanner.getTapTime(sling, releasePoint, _tpt, tapInterval);
                    dx = (int) releasePoint.getX() - refPoint.x;
                    dy = (int) releasePoint.getY() - refPoint.y;
                    shot = new Shot(refPoint.x, refPoint.y, dx, dy, 0, tapTime);
                } else {
                    logger.error("No Release Point Found");
                    return state;
                }
                

                // check whether the slingshot is changed. the change of the slingshot indicates a change in the scale.
                
                ActionRobot.fullyZoomOut();
                screenshot = ActionRobot.doScreenShot();
                vision = new Vision(screenshot);
                Rectangle _sling = vision.findSlingshotMBR();
                if (_sling != null) {
                    double scale_diff = Math.pow((sling.width - _sling.width), 2) + Math.pow((sling.height - _sling.height), 2);
                    if (scale_diff < 25) {
                        if (dx < 0) {
                            actionRobot.cshoot(shot);
                            state = actionRobot.getState();
                            double reward = getReward(state);
                            if (state == GameStateExtractor.GameState.PLAYING) {
                                screenshot = ActionRobot.doScreenShot();
                                vision = new Vision(screenshot);
                                java.util.List<Point> traj = vision.findTrajPoints();
                                trajectoryPlanner.adjustTrajectory(traj, sling, releasePoint);
                                firstShot = false;
                                updateQValue(currentState, nextAction, new ProblemState(vision), reward, false);
                            } else if (state == GameStateExtractor.GameState.WON || state == GameStateExtractor.GameState.LOST) {
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

    /**
     * checks if highest q_value is 0.0 which means that we have never been in this state,
     * so we need to initialize all possible actions to 0.0
     * @param s
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

    /**
     * returns reward as highscore difference
     * @param state
     * @return if the game is lost the reward is -1, else it is the highscore of the current level
     */
    private double getReward(GameStateExtractor.GameState state) {
        if (state == GameStateExtractor.GameState.WON) {
            GameStateExtractor gameStateExtractor = new GameStateExtractor();
            BufferedImage scoreScreenshot = actionRobot.doScreenShot();
            return gameStateExtractor.getScoreEndGame(scoreScreenshot);
        } else {
            return -1;
        }
    }

    /**
     * updates q-value in database when new information comes in
     * @param from
     * @param action
     * @param to
     * @param reward
     * @param end true if the current level was finished (could be either won or lost)
     */
    private void updateQValue(ProblemState from, int action, ProblemState to, double reward, boolean end) {
        double oldValue = qValuesDAO.getQValue(from.toString(), action);
        double newValue;
        if (end) {
            newValue = oldValue + learningRate * (reward - oldValue);
        } else {
            //possible error: highest Q value could have been different compared to when the action was selected with qValuesDAO.getBestAction
            newValue = oldValue + learningRate * (reward + discountFactor * qValuesDAO.getHighestQValue(to.toString()) - oldValue);
        }
        qValuesDAO.updateQValue(newValue, from.toString(), action);
        qValuesDAO.saveMove(from.toString(), action, to.toString(), reward);
    }

    /**
     * Returns next action, with explorationrate as probability of taking a random action
     * and else look for the so far best action
     * @param problemState
     * @return
     */
    private int getNextAction(ProblemState problemState) {
        int randomValue = randomGenerator.nextInt(100);
        if (randomValue < explorationRate * 100) {
            logger.info("Picked random action");
            return qValuesDAO.getRandomAction(problemState.toString());
        } else {
            logger.info("Picked currently best available action");
            return qValuesDAO.getBestAction(problemState.toString());
        }
    }
}
