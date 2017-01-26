package ab.demo.agents;

import ab.demo.DAO.GamesDAO;
import ab.demo.DAO.MovesDAO;
import ab.demo.DAO.ProblemStatesDAO;
import ab.demo.DAO.QValuesDAO;
import ab.demo.other.Action;
import ab.demo.other.GameState;
import ab.demo.other.ProblemState;
import ab.vision.GameStateExtractor;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
     */
    @Override
    protected void insertPossibleActionsForProblemStateIntoDatabase() {
        ProblemState problemState = GameState.getProblemState();
        if (qValuesDAO.getActionCount(problemState.getId()) == 0) {
            logger.info("No Actions found for StateID: " + problemState.getId() + " -> Insert Actions");
            for (Action action : problemState.getPossibleActions()) {
                qValuesDAO.insertNewAction(
                        0,
                        problemState.getId(),
                        action.getTargetObject().x,
                        action.getTargetObject().y,
                        action.getTargetObject().getType().toString(),
                        action.getTargetObject().objectsAboveCount,
                        action.getTargetObject().objectsLeftCount,
                        action.getTargetObject().objectsRightCount,
                        action.getTargetObject().objectsBelowCount,
                        action.getTargetObject().distanceToPigs,
                        action.getTrajectoryType().name(),
                        action.getTargetObject().myToString());
            }
        }
    }

    @Override
    protected int getProblemStateId(ProblemState problemState) {
        int problemStateId = new ArrayList<>(getPossibleProblemStateIds(problemState)).get(0);
        logger.info("Calculated Problemstate ID: " + problemStateId);
        return problemStateId;
    }

    @Override
    protected int getNumberOfProblemStateIds(ProblemState problemState) {
        Set<Integer> posIds = getPossibleProblemStateIds(problemState);
        logger.info("Possible ProblemstateIDs: " + posIds);
        return posIds.size();
    }

    private Set<Integer> getPossibleProblemStateIds(ProblemState problemState) {
        // idea: look for every action in which states it occurs and then intersect all of them
        Set<Integer> possibleProblemstateIds = new HashSet<Integer>();
        boolean firstFoundProblemStateIdS = true;
        int amountOfDifferentActions = 0;
        
        for (Action action : problemState.getPossibleActions()){
            List<Integer> problemStateIds = qValuesDAO.getStateIds(
                action.getTargetObject().x,
                action.getTargetObject().y,
                action.getTargetObject().getType().toString(),
                action.getTargetObject().objectsAboveCount,
                action.getTargetObject().objectsLeftCount,
                action.getTargetObject().objectsRightCount,
                action.getTargetObject().objectsBelowCount,
                action.getTargetObject().distanceToPigs,
                action.getTrajectoryType().name());
            if (firstFoundProblemStateIdS) {
                possibleProblemstateIds.addAll(problemStateIds);
                firstFoundProblemStateIdS = false;
            } else {
                // perform intersection
                // allow 1 wrong action
                Set<Integer> _temp = new HashSet<Integer>(possibleProblemstateIds);
                _temp.retainAll(problemStateIds);
                if (_temp.size() == 0 && amountOfDifferentActions == 0){
                    amountOfDifferentActions++;
                } else if (possibleProblemstateIds.size() == 0){
                    //if empty it wont fill up later so return here
                    return possibleProblemstateIds;
                } else {
                    possibleProblemstateIds.retainAll(problemStateIds);
                }
            }
        }

        return possibleProblemstateIds;
    }

    /**
     * updates q-value in database when new information comes in
     *
     * @param from
     * @param to
     * @param reward
     * @param end        true if the current level was finished (could be either won or lost)
     */
    private void updateQValue(ProblemState from, ProblemState to, Action action, double reward, boolean end) {
        double oldValue = qValuesDAO.getQValue(from.getId(),
                action.getTargetObject().x,
                action.getTargetObject().y,
                action.getTargetObject().getType().toString(),
                action.getTargetObject().objectsAboveCount,
                action.getTargetObject().objectsLeftCount,
                action.getTargetObject().objectsRightCount,
                action.getTargetObject().objectsBelowCount,
                action.getTargetObject().distanceToPigs,
                action.getTrajectoryType().name());
        double newValue;

        if (end) {
            newValue = oldValue + learningRate * (reward - oldValue);
        } else {
            //possible error: highest Q value could have been different compared to when the action was selected with qValuesDAO.getBestAction
            newValue = oldValue + learningRate * (reward + discountFactor * qValuesDAO.getHighestQValue(to.getId()) - oldValue);
        }

        qValuesDAO.updateQValue(newValue, from.getId(),
                action.getTargetObject().x,
                action.getTargetObject().y,
                action.getTargetObject().getType().toString(),
                action.getTargetObject().objectsAboveCount,
                action.getTargetObject().objectsLeftCount,
                action.getTargetObject().objectsRightCount,
                action.getTargetObject().objectsBelowCount,
                action.getTargetObject().distanceToPigs,
                action.getTrajectoryType().name());

    }

    public void afterShotHook(ProblemState previousProblemState) {
        if (GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.PLAYING) {
            updateQValue(previousProblemState, GameState.getProblemState(), GameState.getNextAction(),
                    GameState.getReward(), false);
        } else if (GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.WON || GameState.getGameStateEnum() == GameStateExtractor.GameStateEnum.LOST) {
            updateQValue(previousProblemState, GameState.getProblemState(), GameState.getNextAction(),
                    GameState.getReward(), true);
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
            //action.setProblemState(GameState.getProblemState());
            action.setRand(true);
            action.setName("random_" + action.getTrajectoryType().name() + "_" + action.getTargetObjectString());
        } else {
            action = qValuesDAO.getBestAction(GameState.getProblemState().getId());
            //action.setProblemState(GameState.getProblemState());
            action.setRand(false);
            action.setName("best_" + action.getTrajectoryType().name() + "_" + action.getTargetObjectString());
        }
        logger.info("Selected the following action: " + action.getName());
        return action;
    }

}
