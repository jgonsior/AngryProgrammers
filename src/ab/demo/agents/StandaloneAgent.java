package ab.demo.agents;

import ab.demo.DAO.GamesDAO;
import ab.demo.DAO.MovesDAO;
import ab.demo.DAO.ProblemStatesDAO;
import ab.demo.other.Action;
import ab.demo.other.*;
import ab.server.Proxy;
import ab.utils.ABUtil;
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
 * Standalone Agent which can play AngryBirds
 *
 * @author jgonsior
 */
public abstract class StandaloneAgent implements Runnable {

    protected static final Logger logger = Logger.getLogger(StandaloneAgent.class);
    protected double discountFactor = 0.9;
    protected double learningRate = 0.1;
    protected double explorationRate = 0.7;
    protected Vision vision;
    protected Action nextAction;
    protected Random randomGenerator;
    protected ActionRobot actionRobot;
    protected Map<Integer, Integer> scores = new LinkedHashMap<Integer, Integer>();
    protected int fixedLevel = -1;
    protected int currentLevel;
    protected GamesDAO gamesDAO;
    protected MovesDAO movesDAO;

    protected ProblemStatesDAO problemStatesDAO;

    // a standalone implementation of the Reinforcement StandaloneAgent
    public StandaloneAgent(GamesDAO gamesDAO, MovesDAO movesDAO, ProblemStatesDAO problemStatesDAO) {
        this.gamesDAO = gamesDAO;
        this.movesDAO = movesDAO;
        this.problemStatesDAO = problemStatesDAO;
        this.actionRobot = new ActionRobot();
        this.randomGenerator = new Random();

        ActionRobot.GoFromMainMenuToLevelSelection();
    }

    /**
     * calculates based on the bird type we want to shoot at if it is a specia bird and if so what is the tapping time
     *
     * @param releasePoint
     * @param targetPoint
     * @return
     */
    protected int calculateTappingTime(Point releasePoint, Point targetPoint) {
        double releaseAngle = GameState.getTrajectoryPlanner().getReleaseAngle(GameState.getSlingshot(),
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

        return GameState.getTrajectoryPlanner().getTapTime(GameState.getSlingshot(), releasePoint, targetPoint, tappingInterval);
    }

    protected void updateCurrentVision() {
        BufferedImage screenshot = ActionRobot.doScreenShot();
        GameState.setScreenshot(screenshot);
        vision = new Vision(screenshot);
    }

    public void showCurrentScreenshot() {
        JDialog frame = new JDialog();
        frame.setModal(true);
        frame.getContentPane().setLayout(new FlowLayout());
        frame.getContentPane().add(new JLabel(new ImageIcon(GameState.getScreenshot())));
        frame.pack();
        frame.setVisible(true);
    }

    public void saveCurrentScreenshot(String title) {
        File outputFile = new File("imgs/" + Proxy.getProxyPort() + "/" + GameState.getGameId() + "/" + currentLevel + "_" + GameState.getMoveCounter() + "_" + title + "_" + System.currentTimeMillis() + ".gif");
        try {
            outputFile.getParentFile().mkdirs();
            ImageIO.write(GameState.getScreenshot(), "gif", outputFile);
        } catch (IOException e) {
            logger.error("Unable to save screenshot " + e);
            e.printStackTrace();
        }
        logger.info("Saved screenshot " + outputFile.getAbsolutePath());
    }

    public void saveCurrentScreenshot() {
        saveCurrentScreenshot(nextAction.getName());
    }


    protected void playLevel() {
        ProblemState previousProblemState;

        actionRobot.loadLevel(currentLevel);

        GameState.initNewGameState(currentLevel, gamesDAO, explorationRate, learningRate, discountFactor);
        GameState.setGameStateEnum(actionRobot.getState());

        int birdCounter = countBirds();
        while (birdCounter > 0) {
            if (GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.PLAYING) {

                updateCurrentVision();
                GameState.setSlingshot(this.findSlingshot());

                int problemStateId = problemStatesDAO.insertId();

                GameState.setProblemState(new ProblemState(vision, actionRobot, problemStateId));

                previousProblemState = GameState.getProblemState();

                // check if there are still pigs available
                List<ABObject> pigs = vision.findPigsMBR();

                if (GameState.getMoveCounter() == 0) {
                    // count initally all birds
                    countBirds();
                }
                logger.info("Current Bird count: " + birdCounter);
                updateCurrentVision();

                if (!pigs.isEmpty()) {
                    updateCurrentVision();

                    // get next action
                    nextAction = this.getNextAction();
                    GameState.setNextAction(nextAction);

                    Set<Object> blocksAndPigsBeforeShot = vision.getBlocksAndPigs(true);

                    Point releasePoint = shootOneBird(nextAction);

                    //and one bird less…
                    birdCounter--;


                    logger.info("done shooting");

                    waitUntilBlocksHaveBeenFallenDown(blocksAndPigsBeforeShot);

                    logger.info("done waiting for blocks to fall down");

                    GameState.setReward(getCurrentReward());
                    movesDAO.save(GameState.getGameId(), birdCounter, previousProblemState.getId(), nextAction.getId(), GameState.getProblemState().getId(), GameState.getReward(), nextAction.isRand(), nextAction.getTrajectoryType().name());

                    //save the information about the current zooming for the next shot
                    List<Point> trajectoryPoints = vision.findTrajPoints();
                    GameState.getTrajectoryPlanner().adjustTrajectory(trajectoryPoints, GameState.getSlingshot(), releasePoint);

                    checkIfDonePlayingAndWaitForWinningScreen(birdCounter);

                    //update currentGameStateEnum
                    GameState.setGameStateEnum(actionRobot.getState());

                    afterShotHook(previousProblemState);

                    GameState.incrementMoveCounter();
                } else {
                    logger.error("No pig's found anymore!!!!!!!!!");
                }
            } else {
                logger.warn("Accidentally in solving method without being in PLAYING state");
            }

            //if we've won or lost the game already we don't need to shoot any other birds anymore
            if (GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.WON || GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.LOST) {
                birdCounter = 0;
            }
        }
    }

    protected abstract void afterShotHook(ProblemState previousProblemState);

    protected abstract Action getNextAction();


    public void run() {
        currentLevel = fixedLevel;
        if (this.fixedLevel == -1) {
            currentLevel = 1;
        }

        int score;

        //one cycle means one shot was being executed
        while (true) {
            logger.info("Next iteration of the all mighty while loop");

            this.playLevel();

            if (GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.WON) {
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

                if (fixedLevel == -1) {
                    currentLevel++;
                }
                actionRobot.loadLevel(currentLevel); //actually currentLevel is now the next level because we have just won the current one
                // make a new trajectory planner whenever a new level is entered because of reasons
                GameState.refreshTrajectoryPlanner();

            } else if (GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.LOST) {
                logger.info("Restart level");
                actionRobot.restartLevel();
            } else if (GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.LEVEL_SELECTION) {
                logger.warn("Unexpected level selection page, go to the last current level : "
                        + currentLevel);
                actionRobot.loadLevel(currentLevel);
            } else if (GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.MAIN_MENU) {
                logger.warn("Unexpected main menu page, go to the last current level : "
                        + currentLevel);
                ActionRobot.GoFromMainMenuToLevelSelection();
                actionRobot.loadLevel(currentLevel);
            } else if (GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.EPISODE_MENU) {
                logger.warn("Unexpected episode menu page, go to the last current level : "
                        + currentLevel);
                ActionRobot.GoFromMainMenuToLevelSelection();
                actionRobot.loadLevel(currentLevel);
            }
        }
    }

    /***
     * waits until the shoot was successfully being executed
     * make screenshots as long as 2 following screenshots are equal
     * @param blocksAndPigsBefore
     */
    protected void waitUntilBlocksHaveBeenFallenDown(Set<Object> blocksAndPigsBefore) {
        this.updateCurrentVision();

        Set<Object> blocksAndPigsAfter = vision.getBlocksAndPigs(true);
        saveCurrentScreenshot();
        logger.info("bef:" + blocksAndPigsBefore);
        logger.info("aft:" + blocksAndPigsAfter);

        int loopCounter = 0;

        //wait until shot is being fired
        while (blocksAndPigsBefore.equals(blocksAndPigsAfter)) {
            try {
                logger.info("Wait for 500 (for shot)");
                Thread.sleep(500);

                this.updateCurrentVision();

                blocksAndPigsAfter = vision.getBlocksAndPigs(true);

                saveCurrentScreenshot();
                logger.info("bef:" + blocksAndPigsBefore);
                logger.info("aftd:" + blocksAndPigsAfter);
                loopCounter++;
                if (loopCounter > 30) {
                    logger.warn("Broke out of shoot-loop");
                    //possibly we are here without any reasonable reason so dont stay here forever
                    break;
                }
            } catch (InterruptedException e) {
                logger.error(e);
            }
        }

        logger.info("two different screenshots found");
        loopCounter = 0;

        while (actionRobot.getState() == GameStateExtractor.GameStateEnum.PLAYING && !blocksAndPigsBefore.equals(blocksAndPigsAfter)) {
            try {

                logger.info("Wait for 500 (for settled situation)");
                Thread.sleep(500);

                this.updateCurrentVision();

                blocksAndPigsBefore = blocksAndPigsAfter;

                blocksAndPigsAfter = vision.getBlocksAndPigs(false);

                saveCurrentScreenshot();
                logger.info("bef:" + blocksAndPigsBefore);
                logger.info("aft:" + blocksAndPigsAfter);

                loopCounter++;
                if (loopCounter > 30) {
                    logger.warn("Broke out of settle-loop");
                    //possibly we are here without any reasonable reason so dont stay here forever
                    break;
                }


            } catch (InterruptedException e) {
                logger.error(e);
            }
        }
    }

    protected void checkIfDonePlayingAndWaitForWinningScreen(int birdCounter) {
        this.updateCurrentVision();

        // check if there are some birds on the sling
        /*
        boolean birdsLeft = false;
        for (ABObject bird : vision.findBirdsMBR()){
            if (bird.getCenterX() < slingshot.x + 50 && bird.getCenterY() > slingshot.y - 30){
                birdsLeft = true;
        }*/


        if (birdCounter < 1 || vision.findPigsMBR().size() == 0) {
            logger.info("Pig amount: " + String.valueOf(vision.findPigsMBR().size()));
            logger.info("no pigs or birds (on left side) left, now wait until GameState changes");
            int loopCounter = 0;
            // if we have no pigs left or birds, wait for winning screen
            while (actionRobot.getState() == GameStateExtractor.GameStateEnum.PLAYING) {
                try {
                    Thread.sleep(300);
                    logger.info("sleep 300 for change state (current: " + actionRobot.getState() + ")");
                    loopCounter++;
                    if (loopCounter > 30) {
                        logger.warn("Broke out of state-change-loop");
                        //possibly we did not reconize some birds due to bad programmed vision module
                        //so after waiting to long break out
                        countBirds();
                        break;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            //if we have won wait until gameScore on following screens is the same
            if (actionRobot.getState() == GameStateExtractor.GameStateEnum.WON) {
                logger.info("in WON state now wait until stable reward");
                double rewardBefore = -1.0;
                double rewardAfter = getCurrentReward();
                while (rewardBefore != rewardAfter) {
                    try {
                        Thread.sleep(300);
                        logger.info("sleep 300 for new reward (current: " + String.valueOf(rewardAfter) + ")");
                        rewardBefore = rewardAfter;
                        rewardAfter = getCurrentReward();
                        this.updateCurrentVision();
                        saveCurrentScreenshot("scoreScreenshot");
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

    /**
     * logs the current higscores for each level
     */
    protected void logScores() {
        int totalScore = 0;

        for (Integer key : scores.keySet()) {
            totalScore += scores.get(key);
            logger.info(" Level " + key
                    + " Score: " + scores.get(key) + " ");
        }
        logger.info("Total Score: " + totalScore);
    }


    protected int countBirds() {
        int birdCounter = 0;
        ActionRobot.fullyZoomIn();
        updateCurrentVision();

        try {
            birdCounter = vision.findBirdsRealShape().size();
            logger.info("Birds: " + vision.findBirdsRealShape());
        } catch (NullPointerException e) {
            logger.error("Unable to find birds, now check after Zooming out " + e);
            e.printStackTrace();
        }

        ActionRobot.fullyZoomOut();

        //failed on some lvls (e.g. 1), maybe zooms to pig/structure
        if (birdCounter == 0) {
            updateCurrentVision();
            birdCounter = vision.findBirdsRealShape().size();
            logger.info("Birds: " + vision.findBirdsRealShape());
        }
        return birdCounter;

    }

    protected Rectangle findSlingshot() {
        Rectangle _slingshot = vision.findSlingshotMBR();

        // confirm the slingshot
        while (_slingshot == null && actionRobot.getState() == GameStateExtractor.GameStateEnum.PLAYING) {
            logger.warn("No slingshot detected. Please remove pop up or zoom out");
            ActionRobot.fullyZoomOut();
            this.updateCurrentVision();
            _slingshot = vision.findSlingshotMBR();
            ActionRobot.skipPopUp();
        }
        return _slingshot;
    }

    /**
     * returns reward as highscore difference
     *
     * @return if the game is lost or the move was not the finishing one the reward is -1, else it is the highscore of the current level
     */
    protected double getCurrentReward() {
        GameStateExtractor GameStateExtractor = new GameStateExtractor();
        this.updateCurrentVision();
        BufferedImage scoreScreenshot = GameState.getScreenshot();
        return GameStateExtractor.getScoreEndGame(scoreScreenshot);
    }

    /**
     * open question: what is reference point, what release point?!
     *
     * @param action
     */
    protected Point shootOneBird(Action action) {
        Point targetPoint = action.getTargetPoint();
        Rectangle slingshot = GameState.getSlingshot();

        //ABObject pig = vision.findPigsMBR().get(0);
        //targetPoint = pig.getCenter();

        Point releasePoint = ABUtil.calculateReleasePoint(targetPoint, action.getTrajectoryType(), GameState.getTrajectoryPlanner(), slingshot);
        Shot shot = ABUtil.generateShot(slingshot, GameState.getTrajectoryPlanner(), this.calculateTappingTime(releasePoint, targetPoint), releasePoint);

        // check whether the slingshot is changed. the change of the slingshot indicates a change in the scale.

        ActionRobot.fullyZoomOut();

        this.updateCurrentVision();

        Rectangle _sling = vision.findSlingshotMBR();

        if (_sling != null) {
            double scaleDifference = Math.pow((slingshot.width - _sling.width), 2) + Math.pow((slingshot.height - _sling.height), 2);
            if (scaleDifference < 25) {
                if (shot.getDx() < 0) {
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

    public void setFixedLevel(int level) {
        this.fixedLevel = level;
    }
}
