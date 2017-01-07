package ab.demo.Agents;

import ab.demo.Action;
import ab.demo.DAO.*;
import ab.demo.ProblemState;
import ab.demo.other.ActionRobot;
import ab.demo.qlearning.StateObject;
import ab.server.Proxy;
import ab.vision.ABObject;
import ab.vision.GameStateExtractor;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author jgonsior
 */
public class ReinforcementLearningAgentStandalone extends Agent {

    private static final Logger logger = Logger.getLogger(ReinforcementLearningAgentStandalone.class);

    private double discountFactor = 0.9;
    private double learningRate = 0.1;
    private double explorationRate = 0.7;

    private QValuesDAO qValuesDAO;
    private GamesDAO gamesDAO;
    private MovesDAO movesDAO;
    private ObjectsDAO objectsDAO;
    private StateIdDAO stateIdDAO;
    private StatesDAO statesDAO;

    // a standalone implementation of the Reinforcement Agent
    public ReinforcementLearningAgentStandalone(QValuesDAO qValuesDAO, GamesDAO gamesDAO, MovesDAO movesDAO, ObjectsDAO objectsDAO, StateIdDAO stateIdDAO, StatesDAO statesDAO) {
        this.actionRobot = new ActionRobot();
        this.randomGenerator = new Random();

        this.qValuesDAO = qValuesDAO;
        this.gamesDAO = gamesDAO;
        this.movesDAO = movesDAO;
        this.objectsDAO = objectsDAO;
        this.stateIdDAO = stateIdDAO;
        this.statesDAO = statesDAO;

        ActionRobot.GoFromMainMenuToLevelSelection();
    }


    /**
     * ?!
     *
     * @return ProblemState with modified id and modified initialization flag
     */
    private ProblemState getStateId(ProblemState state) {
        Set objectIds = new HashSet();

        for (ABObject object : state.getAllObjects()) {

            // do not compare birds on the right side if they still lay there
            if (String.valueOf(object.getType()).contains("Bird") && object.getCenterX() > 300) {
                continue;
            }

            objectIds.add(objectsDAO.insertObject((int) object.getCenterX() / 10,
                    (int) object.getCenterX() / 10,
                    String.valueOf(object.getType()),
                    String.valueOf(object.shape)));
        }

        List<StateObject> stateObjects = statesDAO.getObjectIdsForAllStates();
        List<Integer> similarStateIds = new ArrayList<>();
        for (StateObject stateObject : stateObjects) {
            Set<Integer> targetObjectIds = stateObject.objectIds;

            // if they are the same, return objectId
            if (objectIds.equals(targetObjectIds)) {
                logger.info("Found known state " + stateObject.stateId);
                state.setId(stateObject.stateId);
                state.setInitialized(true);
                return state;
            } else if (objectIds.size() == targetObjectIds.size()) {
                //else look for symmetric difference if same length
                //(we assume the vision can count correctly, just had problems between rect and circle)
                Set<Integer> intersection = new HashSet<Integer>(objectIds);
                intersection.retainAll(targetObjectIds);

                Set<Integer> difference = new HashSet<Integer>();
                difference.addAll(objectIds);
                difference.addAll(targetObjectIds);
                difference.removeAll(intersection);

                if (difference.size() < 3) {
                    similarStateIds.add(stateObject.stateId);
                    logger.info("Candidate state: " + stateObject.stateId);
                }
            }
        }

        if (similarStateIds.size() == 0) {
            logger.info("Init new state");
            state.setId(stateIdDAO.insertStateId());
            state.setInitialized(false);
            return state;
        } else {
            //@todo in the case of multiple similar states we should use the one which is the most similar one to our own one
            state.setId(similarStateIds.get(0));
            state.setInitialized(true);
            return state;
        }
    }


    /**
     * calculates based on the bird type we want to shoot at if it is a specia bird and if so what is the tapping time
     *
     * @param releasePoint
     * @param targetPoint
     * @return
     */
    protected int calculateTappingTime(Point releasePoint, Point targetPoint) {
        double releaseAngle = trajectoryPlanner.getReleaseAngle(slingshot,
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

        return trajectoryPlanner.getTapTime(slingshot, releasePoint, targetPoint, tappingInterval);
    }

    @Override
    protected void calculateCurrentGameId() {
        currentGameId = gamesDAO.saveGame(currentLevel, Proxy.getProxyPort(), explorationRate, learningRate, discountFactor);
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
        int actionId = nextAction.getActionId();
        double oldValue = qValuesDAO.getQValue(from.getId(), actionId);
        double newValue;

        if (end) {
            newValue = oldValue + learningRate * (reward - oldValue);
        } else {
            //possible error: highest Q value could have been different compared to when the action was selected with qValuesDAO.getBestAction
            newValue = oldValue + learningRate * (reward + discountFactor * qValuesDAO.getHighestQValue(to.getId()) - oldValue);
        }

        qValuesDAO.updateQValue(newValue, from.getId(), actionId);
        movesDAO.saveMove(gameId, moveCounter, from.getId(), actionId, to.getId(), reward, nextAction.isRand(), nextAction.getTrajectoryType().name());

    }


    @Override
    protected void afterShotHook(ProblemState previousProblemState) {
        if (currentGameStateEnum == GameStateExtractor.GameStateEnum.PLAYING) {
            updateCurrentProblemState();
            updateQValue(previousProblemState, currentProblemState, currentAction, currentReward, false, currentGameId, currentMoveCounter);
        } else if (currentGameStateEnum == GameStateExtractor.GameStateEnum.WON || currentGameStateEnum == GameStateExtractor.GameStateEnum.LOST) {
            updateQValue(previousProblemState, currentProblemState, currentAction, currentReward, true, currentGameId, currentMoveCounter);
        }
    }

    /**
     * Returns next action, with explorationrate as probability of taking a random action
     * and else look for the so far best action
     *
     * @return
     */
    protected void calculateNextAction() {
        int randomValue = randomGenerator.nextInt(100);
        Action action;
        if (randomValue < explorationRate * 100) {
            //get random action should return more than one id!
            action = qValuesDAO.getRandomAction(currentProblemState.getId());
            action.setProblemState(currentProblemState);
            action.setRand(true);
            action.setName("random_" + action.getTrajectoryType().name() + "_" + action.getTargetObjectString());
        } else {
            action = qValuesDAO.getBestAction(currentProblemState.getId());
            action.setProblemState(currentProblemState);
            action.setRand(false);
            action.setName("best_" + action.getTrajectoryType().name() + "_" + action.getTargetObjectString());
        }
        logger.info("Selected the following action: " + action.getName());
        currentAction = action;
    }

    protected void updateCurrentProblemState() {
        // 1. create state
        ProblemState problemState = getStateId(new ProblemState(currentVision, actionRobot, 0, false));

        // 2. if state was generated newly create all Objects and link them to this state
        if (problemState.isInitialized() == false) {
            int objectId;
            int stateId = problemState.getId();
            for (ABObject object : problemState.getAllObjects()) {
                objectId = objectsDAO.insertObject((int) object.getCenterX() / 10,
                        (int) object.getCenterX() / 10,
                        String.valueOf(object.getType()),
                        String.valueOf(object.shape));
                statesDAO.insertState(stateId, objectId);
            }

            // 3. Generate actions in q_values if we have no actions initialised yet
            try {
                this.insertsPossibleActionsForProblemStateIntoDatabase(problemState);
                problemState.setInitialized(true);
            } catch (NullPointerException e) {
                logger.error("NullPointer in insertsPossibleActionsForProblemStateIntoDatabase (wrong bird amount counted, better restart level) " + e);
                //can we do this here or is it to hard?
                restartThisLevel();
            }
        }
        this.currentProblemState = problemState;
    }
}
