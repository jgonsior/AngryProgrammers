package ab.demo.strategies;

import ab.demo.ProblemState;
import ab.demo.other.GameState;

/**
 * @author: Julius Gonsior
 */
public abstract class Strategy {
    private ProblemState problemState;
    private GameState gameState;

    protected GameState getGameState() {
        return gameState;
    }

    protected void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    protected ProblemState getProblemState() {
        return problemState;
    }

    protected void setProblemState(ProblemState problemState) {
        this.problemState = problemState;
    }

    public abstract Action getNextAction();

    public abstract void afterShotHook(ProblemState previousProblemState);
}