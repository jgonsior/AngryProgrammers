package ab.demo.other;

import ab.planner.TrajectoryPlanner;
import ab.vision.ABObject;
import ab.vision.ABShape;
import ab.vision.ABType;
import ab.vision.Vision;
import ab.vision.real.shape.Circle;
import ab.vision.real.shape.Poly;
import org.apache.log4j.Logger;

import java.awt.*;
import java.awt.geom.Area;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents the current state of the game using the coordinates of the birds, the pigs and all found objects and their type
 *
 * @author jgonsior
 */
public class ProblemState {

    private static final Logger logger = Logger.getLogger(ProblemState.class);

    private final int MIN_PIXEL_OVERLAP = 1;
    private List<Action> possibleActions;
    private List<ABObject> allObjects;
    private int id;
    private Vision vision;
    private Rectangle slingshot;
    private ABObject birdOnSlingshot;
    private List<ABObject> pigs;
    private List<ABObject> birds;

    public ProblemState() {
        vision = GameState.getVision();

        findSlingshot();

        birds = new ArrayList<>(vision.findBirdsRealShape());
        findBirdOnSlingshot();

        allObjects = new ArrayList<>();
        pigs = new ArrayList<>(vision.findPigsRealShape());

        allObjects.addAll(vision.findBirdsRealShape());
        allObjects.addAll(vision.findBlocksRealShape());
        allObjects.addAll(vision.findPigsRealShape());
        allObjects.addAll(vision.findHills());
        allObjects.addAll(vision.findTNTs());

        possibleActions = calculatePossibleActions();


    }

    public Rectangle getSlingshot() {
        return slingshot;
    }

    private ABObject getBirdOnSlingshot() {
        return birdOnSlingshot;
    }

    private void findBirdOnSlingshot() {
        int maxY = 0;
        ABObject currentBird = null;
        for (ABObject bird : birds) {
            if (bird.y > maxY) {
                maxY = bird.y;
                currentBird = bird;
            }
        }
        birdOnSlingshot = currentBird;
    }

    private void findSlingshot() {
        slingshot = vision.findSlingshotMBR();

        // confirm the slingshot
        while (slingshot == null) {
            logger.warn("No slingshot detected. Please remove pop up or zoom out");
            ActionRobot.fullyZoomOut();
            GameState.updateCurrentVision();
            slingshot = vision.findSlingshotMBR();
            ActionRobot.skipPopUp();
        }
    }

    public List<Action> getPossibleActions() {
        return possibleActions;
    }

    private List<ABObject> getObjectsOnTrajectory(List<Point> predictedTrajectory) {
        ArrayList<ABObject> objectsOnTrajectory = new ArrayList<>();
        Circle currentBird = (Circle) getBirdOnSlingshot();

        for (ABObject object : allObjects) {
            for (Point p : predictedTrajectory) {
                if (p.x < 840 && p.y < 480 && p.y > 100 && p.x > 400) {
                    //create new circle object with the size of the currentBird we're going to shoot with and
                    //compute if that new circle object intersects with the object
                    if (intersects(new Circle(p.x, p.y, currentBird.r, currentBird.getType()), object)) {
                        //set coordinates of object on trajectory to the coordinates of the trajectory point, but only
                        //if it isn't a bird object
                        //why?!
                        if (!birds.contains(object)) {
                            object.setCoordinates(p.x, p.y);
                            objectsOnTrajectory.add(object);
                        }
                        // object intersects so dont need to check rest of points
                        break;
                    }
                }
            }
        }
        return objectsOnTrajectory;
    }

    private List<ABObject> getObjectsOnTrajectoryUntilTargetPoint(List<Point> predictedTrajectory, ABObject targetObject) {
        ArrayList<ABObject> objectsOnTrajectory = new ArrayList<>();
        Circle currentBird = (Circle) getBirdOnSlingshot();

        for (ABObject object : allObjects) {
            //prevent the object from being added to the output list itself
            if (object.x != targetObject.x && object.y != targetObject.y) {
                for (Point p : predictedTrajectory) {
                    //check only objects in the area we can shoot with objects at
                    if (p.x < 840 && p.y < 480 && p.y > 100 && p.x > 400) {
                        if (intersects(new Circle(p.x, p.y, currentBird.r - MIN_PIXEL_OVERLAP, currentBird.getType()), object)) {
                            objectsOnTrajectory.add(object);
                            break;
                        }
                    }
                }
            }
        }

        return objectsOnTrajectory;
    }


    private List<Point> generatePointsAroundTargets(List<ABObject> targets, int birdRadius) {
        ArrayList<Point> possibleTargetPoints = new ArrayList<>();
        for (ABObject target : targets) {
            int targetRadius = (int) ((Circle) target).r;
            int fromTo = targetRadius + birdRadius - MIN_PIXEL_OVERLAP;
            for (int xoff = -fromTo; xoff < fromTo; xoff += 2) {
                for (int yoff = -fromTo; yoff < fromTo; yoff += 2) {
                    possibleTargetPoints.add(new Point((int) (target.getCenterX() + xoff), (int) (target.getCenterY() + yoff)));
                }
            }
        }
        return possibleTargetPoints;
    }

    private Action calculateBestMultiplePigShot() {
        // idea: try to find shot which maximize pigs on the trajectory
        // -> : just search around every pig for shot and check his trajectories
        TrajectoryPlanner tp = GameState.getTrajectoryPlanner();
        ABObject currentBird = getBirdOnSlingshot();
        int birdRadius = (int) ((Circle) currentBird).r;

        ArrayList<Point> possibleTargetPoints = new ArrayList<>(generatePointsAroundTargets(pigs, birdRadius));

        Point bestShot = null;
        ABObject.TrajectoryType bestTrajType = null;
        int maxAmountOfPigsOnTraj = -1;
        int safeAmountOfPigsOnTraj = -1;
        ABObject leftMostObject = null;
        // TODO: maybe also use better slingshot finding function

        for (Point ptp : possibleTargetPoints) {
            //get predicted trajectory and check for every object if it gets hit
            ArrayList<Point> estimatedLaunchPoints = tp.estimateLaunchPoint(slingshot, ptp);
            ABObject.TrajectoryType currentTrajectoryType;

            for (int i = 0; i < estimatedLaunchPoints.size(); i++) {
                Point estimatedLaunchPoint = estimatedLaunchPoints.get(i);
                if (i == 0) {
                    currentTrajectoryType = ABObject.TrajectoryType.LOW;
                } else {
                    currentTrajectoryType = ABObject.TrajectoryType.HIGH;
                }

                ArrayList<Point> predictedTrajectory = new ArrayList<>(tp.predictTrajectory(slingshot, estimatedLaunchPoint));

                ArrayList<ABObject> pigsOnTraj, objsOnTraj, correctedPigs, allObjsOnTraj;
                ABObject currentLeftMostObject = null;

                allObjsOnTraj = new ArrayList<>(this.getObjectsOnTrajectory(predictedTrajectory));
                objsOnTraj = new ArrayList<>();
                pigsOnTraj = new ArrayList<>();
                correctedPigs = new ArrayList<>();
                int minX = 10000;

                // now we know which objects intersect with the trajectory,
                // now check which pigs would be hit behind 1st object
                // get MinX Value and remove all pigs behind this x

                for (ABObject obj : allObjsOnTraj) {
                    if (obj.getType() == ABType.Pig) {
                        pigsOnTraj.add(obj);
                    } else {
                        objsOnTraj.add(obj);
                        if (obj.movedX < minX) {
                            minX = obj.movedX;
                            currentLeftMostObject = obj;
                        }
                    }
                }

                for (ABObject pig : pigsOnTraj) {
                    if (pig.x <= minX) {
                        correctedPigs.add(pig);
                        currentLeftMostObject = pig;
                    }
                }

                // now we got the corrected value of pigs on this trajectory, now return the best?!
                if (correctedPigs.size() >= safeAmountOfPigsOnTraj) {
                    safeAmountOfPigsOnTraj = correctedPigs.size();
                    maxAmountOfPigsOnTraj = pigsOnTraj.size();
                    bestTrajType = currentTrajectoryType;
                    bestShot = ptp;
                    leftMostObject = currentLeftMostObject;
                }
            }
        }

        ABObject pseudoObject = new ABObject(new Rectangle(bestShot), ABType.BestMultiplePigShot);
        pseudoObject.setPigsOnTraj(safeAmountOfPigsOnTraj, maxAmountOfPigsOnTraj);
        Set<ABObject> leftMostObjectSet = new HashSet<ABObject>();
        leftMostObjectSet.add(leftMostObject);
        pseudoObject.setObjectsLeftSet(leftMostObjectSet);
        System.out.println("Best shot: " + bestShot + " - " + bestTrajType + " will kill approx: " + safeAmountOfPigsOnTraj + " to " + maxAmountOfPigsOnTraj);

        return new Action(pseudoObject, bestTrajType);
    }


    private boolean intersects(Circle circle, ABObject target) {
        Area area;
        if (target.shape == ABShape.Poly) {
            area = new Area(((Poly) target).polygon);
        } else {
            area = new Area(target);
        }
        area.intersect(new Area(circle));
        return !area.isEmpty();
    }

    private double calculateDistanceToPig(ABObject object) {
        double pigX = 0, pigY = 0, distanceToPigs;

        double pigCount = (double) vision.findPigsMBR().size();
        for (ABObject pig : vision.findPigsMBR()) {
            pigX += pig.getCenterX();
            pigY += pig.getCenterY();
        }
        pigX = pigX / pigCount;
        pigY = pigY / pigCount;

        return Math.sqrt(Math.pow((Math.abs(object.getCenterX() - pigX)), 2) + Math.pow((Math.abs(object.getCenterY() - pigY)), 2));

    }

    private ArrayList<Action> getScoredStructuralObjects() {
        ArrayList<Action> result = new ArrayList<>();

        //finds all objects in the scene beside slingshot, birds, pigs. and hills.
        List<ABObject> blocksRealShapeSorted = vision.findBlocksRealShape();

        blocksRealShapeSorted.addAll(vision.findTNTs());

        //sorted descending after Y coordinates, so first object to iterate over is the highest one
        blocksRealShapeSorted.sort((o1, o2) -> o1.y - o2.y);

        double minObjectX, maxObjectX, minObjectY, maxObjectY;
        int objectsLeftCount, objectsRightCount, objectsBelowCount;
        Set<ABObject> objectsAbove = new HashSet<>();

        for (ABObject object : blocksRealShapeSorted) {
            objectsLeftCount = 0;
            objectsRightCount = 0;
            objectsBelowCount = 0;

            minObjectX = object.x;
            maxObjectX = object.x + object.width;
            minObjectY = object.y;
            maxObjectY = object.y + object.height;

            double minNeighborX, maxNeighborX, minNeighborY, maxNeighborY;
            boolean nearX, nearY;
            for (ABObject neighbor : vision.findBlocksMBR()) {
                minNeighborX = neighbor.x;
                maxNeighborX = neighbor.x + neighbor.width;
                minNeighborY = neighbor.y;
                maxNeighborY = neighbor.y + neighbor.height;

                nearX = false;
                nearY = false;

                if ((minObjectX >= minNeighborX - 10 && minObjectX <= maxNeighborX + 10)
                        || (minNeighborX >= minObjectX - 10 && minNeighborX <= maxObjectX + 10)) {
                    nearX = true;
                }

                if ((minObjectY >= minNeighborY - 10 && minObjectY <= maxNeighborY + 10)
                        || (minNeighborY >= minObjectY - 10 && minNeighborY <= maxObjectY + 10)) {
                    nearY = true;
                }

                if (nearX && maxNeighborY <= minObjectY) {
                    objectsAbove.addAll(neighbor.getObjectsAboveSet());
                } else if (nearY && maxNeighborX <= minObjectX && minObjectX - maxNeighborX < 20) {
                    objectsLeftCount++;
                } else if (nearY && minNeighborX >= maxObjectX && minNeighborX - maxObjectX < 20) {
                    objectsRightCount++;
                } else if (nearX && maxNeighborY > minObjectY) {
                    objectsBelowCount++;
                }
            }


            object.setObjectsAboveSet(objectsAbove);

            for (int i = 0; i < 2; i++) {
                ABObject.TrajectoryType trajType;
                if (i == 0) {
                    trajType = ABObject.TrajectoryType.HIGH;
                } else {
                    trajType = ABObject.TrajectoryType.LOW;
                }

                Point targetPoint = object.getCenter();
                Point releasePoint = calculateReleasePoint(targetPoint, trajType);
                if (releasePoint == null) {
                    continue;
                }
                List<Point> trajectoryPoints = GameState.getTrajectoryPlanner().predictTrajectory(slingshot, releasePoint);

                trajectoryPoints.removeIf(point -> (point.x >= object.getCenterX() /*&& point.y >= object.getMinY()*/));

                List<ABObject> objectsOnTrajectory = getObjectsOnTrajectoryUntilTargetPoint(trajectoryPoints, object);

                //ScreenshotUtil.saveTrajectoryScreenshot(slingshot, releasePoint, object, objectsOnTrajectory);

                objectsLeftCount = objectsOnTrajectory.size();
                object.setObjectsLeftSet(new HashSet<>(objectsOnTrajectory));
                object.setObjectsAround(object.getObjectsAboveSet().size(), objectsLeftCount, objectsRightCount, objectsBelowCount, calculateDistanceToPig(object));
                Action action = new Action(object, trajType);
                action.setScore(calculateScore(object, action));
                result.add(action);
            }
        }
        return normalizeActions(result);
    }

    private ArrayList<Action> normalizeActions(ArrayList<Action> actions) {
        //List<Action> normalizedActions = new ArrayList<>();
        double maxScore = -1000;
        // find highest score
        for (Action action : actions) {
            if (maxScore < action.getScore()) {
                maxScore = action.getScore();
            }
        }
        // normalization
        for (Action action : actions) {
            action.setScore(100 * action.getScore() / maxScore);
            //normalizedActions
        }
        return actions;
    }

    private double calculateScore(ABObject targetObject, Action action) {
        double score;

        //todo: maybe rethink this values
        int orientationOffset = 0;
        int typeOffset = 0;
        if (targetObject.shape == ABShape.Rect && targetObject.width != targetObject.height) {
            // get orientation if its not quadratic
            if (targetObject.angle > 45 && targetObject.angle < 135) {
                // vertical
                if (action.getTrajectoryType() == ABObject.TrajectoryType.HIGH) {
                    orientationOffset = -1;
                } else {
                    orientationOffset = 3;
                }
            } else {
                // horizontal
                if (action.getTrajectoryType() == ABObject.TrajectoryType.LOW) {
                    orientationOffset = -1;
                } else {
                    orientationOffset = 2;
                }
            }
        } else if (action.getTrajectoryType() == ABObject.TrajectoryType.LOW) {
            orientationOffset = 2;
        }

        if (targetObject.getType() == ABType.Stone && birdOnSlingshot.getType() == ABType.BlackBird) {
            typeOffset = 5;
        } else if (targetObject.getType() == ABType.Wood && birdOnSlingshot.getType() == ABType.YellowBird) {
            typeOffset = 3;
        } else if (targetObject.getType() == ABType.Ice && birdOnSlingshot.getType() == ABType.BlueBird) {
            typeOffset = 3;
        }
        double pigDependentFactor;
        if (pigs.size() > 1) {
            pigDependentFactor = 30;
        } else {
            pigDependentFactor = 10;
        }
        double objectScore = (targetObject.objectsAboveCount - targetObject.objectsLeftCount + targetObject.objectsRightCount / 2 + targetObject.objectsBelowCount / 4);
        double distanceMultiplier = (100 - targetObject.distanceToPigs) / pigDependentFactor;

        score = objectScore * distanceMultiplier + orientationOffset + typeOffset;

        return score;
    }

    private List<Action> calculatePossibleActions() {
        List<Action> allPossibleActions = new ArrayList<>();
        List<Action> possibleActions = new ArrayList<>();

        //tnt and big round object is already included
        allPossibleActions.addAll(getScoredStructuralObjects());

        //extract tnt
        possibleActions.addAll(allPossibleActions.stream().filter(
                a -> a.getTargetObject().type == ABType.TNT
        ).collect(Collectors.toList()));

        for (ABObject obj : vision.findBlocksRealShape()) {
            if (obj.shape == ABShape.Circle) {
                // have to see if 9000 is too big
                if (((Circle) obj).r > 9) {
                    logger.debug("found big round object with radius " + ((Circle) obj).r);
                    logger.debug(obj);
                }
            }
        }


        //extract round objects
        possibleActions.addAll(allPossibleActions.stream().filter(
                a -> a.getTargetObject().shape == ABShape.Circle && ((Circle) a.getTargetObject()).r > 9
        ).collect(Collectors.toList()));

        // pre select the five best possibleActions
        allPossibleActions.sort((o1, o2) -> Double.compare(o1.getScore(), o2.getScore()));

        possibleActions.addAll(allPossibleActions.subList(allPossibleActions.size() - 5, allPossibleActions.size()));

        possibleActions.add(calculateBestMultiplePigShot());

        return possibleActions;
    }

    /**
     * Returns a deserialized problem state representation,
     * gets all objects from left upper corner to right lower corner.
     *
     * @return the problem state deserialized into a string
     */
    public String toString() {
        String string = "";
        for (ABObject object : allObjects) {
            string += " " + object.getType()
                    + " " + String.valueOf(((int) object.getCenterX()) / 10)
                    + " " + String.valueOf(((int) object.getCenterY()) / 10)
                    + " " + String.valueOf(object.shape);
        }
        return string;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Point calculateReleasePoint(Point targetPoint, ABObject.TrajectoryType trajectoryType) {
        Point releasePoint = null;
        // estimate the trajectory

        ArrayList<Point> estimateLaunchPoints = GameState.getTrajectoryPlanner().estimateLaunchPoint(getSlingshot(), targetPoint);

        // do a high shot when entering a level to find an accurate velocity
        if (estimateLaunchPoints.size() == 1) {
            if (trajectoryType != ABObject.TrajectoryType.LOW) {
                logger.error("Somehow there was only one launch point found and therefore we can only do a LOW shot, eventhough a HIGH shot was being requested.");
                return null;
            }
            releasePoint = estimateLaunchPoints.get(0);
        } else if (estimateLaunchPoints.size() == 2) {
            if (trajectoryType == ABObject.TrajectoryType.HIGH) {
                releasePoint = estimateLaunchPoints.get(1);
            } else if (trajectoryType == ABObject.TrajectoryType.LOW) {
                releasePoint = estimateLaunchPoints.get(0);
            }
        } else if (estimateLaunchPoints.isEmpty()) {
            logger.info("No release point found for the target");
            logger.info("Try a shot with 45 degree");
        }
        return releasePoint;
    }

}