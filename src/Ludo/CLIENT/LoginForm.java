package Ludo.CLIENT;

import Ludo.COMMON.IAuthService;
import Ludo.COMMON.IFriendService;
import Ludo.COMMON.ILobbyService;
import Ludo.COMMON.User;

import javax.swing.*;
import java.awt.*;
import java.rmi.Naming;
import java.rmi.RemoteException;

public class LoginForm extends JFrame {
    private JLabel lblUsername;
    private JLabel lblPassword;
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JButton btnLogin;
    private JButton btnRegister;
    private JPanel LoginPanel;
    private JLabel lblStatus;
    private JButton btnExit;
    private JPanel CredentialsPanel;
    private IAuthService authService;

    public JPanel getMainPanel() {
        return LoginPanel;
    }

    private boolean refreshAuthService() {
        try {
            authService = (IAuthService) Naming.lookup("rmi://127.0.0.1:2000/auth");
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    public LoginForm(IAuthService authService) {

        this.authService = authService;

        // LOGIN BUTTON LOGIC
        btnLogin.addActionListener(e -> {

            // EXTRACT THE WRITTEN USERNAME AND PASSWORD
            String user = txtUsername.getText();
            String pass = new String(txtPassword.getPassword());

            // FIRST CASE : EMPTY FIELDS
            if (user.isEmpty() || pass.isEmpty()) {
                lblStatus.setText("Fields cannot be empty!");
                return;
            }

            // SECOND CASE : NON-EMPTY FIELDS
            try {
                if (!refreshAuthService()) {
                    lblStatus.setText("Server Connection Error!");
                    return;
                }

                // CALL THE SERVER TO CHECK IF THE USER EXISTS
                User authenticatedUser = authService.login(user, pass);

                if (authenticatedUser != null) {
                    // SUCCESS
                    navigateToLobby(authenticatedUser);
                } else {
                    // INVALID USERNAME | PASS
                    lblStatus.setText("Invalid credentials!");
                }
            }
            // SERVER CONNECTION IS LOST OR USER IS ALREADY ONLINE/IN-GAME
            catch (RemoteException ex) {
                if (ex.getMessage().contains("ALREADY_LOGGED_IN")) {
                    lblStatus.setText("User is already logged in elsewhere!");
                } else {
                    lblStatus.setText("Server Connection Error!");
                }
            }

        });

        // REGISTER BUTTON LOGIC
        btnRegister.addActionListener(e -> {

            // EXTRACT THE WRITTEN USERNAME AND PASSWORD
            String user = txtUsername.getText().trim();
            String pass = new String(txtPassword.getPassword());

            // FIRST CASE : EMPTY FIELDS
            if (user.isEmpty() || pass.isEmpty()) {
                lblStatus.setText("Fields cannot be empty!");
                return;
            }

            try {
                if (!refreshAuthService()) {
                    lblStatus.setText("Server Connection Error!");
                    return;
                }

                // CALL THE SERVER TO ATTEMPT REGISTRATION
                User newUser = authService.register(user, pass);

                if (newUser != null) {
                    // SUCCESS THE USER IS REGISTERED, SET TO ONLINE AND WILL AUTO-LOGIN
                    navigateToLobby(newUser);

                } else {
                    // FAILURE, USERNAME IS ALREADY IN USE
                    lblStatus.setText("Username '" + user + "' is already taken!");
                }
            }
            // SERVER CONNECTION IS LOST
            catch (RemoteException ex) {
                lblStatus.setText("Server Connection Error!");
            }
        });

        // EXIT BUTTON LOGIC
        btnExit.addActionListener(e -> {

            // 2. SHUT DOWN THE APP
            System.exit(0);
        });
    }

    private void createUIComponents() {
        // LOADING THE BACKGROUND
        LoginPanel = new BackgroundPanel("src/Ludo/CLIENT/ASSETS/BACKGROUND#0.png");

        // SET LAYOUT TO GRIDBAGLAYOUT
        CredentialsPanel = new RoundedPanel();
    }

    private void navigateToLobby(User authenticatedUser)
    {
        // SHOW LOADING STATE
        lblStatus.setForeground(new Color(0, 150, 0)); // Professional green color
        lblStatus.setText("Success! Loading Lobby...");

        // DISABLE BUTTONS
        btnLogin.setEnabled(false);
        btnRegister.setEnabled(false);
        btnExit.setEnabled(false);

        // CREATE TIMER
        Timer transitionTimer = new Timer(2000, new java.awt.event.ActionListener()
        {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                try
                {
                    // LOOKUP THE LOBBY SERVICE
                    ILobbyService lobbyService = (ILobbyService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/lobby");

                    // LOOKUP THE FRIEND SERVICE
                    IFriendService friendService = (IFriendService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/friend");

                    // INITIALIZE THE LOBBY FORM
                    LobbyForm lobbyUI = new LobbyForm(authenticatedUser, lobbyService, authService, friendService);

                    // SET UP THE NEW FRAME
                    JFrame lobbyFrame = new JFrame("Ludo - Game Lobby");
                    lobbyFrame.setUndecorated(true);
                    lobbyFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                    lobbyFrame.add(lobbyUI.getMainPanel());

                    // SHOW LOBBY & CLOSE LOGIN
                    lobbyFrame.setVisible(true);

                    // CLOSE LOGIN PANEL
                    Window loginWindow = SwingUtilities.getWindowAncestor(LoginPanel);
                    if (loginWindow != null) {
                        loginWindow.dispose();
                    }

                }
                catch (Exception ex)
                {
                    lblStatus.setText("Connection Error: Lobby Service not found.");
                    // RE-ENABLE BUTTONS IN CASE OF A CONNECTION ERROR
                    btnLogin.setEnabled(true);
                    btnRegister.setEnabled(true);
                    btnExit.setEnabled(true);
                    ex.printStackTrace();
                }
            }
        });

        transitionTimer.setRepeats(false); // Ensure it only runs once
        transitionTimer.start();

    }

}

// SCRIPT TO MAKE ROUND PANELS
class RoundedPanel extends JPanel
{
    public RoundedPanel() {
        setOpaque(false); // Keeps the corners transparent
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // White color with 180 transparency (out of 255)
        g2.setColor(new Color(255, 255, 255, 220));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 40, 40);
        g2.dispose();
    }
}




