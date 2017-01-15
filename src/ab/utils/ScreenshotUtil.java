package ab.utils;

import ab.demo.other.GameState;
import ab.server.Proxy;
import ab.vision.ABObject;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author: Julius Gonsior
 */
public class ScreenshotUtil {

    protected static final Logger logger = Logger.getLogger(ScreenshotUtil.class);

    public static void saveCurrentScreenshot(String title) {
        saveScreenshot(GameState.getScreenshot(), title);
    }

    public static void saveCurrentScreenshot() {
        saveCurrentScreenshot(GameState.getNextAction().getName());
    }

    public static void saveScreenshot(BufferedImage screenshot, String title) {
        File outputFile = new File("imgs/" + Proxy.getProxyPort() + "/" + GameState.getGameId() + "/" + GameState.getCurrentLevel() + "_" + GameState.getMoveCounter() + "_" + title + "_" + System.currentTimeMillis() + ".gif");
        try {
            outputFile.getParentFile().mkdirs();
            ImageIO.write(GameState.getScreenshot(), "gif", outputFile);
        } catch (IOException e) {
            logger.error("Unable to save screenshot " + e);
            e.printStackTrace();
        }
        logger.info("Saved screenshot " + outputFile.getAbsolutePath());
    }

    public static void saveTrajectoryScreenshot(Rectangle slingshot, Point releasePoint, ABObject targetObject, List<ABObject> objectsOnTrajectory) {
        GameState.updateCurrentVision();
        //screenshot with trajectory
        BufferedImage screenshotWithTrajectory = GameState.getTrajectoryPlanner().plotTrajectory(GameState.getScreenshot(), slingshot, releasePoint);
        String title = targetObject.type + Integer.toString(targetObject.x) + "," + targetObject.y + "|";
        for (ABObject abObject : objectsOnTrajectory) {
            title += abObject.type + Integer.toString(abObject.x) + "," + abObject.y + "-";
        }

        saveScreenshot(screenshotWithTrajectory, title);
    }

}
