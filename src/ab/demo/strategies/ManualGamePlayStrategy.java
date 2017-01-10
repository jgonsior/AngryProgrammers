package ab.demo.strategies;

import ab.demo.other.ProblemState;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Scanner;

/**
 * @author: Julius Gonsior
 */
public class ManualGamePlayStrategy extends Strategy {

    private static final Logger logger = Logger.getLogger(ManualGamePlayStrategy.class);

    /**
     * Returns next action, with explorationrate as probability of taking a random action
     * and else look for the so far best action
     *
     * @return
     */
    public Action getNextAction() {
        ArrayList<Action> possibleActions = gameState.getProblemState().getActions();
        int actionId = Integer.MAX_VALUE;

        while (actionId > possibleActions.size()) {
            for (int i = 0; i < possibleActions.size(); i++) {
                Action possibleAction = possibleActions.get(i);
                System.out.println("(" + i + ")\t" + possibleAction.getName());
            }
            System.out.println("Enter the actionId you want to shoot at: ");
            Scanner input = new Scanner(System.in);
            actionId = input.nextInt();
            input.nextLine();
        }

        Action action = possibleActions.get(actionId);
        logger.info("You selected the following action: " + action.getName());
        return action;
    }

    @Override
    public void afterShotHook(ProblemState previousProblemState) {

    }
}
