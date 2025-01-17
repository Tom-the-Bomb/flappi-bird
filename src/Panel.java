
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;

public class Panel extends JPanel implements ActionListener, KeyListener {
    // frame size
    public static final int HEIGHT = 700;
    public static final int WIDTH = 1000;

    // delay to next tick in milliseconds
    public static final int DELAY = 25;

    // max distance from current to the next pipes opening
    public static final int MAX_PIPE_JUMP = 150;

    // spacing between pipes (in ticks)
    public static final int DIST_NEXT_PIPE = 340;
    public static final int TICKS_SPEED_INCREASE = 200;

    // speeds (positive number representing leftwards movement)
    private int pipeSpeed = 5;

    // used to keep track of pipe distance
    private int ticksElapsed;
    private int lastPipeTick;

    // objects that appear on the game board
    //
    // use ArrayDeque to store the pipes and clouds
    // <https://www.geeksforgeeks.org/arraydeque-in-java/>
    //
    private final Player player;
    private final ArrayDeque<Pipe> pipes;
    private final ArrayDeque<Cloud> clouds;
    // background image
    private Image background;

    // score
    private int score;
    // high score
    private int high;

    // whether or not the user has pressed the space bar yet
    private boolean spacePressed;

    private boolean alive;
    private boolean waitingForReset;

    public Panel() {
        // set the frames size
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        // set the frames background color
        setBackground(new Color(32, 168, 244));

        loadImage();

        // initialize the game state
        player = new Player();
        pipes = new ArrayDeque<>();
        clouds = new ArrayDeque<>();

        ticksElapsed = 0;
        lastPipeTick = 0;
        alive = true;
        waitingForReset = false;
        score = 0;
        high = 0;
        spacePressed = false;

        // this timer will call the actionPerformed() method every `DELAY` ms
        // so we can update the game state such as ticking the assets
        //
        // controls the delay between each tick in ms
        Timer timer = new Timer(DELAY, this);
        timer.start();
    }

    private void loadImage() {
        try {
            BufferedImage bg = ImageIO.read(new File(
                getClass().getResource("/resources/background.jpg").getFile()
            ));
            background = bg.getScaledInstance(WIDTH, HEIGHT, Image.SCALE_SMOOTH);

        } catch (IOException exc) {
            exc.printStackTrace();
        }
    }

    // changes state of game before repainting every time
    @Override
    public void actionPerformed(ActionEvent e) {
        // keeps track of time for pipes
        ticksElapsed++;

        // if you're not alive you're DEAD!!!
        if (!alive) {
            return;
        }

        if ((ticksElapsed - lastPipeTick) * pipeSpeed >= DIST_NEXT_PIPE) {
            int min;
            int max;

            if (!pipes.isEmpty()) {
                // calculate the range of the next pipe's height
                min = Math.max(pipes.getLast().getPipeHeight() - MAX_PIPE_JUMP, 30);
                max = Math.min(pipes.getLast().getPipeHeight() + MAX_PIPE_JUMP, Panel.HEIGHT - 30 - Pipe.SPACE);
            } else {
                // first pipe
                min = MAX_PIPE_JUMP;
                max = Panel.HEIGHT - MAX_PIPE_JUMP - Pipe.SPACE;
            }

            // random: {1, 2}
            int cloudSpeed = (int) (Math.random() * 2) + 1;
            // random: {1, 2, 3, 4, 5}
            int cloudScale = (int) (Math.random() * 5) + 1;
            // add new cloud to the end of the cloud queue
            clouds.add(
                new Cloud(cloudSpeed, cloudScale, new Point(WIDTH, 50))
            );

            // left-most cloud has moved off screen
            if (clouds.getFirst().getPos().x <= 0) {
                clouds.removeFirst();
            }

            // random number for next pipe height that is not too far from last pipe's opening
            int nextPipeHeight = (int) (Math.random() * (max - min + 1)) + min;
            pipes.add(new Pipe(this, nextPipeHeight));

            lastPipeTick = ticksElapsed;

            // left-most pipe has moved off screen
            if (pipes.getFirst().getPose().x <= 0) {
                pipes.removeFirst();
            }
        }

        // increase difficulty by incrementing pipe's movement speed by 1 every `TICKS_SPEED_INCREASE` number of ticks
        if (ticksElapsed % TICKS_SPEED_INCREASE == 0) {
            pipeSpeed++;
        }

        // update all assets on screen
        player.tick();

        for (Pipe pipe : pipes) {
            pipe.tick();
        }

        for (Cloud cloud : clouds) {
            cloud.tick();
        }

        // calls the `paintComponent` method redrawing the graphics
        repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        // draw the background image
        g.drawImage(background,0, 0, null);
        // draw the player (bird)
        player.draw(g, this);

        if (!spacePressed) {
            // draw beginning instructions text
            g.setColor(Color.black);
            g.setFont(new Font("Britannic Bold", Font.BOLD, 30));

            FontMetrics metrics = g.getFontMetrics(g.getFont());

            String message = "Press space to jump";

            // draw the loss message in the center of the screen
            g.drawString(
                message,
                Panel.WIDTH / 2 - metrics.stringWidth(message) / 2,
                Panel.HEIGHT / 2 - metrics.getHeight() / 2
            );
        }

        // re-draw all clouds
        for (Cloud cloud : clouds) {
            cloud.draw(g);
        }

        // re-draw all pipes
        for (Pipe pipe : pipes) {
            pipe.draw(g, this);
        }

        for (Pipe pipe : pipes) {
            Point TL = pipe.getPointTL();
            Point TM = pipe.getPointTM();
            Point TR = pipe.getPointTR();
            Point BL = pipe.getPointBL();
            Point BM = pipe.getPointBM();
            Point BR = pipe.getPointBR();

            // calculate the player's hitbox
            int r = Player.PLAYER_SIZE / 2;

            Point playerPose = player.getPose();
            Point playerCenter = new Point(playerPose.x + r, playerPose.y + r);

            double m = (TL.getY() - TM.getY()) / (TL.getX() - TM.getX());

            // solved for x and y values of the player's hitbox using the system of equations:
            // [1]: -x/y = m (slope of pipe is equal to tangent slope of circular player)
            // [2]: x^2 + y^2 = r^2 (equation of circle for the player)
            //
            double y1 = Math.sqrt(r * r / (m * m + 1));
            double y2 = -y1;
            double x1 = -y1 * m;
            double x2 = -y2 * m;

            x1 += playerCenter.getX();
            x2 += playerCenter.getX();
            y1 += playerCenter.getY();
            y2 += playerCenter.getY();

            Point playerTL = new Point((int) Math.min(x1, x2), (int) Math.min(y1, y2));
            Point playerTR = new Point((int) Math.max(x1, x2), (int) Math.min(y1, y2));
            Point playerBL = new Point((int) Math.min(x1, x2), (int) Math.max(y1, y2));
            Point playerBR = new Point((int) Math.max(x1, x2), (int) Math.max(y1, y2));

            // calculate y-values for the x-values on player's hitbox corners
            double TL_TM = (TL.getY() - TM.getY()) / (TL.getX() - TM.getX()) * (playerTR.getX() - TM.getX()) + TM.getY();
            double TM_TR = (TM.getY() - TR.getY()) / (TM.getX() - TR.getX()) * (playerTL.getX() - TM.getX()) + TM.getY();
            double BL_BM = (BL.getY() - BM.getY()) / (BL.getX() - BM.getX()) * (playerBR.getX() - BM.getX()) + BM.getY();
            double BM_BR = (BM.getY() - BR.getY()) / (BM.getX() - BR.getX()) * (playerBL.getX() - BM.getX()) + BM.getY();

            boolean inRange = playerTL.x <= TM.x && playerTL.x >= TL.x && playerTR.x >= TM.x && playerTR.x <= TR.x;

            if (
                (inRange && playerBL.y >= BM.y && playerBR.y >= BM.y)
                || (inRange && playerTL.y <= TM.y && playerTR.y <= TM.y)
                || (playerTR.x >= TL.x && playerTR.x <= TM.x && (playerTR.y <= TL_TM || playerBR.y >= BL_BM))
                || (playerTL.x <= TR.x && playerTL.x >= TM.x && (playerTL.y <= TM_TR || playerBL.y >= BM_BR))
            ) {
                alive = false;
                drawDefeatScreen(g);
                break;
            }
        }
        drawScore(g);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        if (key == KeyEvent.VK_SPACE) {
            player.jump();
            spacePressed = true;
        }

        if (waitingForReset) {
            if (key == KeyEvent.VK_C) {
                reset();
                waitingForReset = false;
            } else if (key == KeyEvent.VK_Q) {
                System.exit(0);
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyTyped(KeyEvent e) {}

    // resets the game
    public void reset() {
        pipes.clear();
        alive = true;
        ticksElapsed = 0;
        lastPipeTick = 0;
        pipeSpeed = 5;
        player.reset();
    }

    public void drawDefeatScreen(Graphics g) {
        String lossMessage = "YOU LOOOSE press C key to continue Q to quit";

        g.setColor(Color.black);
        g.setFont(new Font("Britannic Bold", Font.BOLD, 30));

        FontMetrics metrics = g.getFontMetrics(g.getFont());

        int y = Panel.HEIGHT / 2 - metrics.getHeight() / 2;

        // draw the loss message in the center of the screen
        g.drawString(
            lossMessage,
            Panel.WIDTH / 2 - metrics.stringWidth(lossMessage) / 2, y
        );

        if (score > high) {
            high = score;

            String highScoreMsg = "\nNew High Score of " + high + "!";

            g.setColor(new Color(150, 0, 0));
            g.drawString(
                highScoreMsg,
                Panel.WIDTH / 2 - metrics.stringWidth(highScoreMsg) / 2,
                y + metrics.getHeight() + 10
            );
        }

        waitingForReset = true;
    }

    // update score display every 2500 ms
    public void drawScore(Graphics g) {
        score = ticksElapsed / 100;

        String score = String.valueOf(this.score);
        String high = "High: " + this.high;

        g.setColor(Color.black);
        g.setFont(new Font("Trebuchet MS", Font.BOLD, 30));

        FontMetrics metrics = g.getFontMetrics(g.getFont());

        // top margin
        int mt = metrics.getHeight();

        // draw the score in the top right corner of the screen
        g.drawString(score, Panel.WIDTH - metrics.stringWidth(score) - 10, mt);

        g.setFont(new Font("Trebuchet MS", Font.BOLD, 20));
        metrics = g.getFontMetrics(g.getFont());
        // draw the high score in the top left corner of the screen
        g.drawString(high, metrics.stringWidth(score) + 10, mt);
    }

    public int getPipeSpeed() {
        return pipeSpeed;
    }
}
