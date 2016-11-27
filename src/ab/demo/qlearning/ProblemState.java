package ab.demo.qlearning;

import ab.vision.ABObject;
import ab.vision.Vision;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the current state of the game using the coordinates of the birds, the pigs and all found objects and their type
 *
 * Example: RedBird 4.5 4.5 RectRedBird 2.5 5.0 RectRedBird 3.5 4.0 RectWood 1.0 9.5 RectWood 0.0 0.5 RectWood 3.5 1.0 RectWood 2.0 2.5 RectWood 3.5 2.0 RectWood 4.0 0.0 RectIce 5.0 6.0 RectWood 5.0 3.5 RectWood 4.0 4.0 RectStone 5.0 3.5 RectWood 5.0 0.5 RectIce 2.5 6.0 RectWood 7.0 2.5 RectWood 6.0 0.0 RectWood 5.0 9.5 RectRedBird 4.5 4.5 RectRedBird 2.5 5.0 RectRedBird 3.5 4.0 Rect
 *
 * @author jgonsior
 */
public class ProblemState {

    private Vision vison;
    private List<ABObject> allObjects;

    public ProblemState(Vision vision) {
        vison = vision;

        allObjects = new ArrayList<>(vison.findBirdsRealShape());
        allObjects.addAll(vison.findBlocksRealShape());
        allObjects.addAll(vison.findPigsRealShape());
    }

    /**
     * Returns a deserialized problem state representation
     *
     * @return the problem state deserialized into a string
     */
    public String toString() {
        String string = "";
        for (ABObject object : allObjects) {
            string += object.getType() + " " + String.valueOf(((int) object.getCenterX()) / 10) + " " + String.valueOf(((int) object.getCenterY()) / 10) + " " + String.valueOf(object.shape);
        }
        return string;
    }

    public List<ABObject> getShootableObjects(){
        List<ABObject> shootableObjects = new ArrayList<>(vison.findBlocksRealShape());
        shootableObjects.addAll(vison.findPigsRealShape());
        for (ABObject object : allObjects) {
            double x1 = object.getCenterX();
            double y1 = object.getCenterY();
            for (ABObject neighbor : allObjects) {
                if  (!object.equals(neighbor) ){
                    double x2 = neighbor.getCenterX();
                    double y2 = neighbor.getCenterY();

                    if ((x1 > x2) && ((x1-x2)<10) ){
                        shootableObjects.remove(object);
                    } else if ((y1 > y2) && ((y1-y2)<10)){
                        shootableObjects.remove(object);
                    }
                }
            }
        }

        return shootableObjects;
    }
}
