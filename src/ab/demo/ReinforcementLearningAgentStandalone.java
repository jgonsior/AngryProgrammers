package ab.demo;

import ab.demo.logging.LoggingHandler;
import ab.demo.other.ActionRobot;
import ab.demo.other.Shot;
import ab.demo.qlearning.ProblemState;
import ab.demo.qlearning.QValuesDAO;
import ab.planner.TrajectoryPlanner;
import ab.server.Proxy;
import ab.utils.StateUtil;
import ab.vision.ABObject;
import ab.vision.GameStateExtractor;
import ab.vision.Vision;
import org.apache.log4j.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

/**
 * @author jgonsior
 */
public class ReinforcementLearningAgentStandalone implements Runnable, Agent {

    private static Logger logger = Logger.getLogger(ReinforcementLearningAgentStandalone.class);

    private ActionRobot actionRobot;

    private double discountFactor = 0.9;
    private double learningRate = 0.1;
    private double explorationRate = 0.7;

    private boolean firstShot;

    private Random randomGenerator;

    private QValuesDAO qValuesDAO;

    private boolean lowTrajectory = false;

    private Map<Integer, Integer> scores = new LinkedHashMap<Integer, Integer>();

    // a standalone implementation of the Reinforcement Agent
    public ReinforcementLearningAgentStandalone(QValuesDAO qValuesDAO) {
        LoggingHandler.initFileLog();
        LoggingHandler.initConsoleLog();

        this.actionRobot = new ActionRobot();
        this.randomGenerator = new Random();
        this.firstShot = true;

        this.qValuesDAO = qValuesDAO;

        ActionRobot.GoFromMainMenuToLevelSelection();
    }

    public void run() {
        int currentLevel = 1;
        actionRobot.loadLevel(currentLevel);
        TrajectoryPlanner trajectoryPlanner = new TrajectoryPlanner();
        int moveCounter = 0;
        int score;

        // id which will be generated randomly every lvl that we can connect moves to one game
        int gameId = qValuesDAO.saveGame(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);

        //play until the server crashes…
        while (true) {
            GameStateExtractor.GameState state = this.solve(trajectoryPlanner);
            moveCounter++;

            if (state == GameStateExtractor.GameState.WON) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    logger.error("Sleep thread was being interrupted" + e);
                }

                score = StateUtil.getScore(ActionRobot.proxy);

                // update the local list of highscores
                if (!scores.containsKey(currentLevel)) {
                    scores.put(currentLevel, score);
                } else {
                    if (scores.get(currentLevel) < score)
                        scores.put(currentLevel, score);
                }

                this.logScores();

                currentLevel++;
                actionRobot.loadLevel(currentLevel); //actually currentLevel is now the next level because we have just won the current one
                // make a new trajectory planner whenever a new level is entered because of reasons
                trajectoryPlanner = new TrajectoryPlanner();

                // first shot on this level, try high shot first
                firstShot = true;

                gameId = qValuesDAO.saveGame(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);
                moveCounter = 0;
            } else if (state == GameStateExtractor.GameState.LOST) {
                logger.info("Restart level");
                actionRobot.restartLevel();
                gameId = qValuesDAO.saveGame(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);
                moveCounter = 0;
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

    /**
     * logs the current higscores for each level
     */
    private void logScores() {
        int totalScore = 0;

        for (Integer key : scores.keySet()) {
            totalScore += scores.get(key);
            logger.info(" Level " + key
                    + " Score: " + scores.get(key) + " ");
        }
        logger.info("Total Score: " + totalScore);
    }

    public GameStateExtractor.GameState solve(TrajectoryPlanner trajectoryPlanner) {
        BufferedImage screenshot = ActionRobot.doScreenShot();
        Vision vision = new Vision(screenshot);

        Rectangle slingshot = this.findSlingshot(vision, screenshot);

        // get all the pigs
        List<ABObject> pigs = vision.findPigsMBR();

        GameStateExtractor.GameState state = actionRobot.getState();

        if (state != GameStateExtractor.GameState.PLAYING) {
            logger.warn("Accidentally in solving method without being in PLAYING state");
            return state;
        }


        if (!pigs.isEmpty()) {

            Point releasePoint = null;
            Shot shot;
            int dx, dy;

            ProblemState currentState = new ProblemState(vision);
            initProblemState(currentState);

            // get Next best Action
            ActionPair nextActionPair = getNextAction(currentState);
            int nextAction = nextActionPair.value;

            List<ABObject> shootableObjects = currentState.getShootableObjects();


            //@todo should be removed and it needs to be investigated why nextAction returns sometimes wrong actions!
            if (shootableObjects.size() - 1 > nextAction) {
                nextAction = shootableObjects.size() - 1;
            }

            ABObject targetObject = shootableObjects.get(nextAction);
            Point targetPoint = targetObject.getCenter();

            // estimate the trajectory
            ArrayList<Point> estimateLaunchPoints = trajectoryPlanner.estimateLaunchPoint(slingshot, targetPoint);

            // do a high shot when entering a level to find an accurate velocity
            if (firstShot && estimateLaunchPoints.size() > 1) {
                lowTrajectory = false;
                releasePoint = estimateLaunchPoints.get(1);
            } else if (estimateLaunchPoints.size() == 1) {
                // TODO: find out if low or high trajectory????
                releasePoint = estimateLaunchPoints.get(0);
            } else if (estimateLaunchPoints.size() == 2) {
                // randomly choose between the trajectories, with a 1 in
                // 6 chance of choosing the high one
                if (randomGenerator.nextInt(6) == 0) {
                    lowTrajectory = false;
                    releasePoint = estimateLaunchPoints.get(1);
                } else {
                    lowTrajectory = true;
                    releasePoint = estimateLaunchPoints.get(0);
                }
            } else if (estimateLaunchPoints.isEmpty()) {
                logger.info("No release point found for the target");
                logger.info("Try a shot with 45 degree");
                releasePoint = trajectoryPlanner.findReleasePoint(slingshot, Math.PI / 4);
            }

            // Get the reference point
            Point refPoint = trajectoryPlanner.getReferencePoint(slingshot);


            //Calculate the tapping time according the bird type
            if (releasePoint != null) {
                double releaseAngle = trajectoryPlanner.getReleaseAngle(slingshot,
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

                int tapTime = trajectoryPlanner.getTapTime(slingshot, releasePoint, targetPoint, tapInterval);
                dx = (int) releasePoint.getX() - refPoint.x;
                dy = (int) releasePoint.getY() - refPoint.y;
                shot = new Shot(refPoint.x, refPoint.y, dx, dy, 0, tapTime);
            } else {
                logger.error("No Release Point Found");
                return state;
            }


            // check whether the slingshot is changed. the change of the slingshot indicates a change in the scale.

            ActionRobot.fullyZoomOut();
            BufferedImage screenshotBefore = ActionRobot.doScreenShot();
            Vision visionBefore = new Vision(screenshotBefore);
            List<ABObject> bnbBefore = getBlocksAndBirds(visionBefore);
            Rectangle _sling = visionBefore.findSlingshotMBR();
            if (_sling != null) {
                double scale_diff = Math.pow((slingshot.width - _sling.width), 2) + Math.pow((slingshot.height - _sling.height), 2);
                if (scale_diff < 25) {
                    if (dx < 0) {
                        actionRobot.cshoot(shot);
                        // make screenshots as long as 2 following screenshots are equal
                        while (actionRobot.getState() == GameStateExtractor.GameState.PLAYING) {
                            try {
                                Thread.sleep(500);
                                screenshot = ActionRobot.doScreenShot();
                                vision = new Vision(screenshot);
                                List<ABObject> bnbAfter = getBlocksAndBirds(vision);
                                if (bnbBefore.equals(bnbAfter)) {
                                    break;
                                } else {
                                    bnbBefore = bnbAfter;
                                }

                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        if (vision.findBirdsMBR().size() == 0 || vision.findPigsMBR().size() == 0) {
                            // if we have no pigs left or birds, wait for winning screen
                            while (actionRobot.getState() == GameStateExtractor.GameState.PLAYING) {
                                try {
                                    Thread.sleep(1500);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }

                        }

                        state = actionRobot.getState();
                        double reward = getReward(state);
                        if (state == GameStateExtractor.GameState.PLAYING) {
                            screenshot = ActionRobot.doScreenShot();
                            vision = new Vision(screenshot);
                            java.util.List<Point> traj = vision.findTrajPoints();
                            trajectoryPlanner.adjustTrajectory(traj, slingshot, releasePoint);
                            firstShot = false;
                            updateQValue(currentState, nextActionPair, new ProblemState(vision), reward, false);
                        } else if (state == GameStateExtractor.GameState.WON || state == GameStateExtractor.GameState.LOST) {
                            updateQValue(currentState, nextActionPair, currentState, reward, true);
                        }
                    }
                } else
                    logger.warn("Scale is changed, can not execute the shot, will re-segement the image");
            } else
                logger.warn("no sling detected, can not execute the shot, will re-segement the image");
        }


        return state;
    }

    private Rectangle findSlingshot(Vision vision, BufferedImage screenshot) {
        Rectangle slingshot = vision.findSlingshotMBR();

        // confirm the slingshot
        while (slingshot == null && actionRobot.getState() == GameStateExtractor.GameState.PLAYING) {
            logger.warn("No slingshot detected. Please remove pop up or zoom out");
            ActionRobot.fullyZoomOut();
            screenshot = ActionRobot.doScreenShot();
            vision = new Vision(screenshot);
            slingshot = vision.findSlingshotMBR();
            ActionRobot.skipPopUp();
        }
        return slingshot;
    }

    /**
     * checks if highest q_value is 0.0 which means that we have never been in this state,
     * so we need to initialize all possible actions to 0.0
     *
     * @param s
     */
    private void initProblemState(ProblemState s) {
        int counter = 0;
        // We have not been in this state then
        if (qValuesDAO.getActionCount(s.toString()) == 0) {
            for (ABObject obj : s.getShootableObjects()) {
                qValuesDAO.insertNewAction(0.0, s.toString(), counter);
                counter += 1;
            }
        }
    }

    /**
     * returns List of current birds and blocks
     *
     * @param vision
     * @return List of current birds and blocks
     */
    private List<ABObject> getBlocksAndBirds(Vision vision) {
        List<ABObject> allObjs = new ArrayList<>();
        allObjs.addAll(vision.findPigsMBR());
        allObjs.addAll(vision.findBlocksMBR());
        return allObjs;
    }

    /**
     * returns reward as highscore difference
     *
     * @param state
     * @return if the game is lost or the move was not the finishing one the reward is -1, else it is the highscore of the current level
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
     *
     * @param from
     * @param nextAction
     * @param to
     * @param reward
     * @param end        true if the current level was finished (could be either won or lost)
     */
    private void updateQValue(ProblemState from, ActionPair nextAction, ProblemState to, double reward, boolean end) {
        int action = nextAction.value;
        double oldValue = qValuesDAO.getQValue(from.toString(), action);
        double newValue;
        if (end) {
            newValue = oldValue + learningRate * (reward - oldValue);
        } else {
            //possible error: highest Q value could have been different compared to when the action was selected with qValuesDAO.getBestAction
            newValue = oldValue + learningRate * (reward + discountFactor * qValuesDAO.getHighestQValue(to.toString()) - oldValue);
        }
        qValuesDAO.updateQValue(newValue, from.toString(), action);
        qValuesDAO.saveMove(gameId, moveCounter, from.toString(), action, to.toString(), reward, nextAction.rand, lowTrajectory);

    }

    /**
     * Returns next action, with explorationrate as probability of taking a random action
     * and else look for the so far best action
     *
     * @param problemState
     * @return
     */
    private ActionPair getNextAction(ProblemState problemState) {
        int randomValue = randomGenerator.nextInt(100);
        if (randomValue < explorationRate * 100) {
            logger.info("Picked random action");
            return new ActionPair(true, qValuesDAO.getRandomAction(problemState.toString()));
        } else {
            logger.info("Picked currently best available action");
            return new ActionPair(false, qValuesDAO.getBestAction(problemState.toString()));
        }
    }

    private class ActionPair {
        public final boolean rand;
        public final int value;

        public ActionPair(boolean rand, int value) {
            this.rand = rand;
            this.value = value;
        }

    }
}
