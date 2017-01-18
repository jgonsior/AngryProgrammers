package ab.demo.agents;

import ab.demo.DAO.GamesDAO;
import ab.demo.DAO.MovesDAO;
import ab.demo.DAO.ProblemStatesDAO;
import ab.demo.other.Action;
import ab.demo.other.GameState;
import ab.demo.other.ProblemState;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Scanner;

/**
 * @author: Julius Gonsior
 */
public class ManualGamePlayAgent extends StandaloneAgent {

    private static final Logger logger = Logger.getLogger(ManualGamePlayAgent.class);

    public ManualGamePlayAgent(GamesDAO gamesDAO, MovesDAO movesDAO, ProblemStatesDAO problemStatesDAO) {
        super(gamesDAO, movesDAO, problemStatesDAO);
    }

    @Override
    protected void afterShotHook(ProblemState previousProblemState) {

    }

    /**
     * Returns next action, with explorationrate as probability of taking a random action
     * and else look for the so far best action
     *
     * @return
     */
    public Action getNextAction() {
        List<Action> possibleActions = GameState.getProblemState().getPossibleActions();


        int actionId = Integer.MAX_VALUE;
        int maxActionId = 0;
        double maxScore = 0;

        while (actionId > possibleActions.size()) {
            for (int i = 0; i < possibleActions.size(); i++) {
                Action possibleAction = possibleActions.get(i);
                if (possibleAction.getScore() > maxScore) {
                    maxActionId = i;
                    maxScore = possibleAction.getScore();
                }
                System.out.println("(" + i + ")\t" + possibleAction.getName());
            }
            System.out.println("Enter the actionId you want to shoot at: ");
            Scanner input = new Scanner(System.in);
            actionId = input.nextInt();
            //actionId = maxActionId;
            input.nextLine();
        }

        Action action = possibleActions.get(actionId);
        logger.info("You selected the following action: " + action.getName());
        return action;
    }
}
