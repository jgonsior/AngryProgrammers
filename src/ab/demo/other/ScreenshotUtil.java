package ab.demo.other;

import ab.server.Proxy;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

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
}
