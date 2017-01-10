package ab.demo.strategies;

import ab.demo.other.GameState;
import ab.demo.other.ProblemState;

/**
 * @author: Julius Gonsior
 */
public abstract class Strategy {
    protected GameState gameState;

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    public abstract Action getNextAction();

    public abstract void afterShotHook(ProblemState previousProblemState);

    public void setQLearningParameters(double discountFactor, double learningRate, double explorationRate) {
    }
}