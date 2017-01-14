package ab.demo.other;

import ab.planner.TrajectoryPlanner;
import ab.utils.ABUtil;
import ab.vision.*;
import ab.vision.real.shape.Circle;
import ab.vision.real.shape.Poly;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Represents the current state of the game using the coordinates of the birds, the pigs and all found objects and their type
 *
 * @author jgonsior
 */
public class ProblemState {
    public ArrayList<ABObject> shootableObjects;
    public ArrayList<ABObject> targetObjects;
    private List<ABObject> allObjects;
    private int id;

    public ProblemState(ActionRobot actionRobot, int id) {
        GameStateExtractor.GameStateEnum state = actionRobot.getState();
        allObjects = new ArrayList<>();
        Vision vision = GameState.getVision();
        this.id = id;

        if (state == GameStateExtractor.GameStateEnum.PLAYING) {

            allObjects.addAll(vision.findBirdsRealShape());
            allObjects.addAll(vision.findBlocksRealShape());
            allObjects.addAll(vision.findPigsRealShape());
            allObjects.addAll(vision.findHills());
            allObjects.addAll(vision.findTNTs());

            shootableObjects = calculateShootableObjects();
            targetObjects = calculateTargetObjects();
        }
    }


    private ABObject getCurrentBird() {
        ArrayList<ABObject> birds = new ArrayList<>(GameState.getVision().findBirdsRealShape());
        int maxY = 0;
        ABObject currentBird = null;
        for (ABObject bird : birds) {
            if (bird.y > maxY) {
                maxY = bird.y;
                currentBird = bird;
            }
        }
        return currentBird;
    }

    private ABObject calculateBestMultiplePigShot() {
        Vision vision = GameState.getVision();
        // idea: try to find shot which maximize pigs on the trajectory
        // -> : just search around every pig for shot and check this trajectorys
        TrajectoryPlanner tp = new TrajectoryPlanner();
        ArrayList<ABObject> pigs = new ArrayList<>(vision.findPigsRealShape());
        ArrayList<ABObject> birds = new ArrayList<>(vision.findBirdsRealShape());
        ArrayList<Point> possibleTargetPoints = new ArrayList<>();
        ABObject currentBird = getCurrentBird();
        int birdRadius = (int) ((Circle) currentBird).r;
        Point bestShot = null;
        ABObject.TrajectoryType bestTrajType = null;
        int maxAmountOfPigsOnTraj = -1;
        int safeAmountOfPigsOnTraj = -1;
        // identify possible targetPoints
        for (ABObject pig : pigs) {
            int pigRadius = (int) ((Circle) pig).r;
            int fromTo = pigRadius + birdRadius;
            for (int xoff = -fromTo; xoff < fromTo; xoff += 2) {
                for (int yoff = -fromTo; yoff < fromTo; yoff += 2) {
                    possibleTargetPoints.add(new Point((int) (pig.getCenterX() + xoff), (int) (pig.getCenterY() + yoff)));
                }
            }
        }
        // TODO: maybe also use better slingshot finding function
        Rectangle sling = vision.findSlingshotMBR();
        for (Point ptp : possibleTargetPoints) {
            //get predicted trajectory and check for every object if it gets hit
            ArrayList<Point> estimatedLaunchPoints = tp.estimateLaunchPoint(sling, ptp);
            ABObject.TrajectoryType currentTrajectoryType = null;
            for (int i = 0; i < estimatedLaunchPoints.size(); i++) {
                Point estimatedLaunchPoint = estimatedLaunchPoints.get(i);
                if (i == 0) {
                    currentTrajectoryType = ABObject.TrajectoryType.LOW;
                } else {
                    currentTrajectoryType = ABObject.TrajectoryType.HIGH;
                }

                ArrayList<Point> predictedTrajectory = new ArrayList<>(tp.predictTrajectory(vision.findSlingshotMBR(), estimatedLaunchPoint));

                ArrayList<Point> pigsOnTraj, objsOnTraj, correctedPigs;
                pigsOnTraj = new ArrayList<>();
                objsOnTraj = new ArrayList<>();
                correctedPigs = new ArrayList<>();

                for (ABObject obj : allObjects) {
                    boolean isPig = false;
                    if (pigs.contains(obj)) {
                        isPig = true;
                    } else if (birds.contains(obj)) {
                        // birds would be shown as blocking all objects on trajectory
                        continue;
                    }

                    for (Point p : predictedTrajectory) {
                        if (p.x < 840 && p.y < 480 && p.y > 100 && p.x > 400) {
                            currentBird.setCoordinates(p.x, p.y);
                            if (intersects(currentBird, obj, 3)) {
                                if (isPig) {
                                    pigsOnTraj.add(p);
                                } else {
                                    objsOnTraj.add(p);
                                }
                                // object intersects so dont need to check rest of points
                                break;
                            }
                        }
                    }
                }

                // now we know which objects intersect with the trajectory,
                // now check which pigs would be hitten behind 1st object
                // get MinX Value and remove all pigs behind this x
                int minX = 10000;
                if (!pigsOnTraj.isEmpty()) {

                    for (Point obj : objsOnTraj) {
                        if (obj.x < minX) {
                            minX = obj.x;
                        }
                    }

                    for (Point pig : pigsOnTraj) {
                        if (pig.x <= minX) {
                            correctedPigs.add(pig);
                        }
                    }
                }

                // now we got the corrected value of pigs on this trajectory, now return the best?!
                if (correctedPigs.size() > safeAmountOfPigsOnTraj) {
                    safeAmountOfPigsOnTraj = correctedPigs.size();
                    maxAmountOfPigsOnTraj = pigsOnTraj.size();
                    bestTrajType = currentTrajectoryType;
                    bestShot = ptp;
                }
            }
        }
        ABObject pseudoObject = new ABObject(new Rectangle(bestShot), ABType.BestMultiplePigShot);
        pseudoObject.setTrajectoryType(bestTrajType);
        System.out.println("Best shot: " + bestShot + " - " + bestTrajType + " will kill approx: " + safeAmountOfPigsOnTraj + " to " + maxAmountOfPigsOnTraj);

        return pseudoObject;
    }

    private boolean intersects(ABObject birdAB, ABObject target, int minPixelOverlap) {
        Circle circle = (Circle) birdAB;
        int circleDistance_x = Math.abs(circle.x - target.x);
        int circleDistance_y = Math.abs(circle.y - target.y);

        if (target.shape == ABShape.Circle) {
            Circle circle2 = (Circle) target;
            if ((circleDistance_x - circle.r - circle2.r - minPixelOverlap <= 0) && (circleDistance_y - circle.r - circle2.r - minPixelOverlap <= 0)) {
                return true;
            } else {
                return false;
            }

        } else if (target.shape == ABShape.Poly){
            // pseudo check for some points from bird
            Polygon polygon = ((Poly) target).polygon;
            if (polygon.contains(new Point((int)(circle.x+circle.r), (int)circle.y))){
                return true;
            } else if (polygon.contains(new Point(circle.x, (int) (circle.y + circle.r)))) {
                return true;
            } else {
                return false;
            }

        } else {
            //Rect
            ABObject rect = target;
            if (circleDistance_x + minPixelOverlap > (rect.width / 2 + circle.r)) {
                return false;
            }
            if (circleDistance_y + minPixelOverlap > (rect.height / 2 + circle.r)) {
                return false;
            }

            if (circleDistance_x + minPixelOverlap <= (rect.width / 2)) {
                return true;
            }
            if (circleDistance_y + minPixelOverlap <= (rect.height / 2)) {
                return true;
            }

            int cornerDistance_sq = (circleDistance_x - rect.width / 2) ^ 2 +
                    (circleDistance_y - rect.height / 2) ^ 2;

            return (cornerDistance_sq + minPixelOverlap <= (Math.pow(circle.r, 2)));

        }

    }


    private ArrayList<ABObject> calculateTargetObjects() {
        Vision vision = GameState.getVision();
        // 1. TNTs
        ArrayList<ABObject> targetObjects = new ArrayList<>(vision.findTNTs());
        // 2. big round objects
        for (ABObject obj : vision.findBlocksRealShape()) {
            if (obj.shape == ABShape.Circle) {
                Circle objC = (Circle) obj;
                // have to see if 9000 is too big
                if (objC.r > 9000) {
                    System.out.println("-" + obj.toString());
                    targetObjects.add(obj);
                }
            }
        }

        List<ABObject> blocksRealShapeSorted = vision.findBlocksRealShape();
        Collections.sort(blocksRealShapeSorted);

        // 3. structural objects with their "scores"
        for (ABObject obj : blocksRealShapeSorted) {
            int objectsLeftCount = 0, objectsRightCount = 0;
            Set<ABObject> aboveObjects = new HashSet<>();

            double oxmin, oxmax, oymin, oymax;

            oxmin = obj.x;
            oxmax = obj.x + obj.width;
            oymin = obj.y;
            oymax = obj.y + obj.height;
            for (ABObject neighbor : vision.findBlocksMBR()) {
                double nxmin, nxmax, nymin, nymax;
                nxmin = neighbor.x;
                nxmax = neighbor.x + neighbor.width;
                nymin = neighbor.y;
                nymax = neighbor.y + neighbor.height;

                boolean nearX = false, nearY = false;

                if ((oxmin >= nxmin - 10 && oxmin <= nxmax + 10) || (nxmin >= oxmin - 10 && nxmin <= oxmax + 10)) {
                    nearX = true;
                }

                if ((oymin >= nymin - 10 && oymin <= nymax + 10) || (nymin >= oymin - 10 && nymin <= oymax + 10)) {
                    nearY = true;
                }

                if (nearX && nymax <= oymin) {
                    aboveObjects.addAll(neighbor.getObjectsAboveSet());
                } else if (nearY && nxmax <= oxmin && oxmin - nxmax < 20) {
                    objectsLeftCount++;
                } else if (nearY && nxmin >= oxmax && nxmin - oxmax < 20) {
                    objectsRightCount++;
                }
            }

            // calculates distance to averagePig
            double pigX = 0, pigY = 0, distanceToPigs;

            double pigCount = (double) vision.findPigsMBR().size();
            for (ABObject pig : vision.findPigsMBR()) {
                pigX += pig.getCenterX();
                pigY += pig.getCenterY();
            }
            pigX = pigX / pigCount;
            pigY = pigY / pigCount;

            distanceToPigs = Math.sqrt(Math.pow((Math.abs(obj.getCenterX() - pigX)), 2) + Math.pow((Math.abs(obj.getCenterY() - pigY)), 2));

            obj.setObjectsAboveSet(aboveObjects);


            List<ABObject> objectsOnTrajectory = ABUtil.getObjectsOnTrajectory(obj, ABObject.TrajectoryType.HIGH);
            objectsLeftCount = objectsOnTrajectory.size();

            obj.setObjectsAround(obj.getObjectsAboveSet().size(), objectsLeftCount, objectsRightCount, distanceToPigs);
            targetObjects.add(obj);
        }

        // 4. pigs
        targetObjects.addAll(vision.findPigsRealShape());

        //5. best pigShot
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

    /**
     * returns an approximation of shootableObjects objects
     *
     * @return list of approximation of shootableObjects objects
     */
    private ArrayList<ABObject> calculateShootableObjects() {
        Vision vision = GameState.getVision();
        List<ABObject> lowShootableObjects = new ArrayList<>(vision.findBlocksRealShape());
        lowShootableObjects.addAll(vision.findHills());
        lowShootableObjects.addAll(vision.findPigsRealShape());
        lowShootableObjects.addAll(vision.findTNTs());

        // check for every object if is blocked by a neighbour
        for (ABObject object : allObjects) {
            double x_object = object.getCenterX();
            double y_object = object.getCenterY();
            innerloop:
            for (ABObject neighbor : allObjects) {
                if (!object.equals(neighbor)) {
                    double x_neighbor = neighbor.getCenterX();
                    double y_neighbor = neighbor.getCenterY();
                    if ((x_neighbor < x_object) && (y_neighbor < y_object)) {
                        if (((x_object - x_neighbor) < 20) && ((y_object - y_neighbor) < 20)) {
                            lowShootableObjects.remove(object);
                            break innerloop;
                        }
                    }
                }
            }
        }

        ArrayList<ABObject> shootableObjects = new ArrayList<>(lowShootableObjects);
        for (ABObject lowShootableObject : lowShootableObjects) {
            ABObject highShootableObject = (ABObject) lowShootableObject.clone();
            highShootableObject.setTrajectoryType(ABObject.TrajectoryType.HIGH);
            shootableObjects.add(highShootableObject);
        }

        return shootableObjects;
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

    public ArrayList<ABObject> getShootableObjects() {
        return shootableObjects;
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

}
