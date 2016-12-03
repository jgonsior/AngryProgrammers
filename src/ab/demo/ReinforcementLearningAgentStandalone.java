package ab.demo;

import ab.demo.other.ActionRobot;
import ab.demo.other.Shot;
import ab.demo.qlearning.QValuesDAO;
import ab.planner.TrajectoryPlanner;
import ab.utils.StateUtil;
import ab.vision.ABObject;
import ab.vision.GameStateExtractor;
import ab.vision.Vision;
import org.apache.log4j.Logger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * @author jgonsior
 */
public class ReinforcementLearningAgentStandalone implements Runnable {

    public static int time_limit = 12;
    private static Logger logger = Logger.getLogger(ReinforcementLearningAgentClient.class);
    private int currentLevel = -1;
    private int failedCounter = 0;
    private int[] solved;
    private TrajectoryPlanner trajectoryPlanner;
    //Wrapper of the communicating messages
    private ActionRobot actionRobot;
    private double discountFactor = 0.9;
    private double learningRate = 0.1;
    private double explorationRate = 0.3;
    private int id = 28888;
    private boolean firstShot;
    private Point prevTarget;
    private Random randomGenerator;
    private QValuesDAO qValuesDAO;
    private String dbUser;
    private String dbPath;
    private String dbPass;
    private String dbName;
    private GameStateExtractor gameStateExtractor;
    private Map<Integer, Integer> scores = new LinkedHashMap<Integer, Integer>();

    // a standalone implementation of the Naive Agent
    public ReinforcementLearningAgentStandalone() {

        actionRobot = new ActionRobot();
        trajectoryPlanner = new TrajectoryPlanner();
        prevTarget = null;
        firstShot = true;
        randomGenerator = new Random();
        // --- go to the Poached Eggs episode level selection page ---
        ActionRobot.GoFromMainMenuToLevelSelection();

    }

    public static void main(String args[]) {

        NaiveAgent na = new NaiveAgent();
        if (args.length > 0)
            na.currentLevel = Integer.parseInt(args[0]);
        na.run();

    }

    // run the client
    public void run() {

        actionRobot.loadLevel(currentLevel);
        while (true) {
            GameStateExtractor.GameState state = solve();
            if (state == GameStateExtractor.GameState.WON) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int score = StateUtil.getScore(ActionRobot.proxy);
                if (!scores.containsKey(currentLevel))
                    scores.put(currentLevel, score);
                else {
                    if (scores.get(currentLevel) < score)
                        scores.put(currentLevel, score);
                }
                int totalScore = 0;
                for (Integer key : scores.keySet()) {

                    totalScore += scores.get(key);
                    System.out.println(" Level " + key
                            + " Score: " + scores.get(key) + " ");
                }
                System.out.println("Total Score: " + totalScore);
                actionRobot.loadLevel(++currentLevel);
                // make a new trajectory planner whenever a new level is entered
                trajectoryPlanner = new TrajectoryPlanner();

                // first shot on this level, try high shot first
                firstShot = true;
            } else if (state == GameStateExtractor.GameState.LOST) {
                System.out.println("Restart");
                actionRobot.restartLevel();
            } else if (state == GameStateExtractor.GameState.LEVEL_SELECTION) {
                System.out
                        .println("Unexpected level selection page, go to the last current level : "
                                + currentLevel);
                actionRobot.loadLevel(currentLevel);
            } else if (state == GameStateExtractor.GameState.MAIN_MENU) {
                System.out
                        .println("Unexpected main menu page, go to the last current level : "
                                + currentLevel);
                ActionRobot.GoFromMainMenuToLevelSelection();
                actionRobot.loadLevel(currentLevel);
            } else if (state == GameStateExtractor.GameState.EPISODE_MENU) {
                System.out
                        .println("Unexpected episode menu page, go to the last current level : "
                                + currentLevel);
                ActionRobot.GoFromMainMenuToLevelSelection();
                actionRobot.loadLevel(currentLevel);
            }

        }

    }

    private double distance(Point p1, Point p2) {
        return Math
                .sqrt((double) ((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y)
                        * (p1.y - p2.y)));
    }

    public GameStateExtractor.GameState solve() {

        // capture Image
        BufferedImage screenshot = ActionRobot.doScreenShot();

        // process image
        Vision vision = new Vision(screenshot);

        // find the slingshot
        Rectangle sling = vision.findSlingshotMBR();

        // confirm the slingshot
        while (sling == null && actionRobot.getState() == GameStateExtractor.GameState.PLAYING) {
            System.out
                    .println("No slingshot detected. Please remove pop up or zoom out");
            ActionRobot.fullyZoomOut();
            screenshot = ActionRobot.doScreenShot();
            vision = new Vision(screenshot);
            sling = vision.findSlingshotMBR();
        }
        // get all the pigs
        java.util.List<ABObject> pigs = vision.findPigsMBR();

        GameStateExtractor.GameState state = actionRobot.getState();

        // if there is a sling, then play, otherwise just skip.
        if (sling != null) {

            if (!pigs.isEmpty()) {

                Point releasePoint = null;
                Shot shot = new Shot();
                int dx, dy;
                {
                    // random pick up a pig
                    ABObject pig = pigs.get(randomGenerator.nextInt(pigs.size()));

                    Point _tpt = pig.getCenter();// if the target is very close to before, randomly choose a
                    // point near it
                    if (prevTarget != null && distance(prevTarget, _tpt) < 10) {
                        double _angle = randomGenerator.nextDouble() * Math.PI * 2;
                        _tpt.x = _tpt.x + (int) (Math.cos(_angle) * 10);
                        _tpt.y = _tpt.y + (int) (Math.sin(_angle) * 10);
                        System.out.println("Randomly changing to " + _tpt);
                    }

                    prevTarget = new Point(_tpt.x, _tpt.y);

                    // estimate the trajectory
                    ArrayList<Point> pts = trajectoryPlanner.estimateLaunchPoint(sling, _tpt);

                    // do a high shot when entering a level to find an accurate velocity
                    if (firstShot && pts.size() > 1) {
                        releasePoint = pts.get(1);
                    } else if (pts.size() == 1)
                        releasePoint = pts.get(0);
                    else if (pts.size() == 2) {
                        // randomly choose between the trajectories, with a 1 in
                        // 6 chance of choosing the high one
                        if (randomGenerator.nextInt(6) == 0)
                            releasePoint = pts.get(1);
                        else
                            releasePoint = pts.get(0);
                    } else if (pts.isEmpty()) {
                        System.out.println("No release point found for the target");
                        System.out.println("Try a shot with 45 degree");
                        releasePoint = trajectoryPlanner.findReleasePoint(sling, Math.PI / 4);
                    }

                    // Get the reference point
                    Point refPoint = trajectoryPlanner.getReferencePoint(sling);


                    //Calculate the tapping time according the bird type
                    if (releasePoint != null) {
                        double releaseAngle = trajectoryPlanner.getReleaseAngle(sling,
                                releasePoint);
                        System.out.println("Release Point: " + releasePoint);
                        System.out.println("Release Angle: "
                                + Math.toDegrees(releaseAngle));
                        int tapInterval = 0;
                        switch (actionRobot.getBirdTypeOnSling()) {

                            case RedBird:
                                tapInterval = 0;
                                break;               // start of trajectory
                            case YellowBird:
                                tapInterval = 65 + randomGenerator.nextInt(25);
                                break; // 65-90% of the way
                            case WhiteBird:
                                tapInterval = 70 + randomGenerator.nextInt(20);
                                break; // 70-90% of the way
                            case BlackBird:
                                tapInterval = 70 + randomGenerator.nextInt(20);
                                break; // 70-90% of the way
                            case BlueBird:
                                tapInterval = 65 + randomGenerator.nextInt(20);
                                break; // 65-85% of the way
                            default:
                                tapInterval = 60;
                        }

                        int tapTime = trajectoryPlanner.getTapTime(sling, releasePoint, _tpt, tapInterval);
                        dx = (int) releasePoint.getX() - refPoint.x;
                        dy = (int) releasePoint.getY() - refPoint.y;
                        shot = new Shot(refPoint.x, refPoint.y, dx, dy, 0, tapTime);
                    } else {
                        System.err.println("No Release Point Found");
                        return state;
                    }
                }

                // check whether the slingshot is changed. the change of the slingshot indicates a change in the scale.
                {
                    ActionRobot.fullyZoomOut();
                    screenshot = ActionRobot.doScreenShot();
                    vision = new Vision(screenshot);
                    Rectangle _sling = vision.findSlingshotMBR();
                    if (_sling != null) {
                        double scale_diff = Math.pow((sling.width - _sling.width), 2) + Math.pow((sling.height - _sling.height), 2);
                        if (scale_diff < 25) {
                            if (dx < 0) {
                                actionRobot.cshoot(shot);
                                state = actionRobot.getState();
                                if (state == GameStateExtractor.GameState.PLAYING) {
                                    screenshot = ActionRobot.doScreenShot();
                                    vision = new Vision(screenshot);
                                    java.util.List<Point> traj = vision.findTrajPoints();
                                    trajectoryPlanner.adjustTrajectory(traj, sling, releasePoint);
                                    firstShot = false;
                                }
                            }
                        } else
                            System.out.println("Scale is changed, can not execute the shot, will re-segement the image");
                    } else
                        System.out.println("no sling detected, can not execute the shot, will re-segement the image");
                }

            }

        }
        return state;
    }
}
