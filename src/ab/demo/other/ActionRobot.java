/*****************************************************************************
 ** ANGRYBIRDS AI AGENT FRAMEWORK
 ** Copyright (c) 2014,XiaoYu (Gary) Ge, Stephen Gould,Jochen Renz
 **  Sahan Abeyasinghe, Jim Keys,   Andrew Wang, Peng Zhang
 ** All rights reserved.
 **This work is licensed under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 **To view a copy of this license, visit http://www.gnu.org/licenses/
 *****************************************************************************/
package ab.demo.other;

import ab.server.Proxy;
import ab.server.proxy.message.ProxyClickMessage;
import ab.server.proxy.message.ProxyDragMessage;
import ab.server.proxy.message.ProxyMouseWheelMessage;
import ab.server.proxy.message.ProxyScreenshotMessage;
import ab.utils.StateUtil;
import ab.vision.ABObject;
import ab.vision.ABType;
import ab.vision.GameStateExtractor;
import ab.vision.Vision;
import org.apache.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * Util class for basic functions
 */
public class ActionRobot {
    private static final Logger logger = Logger.getLogger(ActionRobot.class);
    public static Proxy proxy;

    static {
        if (proxy == null) {
            try {
                //avoid port confusion with peter
                proxy = new Proxy() {
                    @Override
                    public void onOpen() {
                        logger.info("Client connected");
                    }

                    @Override
                    public void onClose() {
                        logger.info("Client disconnected");
                    }
                };
                proxy.start();

                logger.info("Server started on port: " + proxy.getPort());

                logger.info("Waiting for client to connect");
                proxy.waitForClients(1);

            } catch (UnknownHostException e) {
                logger.error(e);
            }
        }
    }

    public String level_status = "UNKNOWN";
    public int current_score = 0;
    private LoadLevelSchema lls;
    private RestartLevelSchema rls;

    // A java util class for the standalone version. It provides common
    // functions an agent would use. E.g. get the screenshot
    public ActionRobot() {

        proxy.send(new ProxyClickMessage(305, 277));


        lls = new LoadLevelSchema(proxy);
        rls = new RestartLevelSchema(proxy);
    }

    public static void GoFromMainMenuToLevelSelection() {
        // --- go from the main menu to the episode menu
        GameStateExtractor.GameStateEnum state = StateUtil.getGameState(proxy);
        while (state == GameStateExtractor.GameStateEnum.MAIN_MENU) {

            logger.info("Go to the Episode Menu");
            proxy.send(new ProxyClickMessage(305, 277));
            logger.info("Wait 1000");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

                e.printStackTrace();
            }
            state = StateUtil.getGameState(proxy);
        }
        // --- go from the episode menu to the level selection menu
        while (state == GameStateExtractor.GameStateEnum.EPISODE_MENU) {
            logger.info("Select the Poached Eggs Episode");
            proxy.send(new ProxyClickMessage(150, 300));
            state = StateUtil.getGameState(proxy);
            logger.info("Wait 1000");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {

                e.printStackTrace();
            }
            state = StateUtil.getGameState(proxy);
        }

        logger.info("selected level");

    }

    public static void fullyZoomOut() {
        for (int k = 0; k < 15; k++) {

            proxy.send(new ProxyMouseWheelMessage(-1));
        }

        logger.info("Wait 2000 after fully zooming out");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void fullyZoomIn() {
        for (int k = 0; k < 15; k++) {
            proxy.send(new ProxyMouseWheelMessage(1));
        }

        logger.info("Wait 2000 after fully zooming in");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static BufferedImage doScreenShot() {
        byte[] imageBytes = proxy.send(new ProxyScreenshotMessage());
        BufferedImage image = null;
        try {
            image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        } catch (IOException e) {

        }

        return image;
    }

    public static void main(String args[]) {

        long time = System.currentTimeMillis();
        ActionRobot.doScreenShot();
        time = System.currentTimeMillis() - time;
        logger.info(" cost: " + time);
        time = System.currentTimeMillis();
        int count = 0;
        while (count < 40) {
            ActionRobot.doScreenShot();
            count++;
        }

        logger.info(" time to take 40 screenshots"
                + (System.currentTimeMillis() - time));
        System.exit(0);

    }

    public static void skipPopUp() {
        proxy.send(new ProxyClickMessage(255, 350));
        proxy.send(new ProxyClickMessage(180, 130));
    }

    public void restartLevel() {
        rls.restartLevel();
    }

    public GameStateExtractor.GameStateEnum shootWithStateInfoReturned(List<Shot> csc) {
        ShootingSchema ss = new ShootingSchema();
        ss.shoot(proxy, csc);
        logger.info("Shooting Completed");
        GameStateExtractor.GameStateEnum state = StateUtil.getGameState(proxy);
        return state;

    }

    public synchronized GameStateExtractor.GameStateEnum getState() {
        GameStateExtractor.GameStateEnum state = StateUtil.getGameState(proxy);
        return state;
    }

    public void shoot(List<Shot> csc) {
        ShootingSchema ss = new ShootingSchema();

        ss.shoot(proxy, csc);
        logger.info("Shooting Completed");
        logger.info("Wait 15 seconds to ensure all objects in the scene static");
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    public void cshoot(Shot shot) {
        this.cFastshoot(shot);
        logger.info("Shooting Completed");

        logger.info("Wait 500 after shooting");
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void cFastshoot(Shot shot) {
        ShootingSchema ss = new ShootingSchema();
        LinkedList<Shot> shots = new LinkedList<Shot>();
        shots.add(shot);
        ss.shoot(proxy, shots);
    }

    public void click() {
        proxy.send(new ProxyClickMessage(100, 100));
    }

    public void drag() {
        proxy.send(new ProxyDragMessage(0, 0, 0, 0));
    }

    public void loadLevel(int... i) {
        int level = 1;
        if (i.length > 0) {
            level = i[0];
        }

        lls.loadLevel(level);
    }

    /*
     * @return the type of the bird on the sling.
     *
     * **/
    public ABType getBirdTypeOnSling() {
        fullyZoomIn();
        BufferedImage screenshot = doScreenShot();
        Vision vision = new Vision(screenshot);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {

            e.printStackTrace();
        }
        fullyZoomOut();
        List<ABObject> _birds = vision.findBirdsMBR();
        if (_birds.isEmpty())
            return ABType.Unknown;
        Collections.sort(_birds, new Comparator<Rectangle>() {

            @Override
            public int compare(Rectangle o1, Rectangle o2) {

                return ((Integer) (o1.y)).compareTo((Integer) (o2.y));
            }
        });
        return _birds.get(0).getType();
    }

    public int getScore() {
        return StateUtil.getScore(ActionRobot.proxy);
    }
}
