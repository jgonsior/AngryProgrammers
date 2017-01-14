package ab.utils;

import ab.demo.other.GameState;
import ab.demo.other.Shot;
import ab.vision.ABObject;
import ab.vision.Vision;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ABUtil {

    private static final Logger logger = Logger.getLogger(ABUtil.class);
    public static int gap = 5; //vision tolerance.

    // If o1 supports o2, return true
    public static boolean isSupport(ABObject o2, ABObject o1) {
        if (o2.x == o1.x && o2.y == o1.y && o2.width == o1.width && o2.height == o1.height)
            return false;

        int ex_o1 = o1.x + o1.width;
        int ex_o2 = o2.x + o2.width;

        int ey_o2 = o2.y + o2.height;
        if (
                (Math.abs(ey_o2 - o1.y) < gap)
                        &&
                        !(o2.x - ex_o1 > gap || o1.x - ex_o2 > gap)
                )
            return true;

        return false;
    }

    //Return a link list of ABObjects that support o1 (test by isSupport function ).
    //objs refers to a list of potential supporters.
    //Empty list will be returned if no such supporters.
    public static List<ABObject> getSupporters(ABObject o2, List<ABObject> objs) {
        List<ABObject> result = new LinkedList<ABObject>();
        //Loop through the potential supporters
        for (ABObject o1 : objs) {
            if (isSupport(o2, o1))
                result.add(o1);
        }
        return result;
    }

    //Return true if the target can be hit by releasing the bird at the specified release point
    public static boolean isReachable(Vision vision, Point target, Shot shot) {
        //test whether the trajectory can pass the target without considering obstructions
        Point releasePoint = new Point(shot.getX() + shot.getDx(), shot.getY() + shot.getDy());
        int traY = GameState.getTrajectoryPlanner().getYCoordinate(vision.findSlingshotMBR(), releasePoint, target.x);
        if (Math.abs(traY - target.y) > 100) {
            //System.out.println(Math.abs(traY - target.y));
            return false;
        }
        boolean result = true;
        List<Point> points = GameState.getTrajectoryPlanner().predictTrajectory(GameState.getSlingshot(), releasePoint);
        for (Point point : points) {
            if (point.x < 840 && point.y < 480 && point.y > 100 && point.x > 400) {
                for (ABObject ab : vision.findBlocksMBR()) {
                    if (((ab.contains(point) && !ab.contains(target)) || Math.abs(vision.getMBRVision()._scene[point.y][point.x] - 72) < 10)
                            && point.x < target.x) {
                        return false;
                    }
                }
            }
        }
        return result;
    }

    public static Point calculateReleasePoint(Point targetPoint, ABObject.TrajectoryType trajectoryType) {
        Point releasePoint = null;
        // estimate the trajectory
        ArrayList<Point> estimateLaunchPoints = GameState.getTrajectoryPlanner().estimateLaunchPoint(GameState.getSlingshot(), targetPoint);

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
            releasePoint = GameState.getTrajectoryPlanner().findReleasePoint(GameState.getSlingshot(), Math.PI / 4);
        }
        return releasePoint;
    }

    public static Shot generateShot(int tappingTime, Point releasePoint) {
        if (releasePoint == null) {
            logger.error("No release point found -,-");
        }

        Point referencePoint = GameState.getTrajectoryPlanner().getReferencePoint(GameState.getSlingshot());

        int dx = (int) releasePoint.getX() - referencePoint.x;
        int dy = (int) releasePoint.getY() - referencePoint.y;

        return new Shot(referencePoint.x, referencePoint.y, dx, dy, 1, tappingTime);

    }

}