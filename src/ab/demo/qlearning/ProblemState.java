package ab.demo.qlearning;

import ab.demo.other.ActionRobot;
import ab.vision.ABObject;
import ab.vision.GameStateExtractor;
import ab.vision.Vision;
import ab.vision.ABShape;
import ab.vision.real.shape.Circle;

import java.util.ArrayList;
import java.util.List;
import java.lang.*;

/**
 * Represents the current state of the game using the coordinates of the birds, the pigs and all found objects and their type
 * <p>
 * Example: RedBird 4.5 4.5 RectRedBird 2.5 5.0 RectRedBird 3.5 4.0 RectWood 1.0 9.5 RectWood 0.0 0.5 RectWood 3.5 1.0 RectWood 2.0 2.5 RectWood 3.5 2.0 RectWood 4.0 0.0 RectIce 5.0 6.0 RectWood 5.0 3.5 RectWood 4.0 4.0 RectStone 5.0 3.5 RectWood 5.0 0.5 RectIce 2.5 6.0 RectWood 7.0 2.5 RectWood 6.0 0.0 RectWood 5.0 9.5 RectRedBird 4.5 4.5 RectRedBird 2.5 5.0 RectRedBird 3.5 4.0 Rect
 *
 * @author jgonsior
 */
public class ProblemState {

    public List<ABObject> shootableObjects;
    public List<ABObject> targetObjects;
    private Vision vision;
    private List<ABObject> allObjects;
    private int id;
    private boolean initialized;

    public ProblemState(Vision vision, ActionRobot actionRobot, int id, boolean initialized) {
        GameStateExtractor.GameState state = actionRobot.getState();
        allObjects = new ArrayList<>();
        this.vision = vision;
        this.id = id;
        this.initialized = initialized;

        if (state == GameStateExtractor.GameState.PLAYING) {

            allObjects.addAll(vision.findBirdsRealShape());
            allObjects.addAll(vision.findBlocksRealShape());
            allObjects.addAll(vision.findPigsRealShape());
            allObjects.addAll(vision.findHills());
            allObjects.addAll(vision.findTNTs());

            shootableObjects = calculateShootableObjects();
            targetObjects = calculateTargetObjects();

        }
    }

    private List<ABObject> calculateTargetObjects(){
        // 1. TNTs
        List<ABObject> targetObjects = new ArrayList<>(vision.findTNTs());
        // 2. big round objects
        for (ABObject obj : vision.findBlocksRealShape()){
            if (obj.shape == ABShape.Circle){
                Circle objC = (Circle) obj;
                // have to see if 9000 is too big
                if (objC.r > 9000){
                    System.out.println("-" + obj.toString());
                    targetObjects.add(obj);
                }
            }
        }
        // 3. structural objects with their "scores"
        for (ABObject obj : vision.findBlocksRealShape()){
            int above, left, right;
            above = left = right = 0;
            double oxmin, oxmax, oymin, oymax;
            oxmin = obj.x;
            oxmax = obj.x + obj.width;
            oymin = obj.y;
            oymax = obj.y + obj.height;
            for (ABObject n : vision.findBlocksMBR()){
                double nxmin, nxmax, nymin, nymax;
                nxmin = n.x;
                nxmax = n.x + n.width;
                nymin = n.y;
                nymax = n.y + n.height;

                boolean nearX, nearY;
                nearX = nearY = false;
                if ((oxmin >= nxmin-10 && oxmin <= nxmax+10) || (nxmin >= oxmin-10 && nxmin <= oxmax+10)){
                    nearX = true;
                }

                if ((oymin >= nymin-10 && oymin <= nymax+10) || (nymin >= oymin-10 && nymin <= oymax+10)){
                    nearY = true;
                }

                if (nearX && nymax <= oymin){
                    above++;
                } else if (nearY && nxmax <= oxmin && oxmin-nxmax < 20){
                    left++;
                } else if (nearY && nxmin >= oxmax && nxmin-oxmax < 20){
                    right++;
                }
            }

            // calculates distance to averagePig
            double pigX, pigY, pigAmount, pigDistance;
            pigX = pigY = 0;
            pigAmount = (double) vision.findPigsMBR().size();
            for (ABObject pig : vision.findPigsMBR()){
                pigX += pig.getCenterX();
                pigY += pig.getCenterY();
            }
            pigX = pigX / pigAmount;
            pigY = pigY / pigAmount;

            pigDistance = Math.sqrt(Math.pow((Math.abs(obj.getCenterX()-pigX)),2) + Math.pow((Math.abs(obj.getCenterY()-pigY)),2));
                        
            obj.setObjectsAround(above, left, right, pigDistance);
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
    private List<ABObject> calculateShootableObjects() {
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

        List<ABObject> shootableObjects = new ArrayList<>(lowShootableObjects);
        for (ABObject lowShootableObject : lowShootableObjects) {
            ABObject highShootableObject = (ABObject) lowShootableObject.clone();
            highShootableObject.setTrajectoryType(ABObject.TrajectoryType.HIGH);
            shootableObjects.add(highShootableObject);
        }

        return shootableObjects;
    }

    public List<ABObject> getShootableObjects() {
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

    public boolean getInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }
}
