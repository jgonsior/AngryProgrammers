package ab.demo.Agents;

import ab.demo.ProblemState;
import ab.demo.other.ActionRobot;
import ab.demo.other.Shot;
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
 * interface for an agent which can play angry birds
 *
 * @author jgonsior
 */
public abstract class Agent implements Runnable {

    private static final Logger logger = Logger.getLogger(Agent.class);

    protected Vision currentVision;
    protected TrajectoryPlanner trajectoryPlanner;
    protected BufferedImage currentScreenshot;
    protected ProblemState currentProblemState;
    protected GameStateExtractor.GameStateEnum currentGameStateEnum;
    protected int currentLevel;
    protected ab.demo.Action currentAction;
    protected double currentReward;
    protected int currentMoveCounter;
    protected int birdCounter;
    protected int currentGameId;
    protected Random randomGenerator;
    protected Rectangle slingshot;
    protected ActionRobot actionRobot;


    protected Map<Integer, Integer> scores = new LinkedHashMap<Integer, Integer>();
    protected boolean fixedLevel = false;

    protected void updateCurrentVision() {
        currentScreenshot = ActionRobot.doScreenShot();
        currentVision = new Vision(currentScreenshot);
    }

    public void showCurrentScreenshot() {
        JDialog frame = new JDialog();
        frame.setModal(true);
        frame.getContentPane().setLayout(new FlowLayout());
        frame.getContentPane().add(new JLabel(new ImageIcon(currentScreenshot)));
        frame.pack();
        frame.setVisible(true);
    }

    public void saveCurrentScreenshot(String title) {
        File outputFile = new File("imgs/" + Proxy.getProxyPort() + "/" + currentGameId + "/" + currentLevel + "_" + currentMoveCounter + "_" + title + "_" + System.currentTimeMillis() + ".gif");
        try {
            outputFile.getParentFile().mkdirs();
            ImageIO.write(currentScreenshot, "gif", outputFile);
        } catch (IOException e) {
            logger.error("Unable to save screenshot " + e);
            e.printStackTrace();
        }
        logger.info("Saved screenshot " + outputFile.getAbsolutePath());
    }

    public void saveCurrentScreenshot() {
        saveCurrentScreenshot(currentAction.getName());
    }

    public void run() {
        if (!this.fixedLevel) {
            currentLevel = 1;
        }
        actionRobot.loadLevel(currentLevel);
        trajectoryPlanner = new TrajectoryPlanner();

        currentMoveCounter = 0;
        int score;
        ProblemState previousProblemState;

        // id which will be generated randomly every lvl so that we can connect moves to games
        calculateCurrentGameId();

        //one cycle means one shot was being executed
        while (true) {
            logger.info("Next iteration of the all mighty while loop");

            currentGameStateEnum = actionRobot.getState();

            if (currentGameStateEnum == GameStateExtractor.GameStateEnum.PLAYING) {

                updateCurrentVision();
                slingshot = this.findSlingshot();

                updateCurrentProblemState();
                previousProblemState = currentProblemState;

                // check if there are still pigs available
                List<ABObject> pigs = currentVision.findPigsMBR();

                if (currentMoveCounter == 0) {
                    // count inital all birds 
                    updateBirdCounter();
                }
                logger.info("Current Bird count: " + String.valueOf(birdCounter));
                updateCurrentVision();

                if (!pigs.isEmpty()) {
                    updateCurrentVision();

                    // get next action
                    calculateNextAction();

                    Set<Object> blocksAndPigsBeforeShot = currentVision.getBlocksAndPigs(true);

                    Point releasePoint = shootOneBird(currentAction);

                    logger.info("done shooting");

                    waitUntilBlocksHaveBeenFallenDown(blocksAndPigsBeforeShot);

                    logger.info("done waiting for blocks to fall down");

                    //save the information about the current zooming for the next shot
                    List<Point> trajectoryPoints = currentVision.findTrajPoints();
                    trajectoryPlanner.adjustTrajectory(trajectoryPoints, slingshot, releasePoint);

                    checkIfDonePlayingAndWaitForWinningScreen();

                    //update currentGameStateEnum
                    currentGameStateEnum = actionRobot.getState();

                    currentReward = getReward(currentGameStateEnum);

                    this.afterShotHook(previousProblemState);


                    currentMoveCounter++;
                } else {
                    logger.error("No pig's found anymore!!!!!!!!!");
                }
            } else {
                logger.warn("Accidentally in solving method without being in PLAYING state");
            }

            if (currentGameStateEnum == GameStateExtractor.GameStateEnum.WON) {
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

                if (!fixedLevel) {
                    currentLevel++;
                }
                actionRobot.loadLevel(currentLevel); //actually currentLevel is now the next level because we have just won the current one
                // make a new trajectory planner whenever a new level is entered because of reasons
                trajectoryPlanner = new TrajectoryPlanner();
                calculateCurrentGameId();
                currentMoveCounter = 0;
            } else if (currentGameStateEnum == GameStateExtractor.GameStateEnum.LOST) {
                restartThisLevel();
            } else if (currentGameStateEnum == GameStateExtractor.GameStateEnum.LEVEL_SELECTION) {
                logger.warn("Unexpected level selection page, go to the last current level : "
                        + currentLevel);
                actionRobot.loadLevel(currentLevel);
            } else if (currentGameStateEnum == GameStateExtractor.GameStateEnum.MAIN_MENU) {
                logger.warn("Unexpected main menu page, go to the last current level : "
                        + currentLevel);
                ActionRobot.GoFromMainMenuToLevelSelection();
                actionRobot.loadLevel(currentLevel);
            } else if (currentGameStateEnum == GameStateExtractor.GameStateEnum.EPISODE_MENU) {
                logger.warn("Unexpected episode menu page, go to the last current level : "
                        + currentLevel);
                ActionRobot.GoFromMainMenuToLevelSelection();
                actionRobot.loadLevel(currentLevel);
            }
        }
    }

    protected abstract void updateCurrentProblemState();

    protected abstract void afterShotHook(ProblemState previousProblemState);

    protected abstract void calculateNextAction();


    /***
     * waits until the shoot was successfully being executed
     * make screenshots as long as 2 following screenshots are equal
     * @param blocksAndPigsBefore
     */
    protected void waitUntilBlocksHaveBeenFallenDown(Set<Object> blocksAndPigsBefore) {
        this.updateCurrentVision();

        Set<Object> blocksAndPigsAfter = currentVision.getBlocksAndPigs(true);
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

                blocksAndPigsAfter = currentVision.getBlocksAndPigs(true);

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

                blocksAndPigsAfter = currentVision.getBlocksAndPigs(false);

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

    protected void checkIfDonePlayingAndWaitForWinningScreen() {
        this.updateCurrentVision();

        // check if there are some birds on the sling
        /*
        boolean birdsLeft = false;
        for (ABObject bird : currentVision.findBirdsMBR()){
            if (bird.getCenterX() < slingshot.x + 50 && bird.getCenterY() > slingshot.y - 30){
                birdsLeft = true;
        }*/


        if (birdCounter < 1 || currentVision.findPigsMBR().size() == 0) {
            logger.info("Pig amount: " + String.valueOf(currentVision.findPigsMBR().size()));
            logger.info("no pigs or birds (on left side) left, now wait until gamestate changes");
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
                        updateBirdCounter();
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
                double rewardAfter = getReward(actionRobot.getState());
                while (rewardBefore != rewardAfter) {
                    try {
                        Thread.sleep(300);
                        logger.info("sleep 300 for new reward (current: " + String.valueOf(rewardAfter) + ")");
                        rewardBefore = rewardAfter;
                        rewardAfter = getReward(actionRobot.getState());
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

    public void setFixedLevel(int level) {
        this.fixedLevel = true;
        this.currentLevel = level;
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


    protected void updateBirdCounter() {
        birdCounter = 0;
        ActionRobot.fullyZoomIn();
        updateCurrentVision();

        try {
            birdCounter = currentVision.findBirdsRealShape().size();
            logger.info("Birds: " + currentVision.findBirdsRealShape());
        } catch (NullPointerException e) {
            logger.error("Unable to find birds, now check after Zooming out " + e);
            e.printStackTrace();
        }

        ActionRobot.fullyZoomOut();

        //failed on some lvls (e.g. 1), maybe zooms to pig/structure
        if (birdCounter == 0) {
            updateCurrentVision();
            birdCounter = currentVision.findBirdsRealShape().size();
            logger.info("Birds: " + currentVision.findBirdsRealShape());
        }

    }


    protected Rectangle findSlingshot() {
        Rectangle _slingshot = currentVision.findSlingshotMBR();

        // confirm the slingshot
        while (_slingshot == null && actionRobot.getState() == GameStateExtractor.GameStateEnum.PLAYING) {
            logger.warn("No slingshot detected. Please remove pop up or zoom out");
            ActionRobot.fullyZoomOut();
            this.updateCurrentVision();
            _slingshot = currentVision.findSlingshotMBR();
            ActionRobot.skipPopUp();
        }
        return _slingshot;
    }

    /**
     * returns reward as highscore difference
     *
     * @param state
     * @return if the game is lost or the move was not the finishing one the reward is -1, else it is the highscore of the current level
     */
    protected double getReward(GameStateExtractor.GameStateEnum state) {
        if (state == GameStateExtractor.GameStateEnum.WON) {
            GameStateExtractor gameStateExtractor = new GameStateExtractor();
            this.updateCurrentVision();
            BufferedImage scoreScreenshot = this.currentScreenshot;
            return gameStateExtractor.getScoreEndGame(scoreScreenshot);
        } else {
            return -1;
        }
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

    protected abstract int calculateTappingTime(Point releasePoint, Point targetPoint);

    /**
     * open question: what is reference point, what release point?!
     *
     * @param action
     */
    protected Point shootOneBird(ab.demo.Action action) {
        Point targetPoint = action.getTargetPoint();

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

    protected abstract void calculateCurrentGameId();


    protected void restartThisLevel() {
        logger.info("Restart level");
        actionRobot.restartLevel();
        calculateCurrentGameId();
        currentMoveCounter = 0;
    }

}
