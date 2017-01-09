package ab.demo.other;

import ab.demo.DAO.GamesDAO;
import ab.demo.ProblemState;
import ab.server.Proxy;
import ab.vision.GameStateExtractor;

import java.awt.image.BufferedImage;

/**
 * @author: Julius Gonsior
 */
public class GameState {

    private BufferedImage currentScreenshot;
    private ProblemState currentProblemState;
    private GameStateExtractor.GameStateEnum currentGameStateEnum;
    private int currentMoveCounter;
    private int currentGameId;

    public GameState(int currentLevel, GamesDAO gamesDAO, double explorationRate, double learningRate, double discountFactor) {
        this.currentGameId = gamesDAO.saveGame(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);
        this.currentMoveCounter = 0;
    }

    public BufferedImage getCurrentScreenshot() {
        return currentScreenshot;
    }

    public void setCurrentScreenshot(BufferedImage currentScreenshot) {
        this.currentScreenshot = currentScreenshot;
    }

    public ProblemState getCurrentProblemState() {
        return currentProblemState;
    }

    public void setCurrentProblemState(ProblemState currentProblemState) {
        this.currentProblemState = currentProblemState;
    }

    public GameStateExtractor.GameStateEnum getCurrentGameStateEnum() {
        return currentGameStateEnum;
    }

    public void setCurrentGameStateEnum(GameStateExtractor.GameStateEnum currentGameStateEnum) {
        this.currentGameStateEnum = currentGameStateEnum;
    }


    public int getCurrentMoveCounter() {
        return currentMoveCounter;
    }

    public void incrementMoveCounter() {
        this.currentMoveCounter++;
    }


    public int getCurrentGameId() {
        return currentGameId;
    }
}
