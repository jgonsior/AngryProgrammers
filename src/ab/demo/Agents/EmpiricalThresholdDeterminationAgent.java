package ab.demo.Agents;

import ab.demo.Action;
import ab.demo.ProblemState;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * @author: Julius Gonsior
 */
public class EmpiricalThresholdDeterminationAgent extends Agent {
    private static final Logger logger = Logger.getLogger(EmpiricalThresholdDeterminationAgent.class);

    @Override
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
        ArrayList<Action> possibleActions = currentProblemState.getActions();
        for (int i = 0; i < possibleActions.size(); i++) {
            Action possibleAction = possibleActions.get(i);
            System.out.println("(" + i + ")\t" + possibleAction.getName());
        }
        System.out.println("Enter the actionId you want to shoot at: ");
        Scanner input = new Scanner(System.in);
        int actionId = input.nextInt();
        input.nextLine();

        Action action = possibleActions.get(actionId);
        logger.info("You selected the following action: " + action.getName());
        currentAction = action;
    }
}
