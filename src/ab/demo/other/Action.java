package ab.demo.other;

import ab.vision.ABObject;

import java.awt.*;

/**
 * @author: Julius Gonsior
 */
public class Action {
    private String name;
    private ABObject.TrajectoryType trajectoryType;
    private boolean rand;
    private ABObject targetObject;
    private Point targetPoint;
    private double score = 0;

    public Action(ABObject targetObject, ABObject.TrajectoryType trajectoryType) {
        //this.id = actionId;
        this.targetObject = targetObject;
        this.targetPoint = targetObject.getCenter();
        this.trajectoryType = trajectoryType;
        setName(targetObject.myToString() + " " + trajectoryType.name());
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
        setName(String.format("%.2f", score) + " " + getName());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @todo: set targetObject?!
     * <p>
     * private void calculateTargetObject() {
     * //calculate targetPoint
     * /*ArrayList<ABObject> shootableObjects = GameState.getProblemState().getPossibleActions();
     * <p>
     * //@todo should be removed and it needs to be investigated why nextAction returns sometimes wrong actions!
     * // seems to be error in vision module where it found invisible objects on initialisation
     * /*if (shootableObjects.size() - 1 < this.getId()) {
     * this.setId(shootableObjects.size() - 1);
     * }*
     * targetObject = shootableObjects.get(this.getId());*
     * <p>
     * targetPoint = targetObject.getCenter();
     * }
     */

    public boolean isRand() {
        return rand;
    }

    public void setRand(boolean rand) {
        this.rand = rand;
    }

    public ABObject.TrajectoryType getTrajectoryType() {
        return trajectoryType;
    }

    public String getTargetObjectString() {
        return targetObject.toString();
    }

    public ABObject getTargetObject() {
        return targetObject;
    }

    public Point getTargetPoint() {
        return targetPoint;
    }
}
