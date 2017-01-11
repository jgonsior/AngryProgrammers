/*****************************************************************************
 ** ANGRYBIRDS AI AGENT FRAMEWORK
 ** Copyright (c) 2014, XiaoYu (Gary) Ge, Stephen Gould, Jochen Renz
 **  Sahan Abeyasinghe,Jim Keys,  Andrew Wang, Peng Zhang
 ** All rights reserved.
 **This work is licensed under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 **To view a copy of this license, visit http://www.gnu.org/licenses/
 *****************************************************************************/
package ab.vision;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class ABObject extends Rectangle implements Comparable<ABObject> {

    private static final long serialVersionUID = 1L;
    private static int counter = 0;
    public int id;
    //object type
    public ABType type;
    public int area = 0;
    //For all MBRs, the shape is Rect by default.
    public ABShape shape = ABShape.Rect;
    //For all MBRs, the angle is 0 by default.
    public double angle = 0;
    //is Hollow or not
    public boolean hollow = false;
    public int objectsAboveCount = 0;
    public int objectsLeftCount = -1;
    public int objectsRightCount = -1;
    public double distanceToPigs = -1;
    private TrajectoryType trajectoryType = TrajectoryType.LOW;
    private Set<ABObject> objectsAboveSet;

    public ABObject(Rectangle mbr, ABType type) {
        super(mbr);
        objectsAboveSet = new HashSet<>();
        objectsAboveSet.add(this);
        this.type = type;
        this.id = counter++;
    }

    public ABObject(Rectangle mbr, ABType type, int id) {
        super(mbr);
        objectsAboveSet = new HashSet<>();
        objectsAboveSet.add(this);
        this.type = type;
        this.id = id;
    }

    public ABObject(ABObject ab) {
        super(ab.getBounds());
        objectsAboveSet = new HashSet<>();
        objectsAboveSet.add(this);
        this.type = ab.type;
        this.id = ab.id;
    }

    public ABObject() {
        objectsAboveSet = new HashSet<>();
        objectsAboveSet.add(this);
        this.id = counter++;
        this.type = ABType.Unknown;
    }

    public static void resetCounter() {
        counter = 0;
    }

    public Set<ABObject> getObjectsAboveSet() {
        return objectsAboveSet;
    }

    public void setObjectsAboveSet(Set<ABObject> objectsAboveSet) {
        this.objectsAboveSet = objectsAboveSet;
    }

    public void setObjectsAround(int objectsAboveCount, int objectsLeftCount, int objectsRightCount, double distanceToPigs) {
        this.objectsAboveCount = objectsAboveCount;
        this.objectsLeftCount = objectsLeftCount;
        this.objectsRightCount = objectsRightCount;
        this.distanceToPigs = distanceToPigs;
    }

    public String toString() {
        return this.id + this.type.toString() + this.shape.toString();
    }

    public String myToString() {
        return String.format("%03d %03d %03d %06f", objectsAboveCount, objectsLeftCount, objectsRightCount, distanceToPigs) + " | " + this.toString() + " " + this.getTrajectoryType().name();
    }

    public TrajectoryType getTrajectoryType() {
        return trajectoryType;
    }

    public void setTrajectoryType(TrajectoryType trajectoryType) {
        this.trajectoryType = trajectoryType;
    }

    public ABType getType() {
        return type;
    }

    public Point getCenter() {
        return new Point((int) getCenterX(), (int) getCenterY());
    }

    public void setCoordinates(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int compareTo(ABObject abObject) {
        return this.y - abObject.y;
    }

    public enum TrajectoryType {HIGH, LOW}

}
