package ab.demo.other;

import ab.demo.DAO.GamesDAO;
import ab.demo.strategies.Action;
import ab.server.Proxy;
import ab.vision.GameStateExtractor;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author: Julius Gonsior
 */
public class GameState {

    private BufferedImage screenshot;
    private ProblemState problemState;
    private GameStateExtractor.GameStateEnum gameStateEnum;
    private int moveCounter;
    private int gameId;
    private Rectangle slingshot;
    private double reward;
    private Action nextAction;

    public GameState(int currentLevel, GamesDAO gamesDAO, double explorationRate, double learningRate, double discountFactor) {
        this.gameId = gamesDAO.save(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);
        this.moveCounter = 0;
    }

    public Rectangle getSlingshot() {
        return slingshot;
    }

    public void setSlingshot(Rectangle slingshot) {
        this.slingshot = slingshot;
    }

    public BufferedImage getScreenshot() {
        return screenshot;
    }

    public void setScreenshot(BufferedImage screenshot) {
        this.screenshot = screenshot;
    }

    public ProblemState getProblemState() {
        return problemState;
    }

    public void setProblemState(ProblemState problemState) {
        this.problemState = problemState;
    }

    public GameStateExtractor.GameStateEnum getGameStateEnum() {
        return gameStateEnum;
    }

    public void setGameStateEnum(GameStateExtractor.GameStateEnum gameStateEnum) {
        this.gameStateEnum = gameStateEnum;
    }

    public int getMoveCounter() {
        return moveCounter;
    }

    public void incrementMoveCounter() {
        this.moveCounter++;
    }

    public int getGameId() {
        return gameId;
    }

    public double getReward() {
        return reward;
    }

    public void setReward(double reward) {
        this.reward = reward;
    }

    public Action getNextAction() {
        return nextAction;
    }

    public void setNextAction(Action nextAction) {
        this.nextAction = nextAction;
    }
}
