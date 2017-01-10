package ab.demo.strategies;

import ab.demo.other.ProblemState;
import ab.vision.ABObject;

import java.awt.*;
import java.util.ArrayList;

/**
 * @author: Julius Gonsior
 */
public class Action {
    private String name;
    private int id;
    private ABObject.TrajectoryType trajectoryType;
    private boolean rand;
    private ProblemState problemState;
    private ABObject targetObject;
    private Point targetPoint;

    public Action(int actionId, ABObject.TrajectoryType trajectoryType, ProblemState problemState) {
        this.id = actionId;
        this.trajectoryType = trajectoryType;
        this.problemState = problemState;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private void calculateTargetObject() {
        //calculate targetPoint
        ArrayList<ABObject> shootableObjects = problemState.targetObjects;

        //@todo should be removed and it needs to be investigated why nextAction returns sometimes wrong actions!
        // seems to be error in vision module where it found invisible objects on initialisation
        /*if (shootableObjects.size() - 1 < this.getId()) {
            this.setId(shootableObjects.size() - 1);
        }*/
        targetObject = shootableObjects.get(this.getId());
        targetPoint = targetObject.getCenter();
    }

    public ProblemState getProblemState() {
        return problemState;
    }

    public void setProblemState(ProblemState problemState) {
        this.problemState = problemState;
    }

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
        if (this.targetObject == null) {
            this.calculateTargetObject();
        }
        return targetObject.toString();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Point getTargetPoint() {
        if (this.targetObject == null) {
            this.calculateTargetObject();
        }
        return targetPoint;
    }
}
