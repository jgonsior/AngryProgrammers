package ab.demo.Agents;

import ab.demo.Action;
import ab.demo.ProblemState;
import ab.vision.ABObject;
import org.apache.log4j.Logger;

import java.awt.*;

/**
 * @author: Julius Gonsior
 */
public class EmpiricalThresholdDeterminationAgent extends Agent {
    private static final Logger logger = Logger.getLogger(EmpiricalThresholdDeterminationAgent.class);

    @Override
    protected int calculateTappingTime(Point releasePoint, Point targetPoint) {
        return 0;
    }


    protected void calculateCurrentGameId() {
        this.currentGameId = 42;
    }

    protected void restartThisLevel() {
        logger.info("Restart level");
        actionRobot.restartLevel();
        this.calculateCurrentGameId();
        currentMoveCounter = 0;
    }

    @Override
    protected void updateCurrentProblemState() {
        currentProblemState = new ProblemState(currentVision, actionRobot, 42, false);
    }

    @Override
    protected void afterShotHook(ProblemState previousProblemState) {
        //do nothing
    }

    /**
     * Returns next action, with explorationrate as probability of taking a random action
     * and else look for the so far best action
     *
     * @return
     */
    protected void calculateNextAction() {
        int randomValue = randomGenerator.nextInt(100);
        Action action = new Action(5, ABObject.TrajectoryType.HIGH, currentProblemState);
        action.setRand(false);
        action.setName("random_" + action.getTrajectoryType().name() + "_" + action.getTargetObjectString());
        logger.info("Selected the following action: " + action.getName());
        currentAction = action;
    }
}
