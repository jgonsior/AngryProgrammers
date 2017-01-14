package ab.demo.agents;

import ab.demo.DAO.GamesDAO;
import ab.demo.DAO.MovesDAO;
import ab.demo.DAO.ProblemStatesDAO;
import ab.demo.DAO.QValuesDAO;
import ab.demo.other.Action;
import ab.demo.other.GameState;
import ab.demo.other.ProblemState;
import ab.vision.ABObject;
import ab.vision.GameStateExtractor;
import org.apache.log4j.Logger;

/**
 * @author: Julius Gonsior
 */
public class ReinforcementLearningAgent extends StandaloneAgent {
    private static final Logger logger = Logger.getLogger(ReinforcementLearningAgent.class);
    private QValuesDAO qValuesDAO;

    public ReinforcementLearningAgent(GamesDAO gamesDAO, MovesDAO movesDAO, ProblemStatesDAO problemStatesDAO, QValuesDAO qValuesDAO) {
        super(gamesDAO, movesDAO, problemStatesDAO);
        this.qValuesDAO = qValuesDAO;
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
            for (ABObject object : problemState.getTargetObjects()) {
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
        if (GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.PLAYING) {
            updateQValue(previousProblemState, GameState.getProblemState(), GameState.getNextAction(),
                    GameState.getReward(), false, GameState.getGameId(), GameState.getMoveCounter());
        } else if (GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.WON || GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.LOST) {
            updateQValue(previousProblemState, GameState.getProblemState(), GameState.getNextAction(),
                    GameState.getReward(), true, GameState.getGameId(), GameState.getMoveCounter());
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
            action = qValuesDAO.getRandomAction(GameState.getProblemState().getId());
            action.setProblemState(GameState.getProblemState());
            action.setRand(true);
            action.setName("random_" + action.getTrajectoryType().name() + "_" + action.getTargetObjectString());
        } else {
            action = qValuesDAO.getBestAction(GameState.getProblemState().getId());
            action.setProblemState(GameState.getProblemState());
            action.setRand(false);
            action.setName("best_" + action.getTrajectoryType().name() + "_" + action.getTargetObjectString());
        }
        logger.info("Selected the following action: " + action.getName());
        return action;
    }

}
