package Ludo.CLIENT;

import Ludo.COMMON.IAuthService;

import javax.swing.*;
import java.rmi.Naming;

public class ClientMain
{
    public static void main(String[] args)
    {
        try
        {
            // LOOKUP THE AUTH SERVICE ON PORT 2000
            IAuthService authService = (IAuthService) Naming.lookup("rmi://127.0.0.1:2000/auth");

            System.out.println("------------------------");
            System.out.println("CONNECTED TO LUDO SERVER");
            System.out.println("------------------------");

            // CREATING THE WINDOW
            JFrame frame = new JFrame("Ludo Game - Login");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            // SETTING FULLSCREEN
            frame.setUndecorated(true);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

            // CREATE FORM OBJECT AND ADD PANEL TO ITS FRAME
            LoginForm loginForm = new LoginForm(authService);
            frame.add(loginForm.getMainPanel());
            frame.setVisible(true);

            System.out.println("-------------------");
            System.out.println("EXITED SUCCESSFULLY");
            System.out.println("-------------------");

        }
        catch (Exception e)
        {
            System.err.println("Client Connection Error: " + e.getMessage());
            e.printStackTrace();
        }

    }
}
