package ab.demo.qlearning;

import ab.vision.ABObject;

/**
 * @author: Julius Gonsior
 */
public class Action {
    private int actionId;
    private ABObject.TrajectoryType trajectoryType;
    private String targetObjectString;

    public boolean isRand() {
        return rand;
    }

    public void setRand(boolean rand) {
        this.rand = rand;
    }

    private boolean rand;

    public Action(int actionId, String trajectoryTypeString, String targetObjectString) {
        this.actionId = actionId;
        this.trajectoryType = ABObject.TrajectoryType.valueOf(trajectoryTypeString);
        this.targetObjectString = targetObjectString;
    }

    public ABObject.TrajectoryType getTrajectoryType() {
        return trajectoryType;
    }

    public void setTrajectoryType(ABObject.TrajectoryType trajectoryType) {
        this.trajectoryType = trajectoryType;
    }

    public String getTargetObjectString() {
        return targetObjectString;
    }

    public void setTargetObjectString(String targetObjectString) {
        this.targetObjectString = targetObjectString;
    }

    public int getActionId() {

        return actionId;
    }

    public void setActionId(int actionId) {
        this.actionId = actionId;
    }
}
