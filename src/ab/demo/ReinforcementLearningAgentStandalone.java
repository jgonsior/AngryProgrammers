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

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * @author jgonsior
 */
public class ReinforcementLearningAgentStandalone implements Agent {

    private static final Logger logger = Logger.getLogger(ReinforcementLearningAgentStandalone.class);

    private ActionRobot actionRobot;

    private double discountFactor = 0.9;
    private double learningRate = 0.1;
    private double explorationRate = 0.7;

    private boolean firstShot;

    private Random randomGenerator;

    private QValuesDAO qValuesDAO;

    private boolean lowTrajectory = false;


    /**
     * some variables containing information about the current state of the game which are being used by multiple methods
     */
    private Vision currentVision;
    private TrajectoryPlanner trajectoryPlanner;
    private BufferedImage currentScreenshot;
    private ProblemState currentProblemState;
    private GameStateExtractor.GameState currentGameState;
    private int currentLevel;
    private double currentReward;
    private int currentMoveCounter;
    private int currentGameId;


    private Map<Integer, Integer> scores = new LinkedHashMap<Integer, Integer>();

    // a standalone implementation of the Reinforcement Agent
    public ReinforcementLearningAgentStandalone(QValuesDAO qValuesDAO) {
        LoggingHandler.initFileLog();

        this.actionRobot = new ActionRobot();
        this.randomGenerator = new Random();
        this.firstShot = true;

        this.qValuesDAO = qValuesDAO;

        ActionRobot.GoFromMainMenuToLevelSelection();
    }

    /***
     * waits until the shoot was successfully being executed
     * make screenshots as long as 2 following screenshots are equal
     * @param blocksAndPigsBefore
     */
    private void waitUntilBlocksHaveBeenFallenDown(Set<Object> blocksAndPigsBefore) {
        this.updateCurrentVision();

        Set<Object> blocksAndPigsAfter = getBlocksAndPigs(currentVision);
        saveCurrentScreenshot();
        logger.info("bef:" + blocksAndPigsBefore);
        logger.info("aft:" + blocksAndPigsAfter);

        //wait until shot is being fired
        while (blocksAndPigsBefore.equals(blocksAndPigsAfter)) {
            try {
                logger.info("Wait for 500");
                Thread.sleep(500);

                this.updateCurrentVision();

                blocksAndPigsAfter = getBlocksAndPigs(currentVision);

                saveCurrentScreenshot();
                logger.info("bef:" + blocksAndPigsBefore);
                logger.info("aftd" + blocksAndPigsAfter);
            } catch (InterruptedException e) {
                logger.error(e);
            }
        }

        logger.info("two different screenshots found");

        while (actionRobot.getState() == GameStateExtractor.GameState.PLAYING && !blocksAndPigsBefore.equals(blocksAndPigsAfter)) {
            try {

                logger.info("Wait for 500");
                Thread.sleep(500);

                this.updateCurrentVision();

                blocksAndPigsBefore = blocksAndPigsAfter;

                blocksAndPigsAfter = getBlocksAndPigs(currentVision);

                saveCurrentScreenshot();
                logger.info("bef:" + blocksAndPigsBefore);
                logger.info("aftd" + blocksAndPigsAfter);


            } catch (InterruptedException e) {
                logger.error(e);
            }
        }
    }

    private void checkIfDonePlayingAndWaitForWinningScreen() {
        if (currentVision.findBirdsMBR().size() == 0 || currentVision.findPigsMBR().size() == 0) {
            logger.info("no pigs or birds left, now wait until gamestate changed");
            // if we have no pigs left or birds, wait for winning screen
            while (actionRobot.getState() == GameStateExtractor.GameState.PLAYING) {
                try {
                    Thread.sleep(300);
                    logger.info("sleep 300 for change state (current: " + actionRobot.getState() + ")");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            //if we have won wait until gameScore on following screens is the same
            if (actionRobot.getState() == GameStateExtractor.GameState.WON) {
                logger.info("in WON state now wait until stable reward");
                double rewardBefore = -1.0;
                double rewardAfter = getReward(actionRobot.getState());
                while(rewardBefore != rewardAfter) {
                    try {
                        Thread.sleep(300);
                        logger.info("sleep 300 for new reward (current: " + String.valueOf(rewardAfter) +")");
                        rewardBefore = rewardAfter;
                        rewardAfter = getReward(actionRobot.getState());
                        this.updateCurrentVision();
                        saveCurrentScreenshot();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            logger.info("done waiting");
            this.updateCurrentVision();
            saveCurrentScreenshot();
        }
    }

    private void showCurrentScreenshot() {
        JDialog frame = new JDialog();
        frame.setModal(true);
        frame.getContentPane().setLayout(new FlowLayout());
        frame.getContentPane().add(new JLabel(new ImageIcon(currentScreenshot)));
        frame.pack();
        frame.setVisible(true);
    }

    private void saveCurrentScreenshot() {
        File outputFile = new File("imgs/" + Proxy.getProxyPort() + "/" + currentGameId + "/" + currentLevel + "_" + currentMoveCounter + "_" + System.currentTimeMillis() + ".gif");
        try {
            outputFile.getParentFile().mkdirs();
            ImageIO.write(currentScreenshot, "gif", outputFile);
        } catch (IOException e) {
            logger.error("Unable to save screenshot " + e);
            e.printStackTrace();
        }
        logger.info("Saved screenshot " + outputFile.getName());
    }

    public void run() {
        currentLevel = 1;
        actionRobot.loadLevel(currentLevel);
        trajectoryPlanner = new TrajectoryPlanner();

        currentMoveCounter = 0;
        int score;
        ProblemState previousProblemState;

        // id which will be generated randomly every lvl that we can connect moves to one game
        currentGameId = qValuesDAO.saveGame(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);

        //one cycle means one shot was being executed
        while (true) {
            logger.info("Next iteration of the all mighty while loop");

            updateCurrentVision();
            updateCurrentProblemState();

            currentGameState = actionRobot.getState();
            previousProblemState = currentProblemState;

            if (currentGameState == GameStateExtractor.GameState.PLAYING) {

                Rectangle slingshot = this.findSlingshot();

                // check if there are still pigs available
                List<ABObject> pigs = currentVision.findPigsMBR();

                if (!pigs.isEmpty()) {
                    updateCurrentVision();

                    // get next action
                    ActionPair nextActionPair = getNextAction();

                    Set<Object> blocksAndPigsBeforeShot = getBlocksAndPigs(currentVision);

                    Point releasePoint = shootOneBird(calculateTargetPointFromActionPair(nextActionPair), slingshot);

                    logger.info("done shooting");

                    waitUntilBlocksHaveBeenFallenDown(blocksAndPigsBeforeShot);

                    logger.info("done waiting for blocks to fall down");


                    //save the information about the current zooming for the next shot
                    //could be deleted if we don't zoom anymore
                    List<Point> trajectoryPoints = currentVision.findTrajPoints();
                    trajectoryPlanner.adjustTrajectory(trajectoryPoints, slingshot, releasePoint);

                    checkIfDonePlayingAndWaitForWinningScreen();

                    //update currentGameState
                    currentGameState = actionRobot.getState();

                    currentReward = getReward(currentGameState);

                    if (currentGameState == GameStateExtractor.GameState.PLAYING) {
                        updateCurrentProblemState();
                        updateQValue(previousProblemState, currentProblemState, nextActionPair, currentReward, false, currentGameId, currentMoveCounter);
                    } else if (currentGameState == GameStateExtractor.GameState.WON || currentGameState == GameStateExtractor.GameState.LOST) {
                        updateQValue(previousProblemState, currentProblemState, nextActionPair, currentReward, true, currentGameId, currentMoveCounter);
                    }


                    currentMoveCounter++;
                } else {
                    logger.error("No pig's found anymore!!!!!!!!!");
                }
            } else {
                logger.warn("Accidentally in solving method without being in PLAYING state");
            }

            if (currentGameState == GameStateExtractor.GameState.WON) {
                logger.info("Wait for 3000 after won screen");
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

                currentGameId = qValuesDAO.saveGame(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);
                currentMoveCounter = 0;
            } else if (currentGameState == GameStateExtractor.GameState.LOST) {
                logger.info("Restart level");
                actionRobot.restartLevel();
                currentGameId = qValuesDAO.saveGame(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);
                currentMoveCounter = 0;
            } else if (currentGameState == GameStateExtractor.GameState.LEVEL_SELECTION) {
                logger.warn("Unexpected level selection page, go to the last current level : "
                        + currentLevel);
                actionRobot.loadLevel(currentLevel);
            } else if (currentGameState == GameStateExtractor.GameState.MAIN_MENU) {
                logger.warn("Unexpected main menu page, go to the last current level : "
                        + currentLevel);
                ActionRobot.GoFromMainMenuToLevelSelection();
                actionRobot.loadLevel(currentLevel);
            } else if (currentGameState == GameStateExtractor.GameState.EPISODE_MENU) {
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

    private void updateCurrentVision() {
        currentScreenshot = ActionRobot.doScreenShot();
        currentVision = new Vision(currentScreenshot);
    }

    private void updateCurrentProblemState() {
        // 1. create state and set StateId
        ProblemState problemState = new ProblemState(currentVision, actionRobot, 0);
        ActionPair stateIdPair = getStateId(problemState);
        int stateId = stateIdPair.value;
        problemState.setId(stateId);

        // 2. if stateId was generated newly create all Objects and link them to this state
        if (stateIdPair.rand){
            int objectId;
            for (ABObject object : problemState.getAllObjects()) {
                objectId = qValuesDAO.insertObject((int) object.getCenterX() / 10,
                        (int) object.getCenterX() / 10,
                        String.valueOf(object.getType()),
                        String.valueOf(object.shape));
                qValuesDAO.insertState(stateId, objectId);
            }

            // 3. Generate actions in q_values if we have no actions initialised yet
            this.insertsPossibleActionsForProblemStateIntoDatabase(problemState);
        }

        this.currentProblemState = problemState;
    }

    /**
     * ?!
     *
     * @return ActionPair with boolean if new created id and id as value
     */
    private ActionPair getStateId(ProblemState state) {
        Set objectIds = new HashSet();

        for (ABObject object : state.getAllObjects()) {
            objectIds.add(qValuesDAO.insertObject((int) object.getCenterX() / 10,
                    (int) object.getCenterX() / 10,
                    String.valueOf(object.getType()),
                    String.valueOf(object.shape)));
        }

        List<StateObject> stateObjects = qValuesDAO.getObjectIdsForAllStates();
        List<Integer> similarStateIds = new ArrayList<>();
        for (StateObject stateObject : stateObjects) {
            Set<Integer> targetObjectIds = stateObject.objectIds;

            // if they are the same, return objectId
            if (objectIds.equals(targetObjectIds)) {
                logger.info("Found known state " + stateObject.stateId);
                return new ActionPair(false,stateObject.stateId);
            } else if (objectIds.size() == targetObjectIds.size()) {
                //else look for symmetric difference if same length
                //(we assume the vision can count correctly, just had problems between rect and circle)
                Set<Integer> intersection = new HashSet<Integer>(objectIds);
                intersection.retainAll(targetObjectIds);

                Set<Integer> difference = new HashSet<Integer>();
                difference.addAll(objectIds);
                difference.addAll(targetObjectIds);
                difference.removeAll(intersection);

                if (difference.size() < 3) {
                    similarStateIds.add(stateObject.stateId);
                    logger.info("Candidate state: " + stateObject.stateId);
                }
            }
        }

        if (similarStateIds.size() == 0) {
            logger.info("Init new state");
            return new ActionPair(true, qValuesDAO.insertStateId());
        } else {
            //@todo in the case of multiple similar states we should use the one which is the most similar one to our own one
            return new ActionPair(false, similarStateIds.get(0));
        }
    }


    private Point calculateTargetPointFromActionPair(ActionPair actionPair) {
        int nextAction = actionPair.value;

        List<ABObject> shootableObjects = currentProblemState.getShootableObjects();

        //@todo should be removed and it needs to be investigated why nextAction returns sometimes wrong actions!
        // seems to be error in vision module where it found invisible objects on initialisation 
        if (shootableObjects.size() - 1 < nextAction) {
            nextAction = shootableObjects.size() - 1;
        }

        ABObject targetObject = shootableObjects.get(nextAction);
        return targetObject.getCenter();
    }

    /**
     * I have no Idea what these function is doing, but maybe it's useful…
     */
    private Point calculateMaybeTheReleasePoint(Rectangle slingshot, Point targetPoint) {
        Point releasePoint = null;
        // estimate the trajectory
        ArrayList<Point> estimateLaunchPoints = trajectoryPlanner.estimateLaunchPoint(slingshot, targetPoint);


        //@todo: the stuff with the trajectory needs to be somehow converted into getNextAction because it IS an action
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
        return releasePoint;
    }

    /**
     * calculates based on the bird type we want to shoot at if it is a specia bird and if so what is the tapping time
     *
     * @param releasePoint
     * @param slingshot
     * @param targetPoint
     * @return
     */
    private int calculateTappingTime(Point releasePoint, Rectangle slingshot, Point targetPoint) {
        double releaseAngle = trajectoryPlanner.getReleaseAngle(slingshot,
                releasePoint);
        logger.info("Release Point: " + releasePoint);
        logger.info("Release Angle: "
                + Math.toDegrees(releaseAngle));
        int tappingInterval = 0;
        switch (actionRobot.getBirdTypeOnSling()) {

            case RedBird:
                tappingInterval = 0;
                break;               // start of trajectory
            case YellowBird:
                tappingInterval = 65 + randomGenerator.nextInt(25);
                break; // 65-90% of the way
            case WhiteBird:
                tappingInterval = 70 + randomGenerator.nextInt(20);
                break; // 70-90% of the way
            case BlackBird:
                tappingInterval = 70 + randomGenerator.nextInt(20);
                break; // 70-90% of the way
            case BlueBird:
                tappingInterval = 65 + randomGenerator.nextInt(20);
                break; // 65-85% of the way
            default:
                tappingInterval = 60;
        }

        return trajectoryPlanner.getTapTime(slingshot, releasePoint, targetPoint, tappingInterval);


    }

    /**
     * open question: what is reference point, what release point?!
     *
     * @param targetPoint
     */
    public Point shootOneBird(Point targetPoint, Rectangle slingshot) {

        //ABObject pig = currentVision.findPigsMBR().get(0);
        //targetPoint = pig.getCenter();

        Point releasePoint = this.calculateMaybeTheReleasePoint(slingshot, targetPoint);

        int tappingTime = 0;
        if (releasePoint != null) {
            tappingTime = this.calculateTappingTime(releasePoint, slingshot, targetPoint);
        } else {
            logger.error("No release point found -,-");
        }

        Point referencePoint = trajectoryPlanner.getReferencePoint(slingshot);

        int dx = (int) releasePoint.getX() - referencePoint.x;
        int dy = (int) releasePoint.getY() - referencePoint.y;

        Shot shot = new Shot(referencePoint.x, referencePoint.y, dx, dy, 1, tappingTime);


        // check whether the slingshot is changed. the change of the slingshot indicates a change in the scale.

        ActionRobot.fullyZoomOut();

        this.updateCurrentVision();

        Rectangle _sling = currentVision.findSlingshotMBR();

        if (_sling != null) {
            double scaleDifference = Math.pow((slingshot.width - _sling.width), 2) + Math.pow((slingshot.height - _sling.height), 2);
            if (scaleDifference < 25) {
                if (dx < 0) {
                    actionRobot.cshoot(shot);
                }
            } else {
                logger.warn("Scale is changed, can not execute the shot, will re-segement the image");
            }
        } else {
            logger.warn("no sling detected, can not execute the shot, will re-segement the image");
        }

        return releasePoint;
    }

    private Rectangle findSlingshot() {
        Rectangle slingshot = currentVision.findSlingshotMBR();

        // confirm the slingshot
        while (slingshot == null && actionRobot.getState() == GameStateExtractor.GameState.PLAYING) {
            logger.warn("No slingshot detected. Please remove pop up or zoom out");
            ActionRobot.fullyZoomOut();
            this.updateCurrentVision();
            slingshot = currentVision.findSlingshotMBR();
            ActionRobot.skipPopUp();
        }
        return slingshot;
    }

    /**
     * checks if highest q_value is 0.0 which means that we have never been in this state,
     * so we need to initialize all possible actions to 0.0
     *
     * @param problemState
     */
    private void insertsPossibleActionsForProblemStateIntoDatabase(ProblemState problemState) {
        int counter = 0;
        if (qValuesDAO.getActionCount(problemState.getId()) == 0) {
            for (ABObject object : problemState.getShootableObjects()) {
                qValuesDAO.insertNewAction(0, problemState.getId(), counter);
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
    private Set<Object> getBlocksAndPigs(Vision vision) {
        Set<Object> allObjs = new HashSet<>();
        allObjs.addAll(vision.findPigsMBR());
        allObjs.addAll(vision.findBlocksMBR());
        allObjs.addAll(vision.findTrajPoints());
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
    private void updateQValue(ProblemState from, ProblemState to, ActionPair nextAction, double reward, boolean end, int gameId, int moveCounter) {
        int action = nextAction.value;
        double oldValue = qValuesDAO.getQValue(from.getId(), action);
        double newValue;

        if (end) {
            newValue = oldValue + learningRate * (reward - oldValue);
        } else {
            //possible error: highest Q value could have been different compared to when the action was selected with qValuesDAO.getBestAction
            newValue = oldValue + learningRate * (reward + discountFactor * qValuesDAO.getHighestQValue(to.getId()) - oldValue);
        }

        qValuesDAO.updateQValue(newValue, from.getId(), action);
        qValuesDAO.saveMove(gameId, moveCounter, from.getId(), action, to.getId(), reward, nextAction.rand, lowTrajectory);

    }

    /**
     * Returns next action, with explorationrate as probability of taking a random action
     * and else look for the so far best action
     *
     * @return
     */
    private ActionPair getNextAction() {
        int randomValue = randomGenerator.nextInt(100);
        if (randomValue < explorationRate * 100) {
            logger.info("Picked random action");
            return new ActionPair(true, qValuesDAO.getRandomAction(currentProblemState.getId()));
        } else {
            logger.info("Picked currently best available action");
            return new ActionPair(false, qValuesDAO.getBestAction(currentProblemState.getId()));
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
