package Ludo.CLIENT;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class CoordinateMapper {
    public static void main(String[] args) throws Exception {
        // Load the 700x700 board image
        BufferedImage boardImage = ImageIO.read(new File("src/Ludo/CLIENT/ASSETS/BOARD.png"));

        JFrame frame = new JFrame("Click each square of RED's path");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(750, 750);
        frame.setResizable(false);

        JLabel label = new JLabel(new ImageIcon(boardImage));
        label.addMouseListener(new MouseAdapter() {
            int clickCount = 0;
            @Override
            public void mouseClicked(MouseEvent e) {
                // Coordinates relative to the image (not the window)
                int x = e.getX();
                int y = e.getY();
                System.out.println(clickCount + " : " + x + ", " + y);
                clickCount++;
                if (clickCount == 57) {  // 52 common + 5 home = 57
                    System.out.println("Done! Copy these values into your GameBoard.");
                }
            }
        });

        frame.add(label);
        frame.setVisible(true);
    }
}