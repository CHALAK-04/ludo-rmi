package Ludo.CLIENT;

import javax.swing.*;
import java.awt.*;

//THIS SCRIPT IS OUT OF OUR PROJECT SCOPE BUT ITS ROLE IS TO SIMPLY LOAD THE BACKGROUND WITH THE HIGHEST QUALITY POSSIBLE
public class BackgroundPanel extends JPanel
{
    private Image backgroundImage;

    public BackgroundPanel(String fileName) {
        // LOAD IMAGE
        this.backgroundImage = new ImageIcon(fileName).getImage();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);


        Graphics2D g2d = (Graphics2D) g.create();


        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);


        if (backgroundImage != null) {
            g2d.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), this);
        }


        g2d.dispose();
    }

}

