package ab.demo;

import ab.demo.DAO.*;
import ab.demo.logging.LoggingHandler;
import ab.demo.other.ActionRobot;
import ab.demo.other.Shot;
import ab.demo.qlearning.Action;
import ab.demo.qlearning.ProblemState;
import ab.demo.qlearning.StateObject;
import ab.planner.TrajectoryPlanner;
import ab.server.Proxy;
import ab.utils.StateUtil;
import ab.vision.ABObject;
import ab.vision.ABType;
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


    private Random randomGenerator;

    private QValuesDAO qValuesDAO;
    private GamesDAO gamesDAO;
    private MovesDAO movesDAO;
    private ObjectsDAO objectsDAO;
    private StateIdDAO stateIdDAO;
    private StatesDAO statesDAO;

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
    private int birdCounter;
    private int currentGameId;
    private Rectangle slingshot;

    private String currentActionName = "";

    private Map<Integer, Integer> scores = new LinkedHashMap<Integer, Integer>();

    // a standalone implementation of the Reinforcement Agent
    public ReinforcementLearningAgentStandalone(QValuesDAO qValuesDAO, GamesDAO gamesDAO, MovesDAO movesDAO, ObjectsDAO objectsDAO, StateIdDAO stateIdDAO, StatesDAO statesDAO) {
        this.actionRobot = new ActionRobot();
        this.randomGenerator = new Random();

        this.qValuesDAO = qValuesDAO;
        this.gamesDAO = gamesDAO;
        this.movesDAO = movesDAO;
        this.objectsDAO = objectsDAO;
        this.stateIdDAO = stateIdDAO;
        this.statesDAO = statesDAO;

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
                logger.info("aft:" + blocksAndPigsAfter);


            } catch (InterruptedException e) {
                logger.error(e);
            }
        }
    }

    private void checkIfDonePlayingAndWaitForWinningScreen() {
        this.updateCurrentVision();

        // check if there are some birds on the sling
        /*
        boolean birdsLeft = false;
            for (ABObject bird : currentVision.findBirdsMBR()){
                if (bird.getCenterX() < slingshot.x + 50 && bird.getCenterY() > slingshot.y - 30){
                    birdsLeft = true;
            }*/
        

        if (birdCounter == 0 || currentVision.findPigsMBR().size() == 0) {
            logger.info("Pig amount: " + String.valueOf(currentVision.findPigsMBR().size()));
            logger.info("no pigs or birds (on left side) left, now wait until gamestate changes");
            int loopCounter = 0;
            // if we have no pigs left or birds, wait for winning screen
            while (actionRobot.getState() == GameStateExtractor.GameState.PLAYING) {
                try {
                    Thread.sleep(300);
                    logger.info("sleep 300 for change state (current: " + actionRobot.getState() + ")");
                    loopCounter++;
                    if (loopCounter > 30){
                        //possibly we did not reconize some birds due to bad programmed vision module 
                        //so after waiting to long break out
                        break;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            //if we have won wait until gameScore on following screens is the same
            if (actionRobot.getState() == GameStateExtractor.GameState.WON) {
                logger.info("in WON state now wait until stable reward");
                double rewardBefore = -1.0;
                double rewardAfter = getReward(actionRobot.getState());
                currentActionName = "scoreScreenshot";
                while (rewardBefore != rewardAfter) {
                    try {
                        Thread.sleep(300);
                        logger.info("sleep 300 for new reward (current: " + String.valueOf(rewardAfter) + ")");
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
        File outputFile = new File("imgs/" + Proxy.getProxyPort() + "/" + currentGameId + "/" + currentLevel + "_" + currentMoveCounter + "_" + currentActionName + "_" + System.currentTimeMillis() + ".gif");
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

        // id which will be generated randomly every lvl so that we can connect moves to games
        currentGameId = gamesDAO.saveGame(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);

        //one cycle means one shot was being executed
        while (true) {
            logger.info("Next iteration of the all mighty while loop");

            currentGameState = actionRobot.getState();

            if (currentGameState == GameStateExtractor.GameState.PLAYING) {

                updateCurrentVision();
                slingshot = this.findSlingshot();

                updateCurrentProblemState();
                previousProblemState = currentProblemState;

                // check if there are still pigs available
                List<ABObject> pigs = currentVision.findPigsMBR();

                if (currentMoveCounter == 0){
                    // count inital all birds 
                    birdCounter = 0;

                    try {
                        ActionRobot.fullyZoomIn();
                        updateCurrentVision();
                        birdCounter = currentVision.findBirdsRealShape().size();
                        ActionRobot.fullyZoomOut();
                    } catch (NullPointerException e){
                        logger.error("Unable to find birds " + e);
                        e.printStackTrace();
                    }
                    
                    //failed on some lvls (e.g. 1), maybe zooms to pig/structure
                    if (birdCounter == 0){
                        updateCurrentVision();
                        birdCounter = currentVision.findBirdsRealShape().size();
                    }
                }
                logger.info("Current Bird count: " + String.valueOf(birdCounter));
                updateCurrentVision();

                if (!pigs.isEmpty()) {
                    updateCurrentVision();

                    // get next action
                    Action nextAction = getNextAction();

                    Set<Object> blocksAndPigsBeforeShot = getBlocksAndPigs(currentVision);

                    Point releasePoint = shootOneBird(nextAction);

                    logger.info("done shooting");

                    waitUntilBlocksHaveBeenFallenDown(blocksAndPigsBeforeShot);

                    logger.info("done waiting for blocks to fall down");

                    //save the information about the current zooming for the next shot
                    List<Point> trajectoryPoints = currentVision.findTrajPoints();
                    trajectoryPlanner.adjustTrajectory(trajectoryPoints, slingshot, releasePoint);

                    checkIfDonePlayingAndWaitForWinningScreen();

                    //update currentGameState
                    currentGameState = actionRobot.getState();

                    currentReward = getReward(currentGameState);

                    if (currentGameState == GameStateExtractor.GameState.PLAYING) {
                        updateCurrentProblemState();
                        updateQValue(previousProblemState, currentProblemState, nextAction, currentReward, false, currentGameId, currentMoveCounter);
                    } else if (currentGameState == GameStateExtractor.GameState.WON || currentGameState == GameStateExtractor.GameState.LOST) {
                        updateQValue(previousProblemState, currentProblemState, nextAction, currentReward, true, currentGameId, currentMoveCounter);
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

                currentGameId = gamesDAO.saveGame(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);
                currentMoveCounter = 0;
            } else if (currentGameState == GameStateExtractor.GameState.LOST) {
                logger.info("Restart level");
                actionRobot.restartLevel();
                currentGameId = gamesDAO.saveGame(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);
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
        // 1. create state
        ProblemState problemState = getStateId(new ProblemState(currentVision, actionRobot, 0, false));
        
        // 2. if state was generated newly create all Objects and link them to this state
        if (problemState.getInitialized() == false) {
            int objectId;
            int stateId = problemState.getId();
            for (ABObject object : problemState.getAllObjects()) {
                objectId = objectsDAO.insertObject((int) object.getCenterX() / 10,
                        (int) object.getCenterX() / 10,
                        String.valueOf(object.getType()),
                        String.valueOf(object.shape));
                statesDAO.insertState(stateId, objectId);
            }

            // 3. Generate actions in q_values if we have no actions initialised yet
            this.insertsPossibleActionsForProblemStateIntoDatabase(problemState);
            problemState.setInitialized(true);
        }

        this.currentProblemState = problemState;
    }

    /**
     * ?!
     *
     * @return ProblemState with modified id and modified initialization flag
     */
    private ProblemState getStateId(ProblemState state) {
        Set objectIds = new HashSet();

        for (ABObject object : state.getAllObjects()) {

            // do not compare birds on the right side if they still lay there
            if (String.valueOf(object.getType()).contains("Bird") && object.getCenterX() > 300) {
                continue;
            }

            objectIds.add(objectsDAO.insertObject((int) object.getCenterX() / 10,
                    (int) object.getCenterX() / 10,
                    String.valueOf(object.getType()),
                    String.valueOf(object.shape)));
        }

        List<StateObject> stateObjects = statesDAO.getObjectIdsForAllStates();
        List<Integer> similarStateIds = new ArrayList<>();
        for (StateObject stateObject : stateObjects) {
            Set<Integer> targetObjectIds = stateObject.objectIds;

            // if they are the same, return objectId
            if (objectIds.equals(targetObjectIds)) {
                logger.info("Found known state " + stateObject.stateId);
                state.setId(stateObject.stateId);
                state.setInitialized(true);
                return state;
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
            state.setId(stateIdDAO.insertStateId());
            state.setInitialized(false);
            return state;
        } else {
            //@todo in the case of multiple similar states we should use the one which is the most similar one to our own one
            state.setId(similarStateIds.get(0));
            state.setInitialized(true);
            return state;
        }
    }


    private Point calculateTargetPointFromActionObject(Action action) {
        int nextAction = action.getActionId();

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
    private Point calculateReleasePoint(Point targetPoint, ABObject.TrajectoryType trajectoryType) {
        Point releasePoint = null;
        // estimate the trajectory
        ArrayList<Point> estimateLaunchPoints = trajectoryPlanner.estimateLaunchPoint(slingshot, targetPoint);


        //@todo: the stuff with the trajectory needs to be somehow converted into getNextAction because it IS an action
        // do a high shot when entering a level to find an accurate velocity
        if (estimateLaunchPoints.size() == 1) {
            if (trajectoryType != ABObject.TrajectoryType.LOW) {
                logger.error("Somehow there was only one launch point found and therefore we can only do a LOW shot, eventhoug a HIGH shot was being requested.");
            }
            releasePoint = estimateLaunchPoints.get(0);
        } else if (estimateLaunchPoints.size() == 2) {
            if (trajectoryType == ABObject.TrajectoryType.HIGH) {
                releasePoint = estimateLaunchPoints.get(1);
            } else if (trajectoryType == ABObject.TrajectoryType.LOW) {
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
     * @param targetPoint
     * @return
     */
    private int calculateTappingTime(Point releasePoint, Point targetPoint) {
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
     * @param action
     */
    public Point shootOneBird(Action action) {
        Point targetPoint = calculateTargetPointFromActionObject(action);

        //ABObject pig = currentVision.findPigsMBR().get(0);
        //targetPoint = pig.getCenter();

        Point releasePoint = this.calculateReleasePoint(targetPoint, action.getTrajectoryType());

        int tappingTime = 0;
        if (releasePoint != null) {
            tappingTime = this.calculateTappingTime(releasePoint, targetPoint);
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
                    birdCounter--;
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
        Rectangle _slingshot = currentVision.findSlingshotMBR();

        // confirm the slingshot
        while (_slingshot == null && actionRobot.getState() == GameStateExtractor.GameState.PLAYING) {
            logger.warn("No slingshot detected. Please remove pop up or zoom out");
            ActionRobot.fullyZoomOut();
            this.updateCurrentVision();
            _slingshot = currentVision.findSlingshotMBR();
            ActionRobot.skipPopUp();
        }
        return _slingshot;
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
                qValuesDAO.insertNewAction(0, problemState.getId(), counter, object.getTrajectoryType().name(), object.toString());
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
            this.updateCurrentVision();
            BufferedImage scoreScreenshot = this.currentScreenshot;
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
    private void updateQValue(ProblemState from, ProblemState to, Action nextAction, double reward, boolean end, int gameId, int moveCounter) {
        int actionId = nextAction.getActionId();
        double oldValue = qValuesDAO.getQValue(from.getId(), actionId);
        double newValue;

        if (end) {
            newValue = oldValue + learningRate * (reward - oldValue);
        } else {
            //possible error: highest Q value could have been different compared to when the action was selected with qValuesDAO.getBestAction
            newValue = oldValue + learningRate * (reward + discountFactor * qValuesDAO.getHighestQValue(to.getId()) - oldValue);
        }

        qValuesDAO.updateQValue(newValue, from.getId(), actionId);
        movesDAO.saveMove(gameId, moveCounter, from.getId(), actionId, to.getId(), reward, nextAction.isRand(), nextAction.getTrajectoryType().name());

    }

    /**
     * Returns next action, with explorationrate as probability of taking a random action
     * and else look for the so far best action
     *
     * @return
     */
    private Action getNextAction() {
        int randomValue = randomGenerator.nextInt(100);
        Action action;
        if (randomValue < explorationRate * 100) {
            //get random action should return more than one id!
            action = qValuesDAO.getRandomAction(currentProblemState.getId());
            action.setRand(true);
            currentActionName = "random_" + action.getTrajectoryType().name() + "_" + action.getTargetObjectString();
        } else {
            action = qValuesDAO.getBestAction(currentProblemState.getId());
            action.setRand(false);
            currentActionName = "best_" + action.getTrajectoryType().name() + "_" + action.getTargetObjectString();
        }
        logger.info("Selected the following action: " + currentActionName);
        return action;
    }
}
