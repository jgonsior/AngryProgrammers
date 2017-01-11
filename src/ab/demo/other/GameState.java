package ab.demo.other;

import ab.demo.DAO.GamesDAO;
import ab.planner.TrajectoryPlanner;
import ab.server.Proxy;
import ab.vision.GameStateExtractor;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author: Julius Gonsior
 */
public class GameState {

    private static BufferedImage screenshot;
    private static ProblemState problemState;
    private static GameStateExtractor.GameStateEnum gameStateEnum;
    private static int moveCounter;
    private static int gameId;
    private static Rectangle slingshot;
    private static double reward;
    private static Action nextAction;
    private static TrajectoryPlanner trajectoryPlanner = new TrajectoryPlanner();

    public static TrajectoryPlanner getTrajectoryPlanner() {
        return trajectoryPlanner;
    }

    public static void initNewGameState(int currentLevel, GamesDAO gamesDAO, double explorationRate, double learningRate, double discountFactor) {
        gameId = gamesDAO.save(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);
        moveCounter = 0;
    }

    public static Rectangle getSlingshot() {
        return slingshot;
    }

    public static void setSlingshot(Rectangle slingshot) {
        GameState.slingshot = slingshot;
    }

    public static BufferedImage getScreenshot() {
        return screenshot;
    }

    public static void setScreenshot(BufferedImage screenshot) {
        GameState.screenshot = screenshot;
    }

    public static ProblemState getProblemState() {
        return problemState;
    }

    public static void setProblemState(ProblemState problemState) {
        GameState.problemState = problemState;
    }

    public static GameStateExtractor.GameStateEnum getGameStateEnum() {
        return gameStateEnum;
    }

    public static void setGameStateEnum(GameStateExtractor.GameStateEnum gameStateEnum) {
        GameState.gameStateEnum = gameStateEnum;
    }

    public static int getMoveCounter() {
        return moveCounter;
    }

    public static void incrementMoveCounter() {
        GameState.moveCounter++;
    }

    public static int getGameId() {
        return gameId;
    }

    public static double getReward() {
        return reward;
    }

    public static void setReward(double reward) {
        GameState.reward = reward;
    }

    public static Action getNextAction() {
        return nextAction;
    }

    public static void setNextAction(Action nextAction) {
        GameState.nextAction = nextAction;
    }

    public static void refreshTrajectoryPlanner() {
        trajectoryPlanner = new TrajectoryPlanner();
    }
}
