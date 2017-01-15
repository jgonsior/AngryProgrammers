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
import java.util.*;
import java.util.List;

/**
 * Represents the current state of the game using the coordinates of the birds, the pigs and all found objects and their type
 *
 * @author jgonsior
 */
public class ProblemState {

    protected static final Logger logger = Logger.getLogger(ProblemState.class);
    private final int MIN_PIXEL_OVERLAP = 3;
    private ArrayList<ABObject> targetObjects;
    private List<ABObject> allObjects;
    private int id;
    private Vision vision;
    private Rectangle slingshot;
    private ABObject birdOnSlingshot;
    private List<ABObject> pigs;
    private List<ABObject> birds;

    public ProblemState(int id) {
        vision = GameState.getVision();

        findSlingshot();
        findBirdOnSlingshot();

        allObjects = new ArrayList<>();
        pigs = new ArrayList<>(vision.findPigsRealShape());
        birds = new ArrayList<>(vision.findBirdsRealShape());

        this.id = id;

        allObjects.addAll(vision.findBirdsRealShape());
        allObjects.addAll(vision.findBlocksRealShape());
        allObjects.addAll(vision.findPigsRealShape());
        allObjects.addAll(vision.findHills());
        allObjects.addAll(vision.findTNTs());

        targetObjects = calculateTargetObjects();



    }

    public Rectangle getSlingshot() {
        return slingshot;
    }

    private ABObject getBirdOnSlingshot() {
        return birdOnSlingshot;
    }

    private void findBirdOnSlingshot() {
        ArrayList<ABObject> birds = new ArrayList<>(GameState.getVision().findBirdsRealShape());
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

    public ArrayList<ABObject> getTargetObjects() {
        return targetObjects;
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

    private ABObject calculateBestMultiplePigShot() {
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

                ArrayList<Point> predictedTrajectory = new ArrayList<>(tp.predictTrajectory(vision.findSlingshotMBR(), estimatedLaunchPoint));

                ArrayList<ABObject> pigsOnTraj, objsOnTraj, correctedPigs, allObjsOnTraj;

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
                        }
                    }
                }

                for (ABObject pig : pigsOnTraj) {
                    if (pig.x <= minX) {
                        correctedPigs.add(pig);
                    }
                }

                // now we got the corrected value of pigs on this trajectory, now return the best?!
                if (correctedPigs.size() > safeAmountOfPigsOnTraj) {
                    safeAmountOfPigsOnTraj = correctedPigs.size();
                    maxAmountOfPigsOnTraj = pigsOnTraj.size();
                    bestTrajType = currentTrajectoryType;
                    bestShot = ptp;
                    System.out.println(bestShot + " : " + minX + " - " + allObjsOnTraj);
                    System.out.println(pigsOnTraj + " : " + objsOnTraj + " : " + correctedPigs);

                }
            }
        }

        ABObject pseudoObject = new ABObject(new Rectangle(bestShot), ABType.BestMultiplePigShot);
        pseudoObject.setTrajectoryType(bestTrajType);
        pseudoObject.setPigsOnTraj(safeAmountOfPigsOnTraj, maxAmountOfPigsOnTraj);
        System.out.println("Best shot: " + bestShot + " - " + bestTrajType + " will kill approx: " + safeAmountOfPigsOnTraj + " to " + maxAmountOfPigsOnTraj);

        return pseudoObject;
    }

    /**
     * Checks if Circle object intersects with min PixelOverlap the target object
     *
     * @param circle
     * @param target
     * @return
     */
    private boolean intersects(Circle circle, ABObject target) {
        int circleDistance_x = Math.abs(circle.x - target.x);
        int circleDistance_y = Math.abs(circle.y - target.y);

        if (target.shape == ABShape.Circle) {
            Circle circle2 = (Circle) target;

            if ((circleDistance_x - circle.r - circle2.r - MIN_PIXEL_OVERLAP <= 0) && (circleDistance_y - circle.r - circle2.r - MIN_PIXEL_OVERLAP <= 0)) {
                return true;
            } else {
                return false;
            }
        } else if (target.shape == ABShape.Poly) {
            // pseudo check for some points from bird
            Polygon polygon = ((Poly) target).polygon;

            if (polygon.contains(new Point((int) (circle.x + circle.r), circle.y))) {
                return true;
            } else if (polygon.contains(new Point(circle.x, (int) (circle.y + circle.r)))) {
                return true;
            } else {
                return false;
            }
        } else {
            //ABUtil black magic, works not in every lvl, but in some lvls it finds real intersections which this function not finds
            /*Point p = new Point(circle.x, circle.y);
            if (((target.contains(p) && !target.contains(ptp)) || Math.abs(mbrVision._scene[p.y][p.x] - 72) < 10) && p.x < ptp.x) {
                return true;
            }*/
            //Rect
            ABObject rect = target;
            if (circleDistance_x + MIN_PIXEL_OVERLAP > (rect.width / 2 + circle.r)) {
                return false;
            }
            if (circleDistance_y + MIN_PIXEL_OVERLAP > (rect.height / 2 + circle.r)) {
                return false;
            }

            if (circleDistance_x + MIN_PIXEL_OVERLAP <= (rect.width / 2)) {
                return true;
            }
            if (circleDistance_y + MIN_PIXEL_OVERLAP <= (rect.height / 2)) {
                return true;
            }

            int cornerDistance_sq = (circleDistance_x - rect.width / 2) ^ 2 +
                    (circleDistance_y - rect.height / 2) ^ 2;

            if (((cornerDistance_sq + MIN_PIXEL_OVERLAP) <= (Math.pow(circle.r, 2)))) {
                return true;
            } else {
                return false;
            }
        }
    }

    private ArrayList<ABObject> getBigRoundObjects() {
        ArrayList<ABObject> result = new ArrayList<>();
        for (ABObject obj : vision.findBlocksRealShape()) {
            if (obj.shape == ABShape.Circle) {
                Circle objC = (Circle) obj;
                // have to see if 9000 is too big
                if (objC.r > 9000) {
                    System.out.println("-" + obj.toString());
                    result.add(obj);
                }
            }
        }
        return result;
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

    private ArrayList<ABObject> getScoredStructuralObjects() {
        ArrayList<ABObject> result = new ArrayList<>();

        List<ABObject> blocksRealShapeSorted = vision.findBlocksRealShape();

        //sorted descending after Y coordinates, so first object to iterate over is the highest one
        Collections.sort(blocksRealShapeSorted);

        double minObjectX, maxObjectX, minObjectY, maxObjectY;
        int objectsLeftCount, objectsRightCount;
        Set<ABObject> objectsAbove = new HashSet<>();

        for (ABObject object : blocksRealShapeSorted) {
            objectsLeftCount = 0;
            objectsRightCount = 0;

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
                }
            }


            object.setObjectsAboveSet(objectsAbove);

            Point releasePoint = calculateReleasePoint(new Point(object.x, object.y), ABObject.TrajectoryType.LOW);
            List<Point> trajectoryPoints = GameState.getTrajectoryPlanner().predictTrajectory(slingshot, releasePoint);

            List<ABObject> objectsOnTrajectory = getObjectsOnTrajectory(trajectoryPoints);
            objectsLeftCount = objectsOnTrajectory.size();

            object.setObjectsAround(object.getObjectsAboveSet().size(), objectsLeftCount, objectsRightCount, calculateDistanceToPig(object));
            result.add(object);
        }
        return result;
    }

    private ArrayList<ABObject> calculateTargetObjects() {
        ArrayList<ABObject> targetObjects = new ArrayList<>();

        targetObjects.addAll(vision.findTNTs());
        targetObjects.addAll(getBigRoundObjects());
        targetObjects.addAll(getScoredStructuralObjects());
        targetObjects.addAll(vision.findPigsRealShape());
        targetObjects.add(calculateBestMultiplePigShot());

        return targetObjects;
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

    public ArrayList<Action> getActions() {
        ArrayList<Action> actions = new ArrayList<>();
        int i = 0;
        for (ABObject targetObject : targetObjects) {
            Action action = new Action(i, targetObject.getTrajectoryType(), this);
            action.setName(targetObject.myToString());
            actions.add(action);
            i++;
        }
        return actions;
    }

    public List<ABObject> getAllObjects() {
        return allObjects;
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
            releasePoint = GameState.getTrajectoryPlanner().findReleasePoint(GameState.getProblemState().getSlingshot(), Math.PI / 4);
        }
        return releasePoint;
    }

}
