package ab.demo;

import ab.demo.logging.LoggingHandler;
import ab.demo.other.ActionRobot;
import ab.demo.other.Shot;
import ab.demo.qlearning.ProblemState;
import ab.demo.qlearning.QValuesDAO;
import ab.demo.qlearning.StateObject;
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
    // id which will be generated randomly every lvl that we can connect moves to one game
    private int gameId;
    private int moveCounter;
    private boolean lowTrajectory = false;

    private Map<Integer, Integer> scores = new LinkedHashMap<Integer, Integer>();

    // a standalone implementation of the Reinforcement Agent
    public ReinforcementLearningAgentStandalone(QValuesDAO qValuesDAO) {
        LoggingHandler.initFileLog();
        LoggingHandler.initConsoleLog();

        this.actionRobot = new ActionRobot();
        this.trajectoryPlanner = new TrajectoryPlanner();
        this.randomGenerator = new Random();
        this.firstShot = true;

        this.qValuesDAO = qValuesDAO;

        ActionRobot.GoFromMainMenuToLevelSelection();
    }

    // run the client
    public void run() {
        actionRobot.loadLevel(currentLevel);
        gameId = qValuesDAO.saveGame(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);
        while (true) {
            GameStateExtractor.GameState state = solve();
            moveCounter = moveCounter + 1;
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

                gameId = qValuesDAO.saveGame(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);
                moveCounter = 0;
            } else if (state == GameStateExtractor.GameState.LOST) {
                logger.info("Restart");
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
        int birdsLeft = vision.findBirdsMBR().size();
        GameStateExtractor.GameState state = actionRobot.getState();

        if (state != GameStateExtractor.GameState.PLAYING) {
            logger.warn("Accidentally in solving method without being in PLAYINg state");
            return state;
        }

        // if there is a sling, then play, otherwise just skip.
        if (sling != null) {

            if (!pigs.isEmpty()) {

                Point releasePoint = null;
                Shot shot = new Shot();
                int dx, dy;

                ProblemState currentState = new ProblemState(vision);
                int stateId = getStateId(currentState);

                // get Next best Action
                ActionPair nextActionPair = getNextAction(stateId);
                int nextAction = nextActionPair.value;

                java.util.List<ABObject> shootables = currentState.getShootableObjects();
                if (shootables.size() - 1 > nextAction) {
                    nextAction = shootables.size() - 1;
                }
                ABObject obj = shootables.get(nextAction);
                Point _tpt = obj.getCenter();

                // estimate the trajectory
                ArrayList<Point> pts = trajectoryPlanner.estimateLaunchPoint(sling, _tpt);

                // do a high shot when entering a level to find an accurate velocity
                if (firstShot && pts.size() > 1) {
                    lowTrajectory = false;
                    releasePoint = pts.get(1);
                } else if (pts.size() == 1) {
                    // TODO: find out if low or high trajectory????
                    releasePoint = pts.get(0);
                } else if (pts.size() == 2) {
                    // randomly choose between the trajectories, with a 1 in
                    // 6 chance of choosing the high one
                    if (randomGenerator.nextInt(6) == 0) {
                        lowTrajectory = false;
                        releasePoint = pts.get(1);
                    } else {
                        lowTrajectory = true;
                        releasePoint = pts.get(0);
                    }
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
                BufferedImage screenshotBefore = ActionRobot.doScreenShot();
                Vision visionBefore = new Vision(screenshotBefore);
                List<ABObject> bnbBefore = getBlocksAndBirds(visionBefore);
                Rectangle _sling = visionBefore.findSlingshotMBR();
                if (_sling != null) {
                    double scale_diff = Math.pow((sling.width - _sling.width), 2) + Math.pow((sling.height - _sling.height), 2);
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
                                    logger.info("bnbBefore: " + bnbBefore);
                                    logger.info("bnbAfter: " + bnbAfter);
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
                                trajectoryPlanner.adjustTrajectory(traj, sling, releasePoint);
                                firstShot = false;
                                updateQValue(stateId, nextActionPair, new ProblemState(vision), reward, false);
                            } else if (state == GameStateExtractor.GameState.WON || state == GameStateExtractor.GameState.LOST) {
                                updateQValue(stateId, nextActionPair, currentState, reward, true);
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
     *
     * @param s
     */
    private int initProblemState(ProblemState s) {
        int counter = 0; 
        // 1. get new StateId
        int stateId = (int)qValuesDAO.insertStateId();
        // 2. create all Objects and link them to this state
        for (ABObject obj : s.allObjects) {
            int objectId = qValuesDAO.insertObject((int)obj.getCenterX()/10, (int)obj.getCenterX()/10, String.valueOf(obj.getType()), String.valueOf(obj.shape));
            qValuesDAO.insertState(stateId, objectId);
        }
        // 3. Generate actions in q_values if we have no actions initialised yet
        if (qValuesDAO.getActionAmount(stateId) == 0) {
            for (ABObject obj : s.getShootableObjects()) {
                qValuesDAO.insertNewAction(0.0, stateId, counter);
                counter += 1;
            }
        }
        return stateId;
    }

    private int getStateId(ProblemState s) {
        Set objectIds = new HashSet();
        for (ABObject obj : s.allObjects) {
            objectIds.add(qValuesDAO.insertObject((int)obj.getCenterX()/10, (int)obj.getCenterX()/10, String.valueOf(obj.getType()), String.valueOf(obj.shape)));
        }

        List<StateObject> stateObjects = qValuesDAO.getObjectListByStates();
        List<Integer> candidates = new ArrayList<>();
        logger.info(objectIds);
        for (StateObject obj : stateObjects){
            Set targetObjecIds = new HashSet();
            String objIdString = obj.objectIds;
            String[] parts = objIdString.split(" ");

            for (String part : parts){
                targetObjecIds.add(part);
            }
            logger.info(targetObjecIds);

            // if they are the same, return objectId
            if (objectIds.equals(targetObjecIds)){
                logger.info("same");
                return obj.stateId;
            } else {
                //else look for symmetric difference
                //@todo: maybe replace this with function from Guava or similar
                Set<String> intersection = new HashSet<String>(objectIds);
                intersection.retainAll(targetObjecIds);

                Set<String> difference = new HashSet<String>();
                difference.addAll(objectIds);
                difference.addAll(targetObjecIds);
                difference.removeAll(intersection);
                if (difference.size() < 3){
                    candidates.add(obj.stateId);
                }
                logger.info("candidate");
            }
        }
        if (candidates.size() == 0){
            return this.initProblemState(s);
        } else {
            return candidates.get(0);
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
    private void updateQValue(int fromId, ActionPair nextAction, ProblemState to, double reward, boolean end) {
        int action = nextAction.value;
        int toId = getStateId(to);
        double oldValue = qValuesDAO.getQValue(fromId, action);
        double newValue;
        if (end) {
            newValue = oldValue + learningRate * (reward - oldValue);
        } else {
            //possible error: highest Q value could have been different compared to when the action was selected with qValuesDAO.getBestAction
            newValue = oldValue + learningRate * (reward + discountFactor * qValuesDAO.getHighestQValue(toId) - oldValue);
        }
        qValuesDAO.updateQValue(newValue, fromId, action);
        qValuesDAO.saveMove(gameId, moveCounter, fromId, action, toId, reward, nextAction.rand, lowTrajectory);

    }

    /**
     * Returns next action, with explorationrate as probability of taking a random action
     * and else look for the so far best action
     *
     * @param problemState
     * @return
     */
    private ActionPair getNextAction(int stateId) {
        int randomValue = randomGenerator.nextInt(100);
        if (randomValue < explorationRate * 100) {
            logger.info("Picked random action");
            return new ActionPair(true, qValuesDAO.getRandomAction(stateId));
        } else {
            logger.info("Picked currently best available action");
            return new ActionPair(false, qValuesDAO.getBestAction(stateId));
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
