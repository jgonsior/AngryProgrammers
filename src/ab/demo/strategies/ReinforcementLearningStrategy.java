package ab.demo.strategies;

import ab.demo.DAO.QValuesDAO;
import ab.demo.other.ProblemState;
import ab.vision.ABObject;
import ab.vision.GameStateExtractor;
import org.apache.log4j.Logger;

import java.util.Random;

/**
 * @author: Julius Gonsior
 */
public class ReinforcementLearningStrategy extends Strategy {

    private static final Logger logger = Logger.getLogger(ReinforcementLearningStrategy.class);
    QValuesDAO qValuesDAO;
    private Random randomGenerator;
    private double discountFactor;
    private double learningRate;
    private double explorationRate;

    public ReinforcementLearningStrategy(QValuesDAO qValuesDAO) {
        super();
        this.randomGenerator = new Random();
        this.qValuesDAO = qValuesDAO;
    }

    public void setQLearningParameters(double discountFactor, double learningRate, double explorationRate) {
        this.discountFactor = discountFactor;
        this.learningRate = learningRate;
        this.explorationRate = explorationRate;
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
            //@todo get target Objects!
            for (ABObject object : problemState.getShootableObjects()) {
                qValuesDAO.insertNewAction(0, problemState.getId(), counter, object.getTrajectoryType().name(), object.toString());
                counter += 1;
            }
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
        int actionId = nextAction.getId();
        double oldValue = qValuesDAO.getQValue(from.getId(), actionId);
        double newValue;

        if (end) {
            newValue = oldValue + learningRate * (reward - oldValue);
        } else {
            //possible error: highest Q value could have been different compared to when the action was selected with qValuesDAO.getBestAction
            newValue = oldValue + learningRate * (reward + discountFactor * qValuesDAO.getHighestQValue(to.getId()) - oldValue);
        }

        qValuesDAO.updateQValue(newValue, from.getId(), actionId);

    }

    public void afterShotHook(ProblemState previousProblemState) {
        if (gameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.PLAYING) {
            updateQValue(previousProblemState, gameState.getProblemState(), gameState.getNextAction(),
                    gameState.getReward(), false, gameState.getGameId(), gameState.getMoveCounter());
        } else if (gameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.WON || gameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.LOST) {
            updateQValue(previousProblemState, gameState.getProblemState(), gameState.getNextAction(),
                    gameState.getReward(), true, gameState.getGameId(), gameState.getMoveCounter());
        }
    }


    /**
     * Returns next action, with explorationrate as probability of taking a random action
     * and else look for the so far best action
     *
     * @return
     */
    public Action getNextAction() {
        int randomValue = randomGenerator.nextInt(100);
        Action action;
        if (randomValue < explorationRate * 100) {
            //get random action should return more than one id!
            action = qValuesDAO.getRandomAction(gameState.getProblemState().getId());
            action.setProblemState(gameState.getProblemState());
            action.setRand(true);
            action.setName("random_" + action.getTrajectoryType().name() + "_" + action.getTargetObjectString());
        } else {
            action = qValuesDAO.getBestAction(gameState.getProblemState().getId());
            action.setProblemState(gameState.getProblemState());
            action.setRand(false);
            action.setName("best_" + action.getTrajectoryType().name() + "_" + action.getTargetObjectString());
        }
        logger.info("Selected the following action: " + action.getName());
        return action;
    }

}
