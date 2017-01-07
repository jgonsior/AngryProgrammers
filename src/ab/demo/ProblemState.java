package ab.demo;

import ab.demo.other.ActionRobot;
import ab.vision.ABObject;
import ab.vision.ABShape;
import ab.vision.GameStateExtractor;
import ab.vision.Vision;
import ab.vision.real.shape.Circle;

import java.util.*;

/**
 * Represents the current state of the game using the coordinates of the birds, the pigs and all found objects and their type
 * <p>
 * Example: RedBird 4.5 4.5 RectRedBird 2.5 5.0 RectRedBird 3.5 4.0 RectWood 1.0 9.5 RectWood 0.0 0.5 RectWood 3.5 1.0 RectWood 2.0 2.5 RectWood 3.5 2.0 RectWood 4.0 0.0 RectIce 5.0 6.0 RectWood 5.0 3.5 RectWood 4.0 4.0 RectStone 5.0 3.5 RectWood 5.0 0.5 RectIce 2.5 6.0 RectWood 7.0 2.5 RectWood 6.0 0.0 RectWood 5.0 9.5 RectRedBird 4.5 4.5 RectRedBird 2.5 5.0 RectRedBird 3.5 4.0 Rect
 *
 * @author jgonsior
 */
public class ProblemState {

    public ArrayList<ABObject> shootableObjects;

    public ArrayList<ABObject> targetObjects;

    private Vision vision;
    private List<ABObject> allObjects;
    private int id;
    private boolean isInitialized;

    public ProblemState(Vision vision, ActionRobot actionRobot, int id, boolean initialized) {
        GameStateExtractor.GameStateEnum state = actionRobot.getState();
        allObjects = new ArrayList<>();
        this.vision = vision;
        this.id = id;
        this.isInitialized = initialized;

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

    private ArrayList<ABObject> calculateTargetObjects() {
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
            int left, right;
            Set<ABObject> aboveObjects = new HashSet<>();
            left = right = 0;

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

                boolean nearX, nearY;
                nearX = nearY = false;
                if ((oxmin >= nxmin - 10 && oxmin <= nxmax + 10) || (nxmin >= oxmin - 10 && nxmin <= oxmax + 10)) {
                    nearX = true;
                }

                if ((oymin >= nymin - 10 && oymin <= nymax + 10) || (nymin >= oymin - 10 && nymin <= oymax + 10)) {
                    nearY = true;
                }

                if (nearX && nymax <= oymin) {
                    aboveObjects.addAll(neighbor.getObjectsAboveSet());
                } else if (nearY && nxmax <= oxmin && oxmin - nxmax < 20) {
                    left++;
                } else if (nearY && nxmin >= oxmax && nxmin - oxmax < 20) {
                    right++;
                }
            }

            // calculates distance to averagePig
            double pigX, pigY, pigAmount, pigDistance;
            pigX = pigY = 0;
            pigAmount = (double) vision.findPigsMBR().size();
            for (ABObject pig : vision.findPigsMBR()) {
                pigX += pig.getCenterX();
                pigY += pig.getCenterY();
            }
            pigX = pigX / pigAmount;
            pigY = pigY / pigAmount;

            pigDistance = Math.sqrt(Math.pow((Math.abs(obj.getCenterX() - pigX)), 2) + Math.pow((Math.abs(obj.getCenterY() - pigY)), 2));

            obj.setObjectsAboveSet(aboveObjects);

            obj.setObjectsAround(obj.getObjectsAboveSet().size(), left, right, pigDistance);
            targetObjects.add(obj);
        }

        // 4. pigs
        targetObjects.addAll(vision.findPigsRealShape());
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
        for (ABObject shootableObject : targetObjects) {
            Action action = new Action(i, shootableObject.getTrajectoryType(), this);
            action.setName(shootableObject.myToString());
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

    public boolean isInitialized() {
        return isInitialized;
    }

    public void setInitialized(boolean initialized) {
        this.isInitialized = initialized;
    }
}
