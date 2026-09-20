package Ludo.CLIENT;

import Ludo.COMMON.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.rmi.RemoteException;
import java.util.ArrayList;

public class LobbyForm {

    //PANEL THAT HOLDS EVERYTHING
    private JPanel LobbyPanel;
    private JButton btnLogout;
    private JPanel StatsAndReplayPanel;
    private JLabel lblStats;
    private JLabel lblWins;
    private JLabel lblLosses;
    private JLabel lblWinRate;
    private JLabel lblLeaderboard;
    private JPanel leaderboardListPanel;
    private JPanel StatsHolderPanel;
    private JLabel lblFriendList;
    private JPanel FriendPanel;
    private JLabel lblAddFreind;
    private JTextField txtAddFriend;
    private JPanel AddFriendPanel;
    private JButton btnAddFriend;
    private JLabel lblAddFriendStatus;
    private JPanel NotificationHostPanel;
    private JPanel CurrentLobbyPanel;
    private JLabel txtCurrentLobby;
    private JPanel LobbyChatPanel;
    private JLabel txtLobbyChat;
    private JPanel gameOptionsContainer;
    private JPanel friendListContainer;

    private User currentUser;
    private ILobbyService lobbyService;
    private IAuthService authService;
    private IFriendService friendService;
    private JPanel lobbyPlayerContainer;
    private JButton btnLeaveLobby;
    private Lobby lastLobbyData;
    private JTextArea chatDisplay;
    private JTextField chatInput;
    private javax.swing.Timer serverHeartbeat;

    // GAME OPTIONS UI COMPONENTS
    private JPanel GameOptionsPanel;
    private JCheckBox chkSpectators, chkRollSix, chkBlock;
    private JSpinner spnTurnTime, spnPiecesToWin;
    private JButton btnStartGame;

    // FLAG TO PREVENT INFINITE RMI LOOPS DURING UI REFRESH
    private boolean isUpdatingFromServer = false;

    // CURRENT LOBBY ID=-1 SO USER CAN CREATE HIS OWN LOBBY
    private int currentLobbyID = -1;

    public LobbyForm(User user, ILobbyService service,IAuthService authService, IFriendService friend)
    {
        this(user, service, authService, friend, -1);
    }

    public LobbyForm(User user, ILobbyService service,IAuthService authService, IFriendService friend, int preferredLobbyID)
    {
        this.currentUser = user;
        this.lobbyService = service;
        this.authService = authService;
        this.friendService = friend;



        // INITIALIZE CONNECTION TO LOBBY
        try {
            ILobbyObserver myObserver = new LobbyObserverImpl(this);

            // Server returns the NEW ID (e.g., 100)
            this.currentLobbyID = lobbyService.joinLobby(preferredLobbyID, currentUser.getUsername());

            if (this.currentLobbyID != -1) {
                // Subscribe so we get FUTURE updates
                lobbyService.subscribe(this.currentLobbyID, currentUser.getUsername(), myObserver);

                // Now that the client observer exists, ask the server to deliver pending friend requests.
                deliverPendingFriendRequests();
                checkPendingReconnect();

                // RMI MAX: Pull the CURRENT state right now
                Lobby data = lobbyService.getLobbyData(this.currentLobbyID);
                if (data != null) {
                    // This manually triggers the UI to draw your name and the [HOST] tag
                    refreshLobbyUI(data);
                }

                System.out.println("DEBUG: Connected to Lobby #" + this.currentLobbyID);
            }
        } catch (RemoteException e) {
            System.err.println("Fatal Error: Could not connect to Lobby Service.");
            e.printStackTrace();
            handleLogout();
        }

        // RUN THE STATS LOGIC IMMEDIATELY
        displayUserStats();
        setupLeaderboardPanel();
        refreshLeaderboard();

        // LOGOUT BUTTON LOGIC
        btnLogout.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handleLogout();
            }
        });

        // NOTIFICATION
        setupNotificationEngine();

        setupFriendListUI();
        refreshFriendList();
        setupCurrentLobbyPanel();
        setupChatPanelUI();
        setupGameOptionsPanel();
        btnAddFriend.setFont(new Font("Agency FB", Font.BOLD, 16)); // Bigger again, with smaller margins so text stays visible
        btnAddFriend.setMargin(new Insets(2, 6, 2, 6));
        gameOptionsContainer.setLayout(new BorderLayout()); // This only affects the small container
        gameOptionsContainer.add(GameOptionsPanel, BorderLayout.CENTER);

        startServerHeartbeat();




        // --- ADD FRIEND LOGIC ---
        btnAddFriend.addActionListener(e -> {
            String targetUser = txtAddFriend.getText().trim();

            // 1. Basic Validation
            if (targetUser.isEmpty()) {
                lblAddFriendStatus.setText("Please enter a username.");
                lblAddFriendStatus.setForeground(Color.RED);
                return;
            }

            if (targetUser.equals(currentUser.getUsername())) {
                lblAddFriendStatus.setText("You can't add yourself!");
                lblAddFriendStatus.setForeground(Color.RED);
                return;
            }

            try {
                // 2. Call the Server
                boolean sent = friendService.sendFriendRequest(currentUser.getUsername(), targetUser);

                if (sent) {
                    lblAddFriendStatus.setText("Request sent to " + targetUser + "!");
                    lblAddFriendStatus.setForeground(new Color(0, 150, 0)); // Success Green
                    txtAddFriend.setText(""); // Clear the input field
                } else {
                    // This happens if user doesn't exist or is already a friend
                    lblAddFriendStatus.setText("User not found or already friend.");
                    lblAddFriendStatus.setForeground(Color.RED);
                }

                // 3. Optional: Clear the status message after 3 seconds so it's not stuck there
                Timer clearTimer = new Timer(3000, ae -> lblAddFriendStatus.setText(""));
                clearTimer.setRepeats(false);
                clearTimer.start();

            } catch (RemoteException ex) {
                lblAddFriendStatus.setText("Connection error!");
                ex.printStackTrace();
            }
        });

    }

    // LOGOUT HANDLER
    private void handleLogout() {
        try {
            // UNSUBSCRIBE
            if (currentLobbyID != -1)
            {
                lobbyService.unsubscribe(currentLobbyID, currentUser.getUsername());
            }

            // TELL THE SERVER THAT USER IS  OFFLINE
            boolean success = authService.logout(currentUser.getUsername());

            if (success) {
                // BACK TO LOG IN WINDOW
                JFrame loginFrame = new JFrame("Ludo Game - Login");
                loginFrame.setUndecorated(true);
                loginFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);

                LoginForm loginForm = new LoginForm(authService);
                loginFrame.add(loginForm.getMainPanel());
                loginFrame.setVisible(true);

                // 4. DISPOSE
                Window lobbyWindow = SwingUtilities.getWindowAncestor(LobbyPanel);
                if (lobbyWindow != null) {
                    lobbyWindow.dispose();
                }
            }
        } catch (RemoteException ex) {
            // If the server is already down, we should still let the user close the app
            System.out.println("Logout network error: " + ex.getMessage());
            System.exit(0);
        }
    }

    public JPanel getMainPanel()
    {
        return LobbyPanel;
    }


    private void deliverPendingFriendRequests() {
        try {
            friendService.deliverPendingFriendRequests(currentUser.getUsername());
            refreshFriendList();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    private void checkPendingReconnect() {
        try {
            IGameService gameService = (IGameService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/game");
            int pendingLobby = gameService.getPendingReconnectLobby(currentUser.getUsername());

            if (pendingLobby != -1) {
                showReconnectOffer(pendingLobby);
            }
        } catch (Exception ignored) {}
    }

    public void showReconnectOffer(int reconnectLobbyID) {
        JDialog dialog = new JDialog((Frame) null, "Reconnect", false);
        dialog.setUndecorated(true);

        RoundedPanel card = new RoundedPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(26, 34, 26, 34));

        JLabel title = new JLabel("GAME DISCONNECTED", SwingConstants.CENTER);
        title.setFont(new Font("Agency FB", Font.BOLD, 34));
        title.setForeground(new Color(40, 40, 40));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel msg = new JLabel("<html><div style='text-align:center;'>You disconnected from an active game.<br>Reconnect within 2 minutes or a loss will be saved.</div></html>");
        msg.setFont(new Font("Agency FB", Font.BOLD, 20));
        msg.setForeground(new Color(40, 40, 40));
        msg.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton reconnect = createListButton("RECONNECT", new Color(40, 167, 69));
        reconnect.setFont(new Font("Agency FB", Font.BOLD, 20));
        reconnect.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton decline = createListButton("DECLINE", new Color(220, 53, 69));
        decline.setFont(new Font("Agency FB", Font.BOLD, 20));
        decline.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.add(reconnect);
        buttons.add(decline);

        card.add(title);
        card.add(Box.createVerticalStrut(12));
        card.add(msg);
        card.add(Box.createVerticalStrut(18));
        card.add(buttons);

        dialog.setContentPane(card);
        dialog.pack();
        dialog.setLocationRelativeTo(LobbyPanel);

        Timer vanishTimer = new Timer(120000, e -> {
            dialog.dispose();
            try {
                IGameService gameService = (IGameService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/game");
                gameService.rejectReconnect(currentUser.getUsername());
                currentUser.addLoss();
                displayUserStats();
                refreshLeaderboard();
            } catch (Exception ignored) {}
        });
        vanishTimer.setRepeats(false);

        reconnect.addActionListener(e -> {
            vanishTimer.stop();
            dialog.dispose();
            openReconnectGameBoard(reconnectLobbyID);
        });

        decline.addActionListener(e -> {
            vanishTimer.stop();
            dialog.dispose();
            try {
                IGameService gameService = (IGameService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/game");
                gameService.rejectReconnect(currentUser.getUsername());
                currentUser.addLoss();
                displayUserStats();
                refreshLeaderboard();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(LobbyPanel, "Connection error while rejecting reconnect.");
            }
        });

        vanishTimer.start();
        dialog.setVisible(true);
    }

    private void openReconnectGameBoard(int reconnectLobbyID) {
        try {
            IGameService gameService = (IGameService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/game");

            lobbyService.unsubscribe(currentLobbyID, currentUser.getUsername());

            GameBoard gameBoard = new GameBoard(
                    currentUser,
                    reconnectLobbyID,
                    lobbyService,
                    authService,
                    friendService,
                    gameService,
                    false,
                    -1,
                    true
            );

            JFrame gameFrame = new JFrame("Ludo - Reconnected Game");
            gameFrame.setUndecorated(true);
            gameFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            gameFrame.add(gameBoard);
            gameFrame.setVisible(true);

            Window lobbyWindow = SwingUtilities.getWindowAncestor(LobbyPanel);
            if (lobbyWindow != null) {
                lobbyWindow.dispose();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(LobbyPanel, "Could not reconnect to the game.");
        }
    }

    private void startServerHeartbeat() {
        serverHeartbeat = new Timer(5000, e -> {
            try {
                lobbyService.getLobbyData(currentLobbyID);
            } catch (RemoteException ex) {
                handleServerConnectionLost();
            }
        });
        serverHeartbeat.start();
    }

    private void handleServerConnectionLost() {
        if (serverHeartbeat != null) {
            serverHeartbeat.stop();
        }

        JOptionPane.showMessageDialog(LobbyPanel, "Server connection lost. Returning to login screen.");

        LoginForm loginForm = new LoginForm(authService);

        JFrame loginFrame = new JFrame("Ludo Game - Login");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setUndecorated(true);
        loginFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        loginFrame.add(loginForm.getMainPanel());
        loginFrame.setVisible(true);

        Window lobbyWindow = SwingUtilities.getWindowAncestor(LobbyPanel);
        if (lobbyWindow != null) {
            lobbyWindow.dispose();
        }
    }

    private void createUIComponents()
    {
        // LOADING THE BACKGROUND
        LobbyPanel = new BackgroundPanel("src/Ludo/CLIENT/ASSETS/BACKGROUND#1.png");

        // LOADING THE STATS AND REPLAY PANEL
        StatsAndReplayPanel  = new RoundedPanel();

        // LOADING THE FRIENDS PANEL
        FriendPanel =  new RoundedPanel();

        //LOADING THE NOTIFICATION HOST PANEL
        NotificationHostPanel = new RoundedPanel();

        // LOADING THE LOBBY PANEL
        CurrentLobbyPanel = new  RoundedPanel();

        // LOADING THE LOBBY CHAT PANEL
        LobbyChatPanel = new RoundedPanel();

        // LOADING THE GAME OPTION PANEL
        GameOptionsPanel = new RoundedPanel();

    }

    // FUNCTION FOR THE NOTIFICATION ENGINE
    private void setupNotificationEngine() {
        // Keep CardLayout as the main manager for swapping notifications
        CardLayout cl = new CardLayout();
        NotificationHostPanel.setLayout(cl);
        NotificationHostPanel.setOpaque(false);

        // Theme Colors & Agency FB Font
        Color themeGreen = new Color(40, 167, 69);
        Color themeRed = new Color(220, 53, 69);
        // Agency FB for that sharp, professional look
        Font headerFont = new Font("Agency FB", Font.BOLD, 24);
        Font btnFont = new Font("Agency FB", Font.BOLD, 18);

        // --- THE CENTERING WRAPPER ---
        // This panel sits inside the CardLayout and uses GridBag to center the content
        JPanel centeringWrapper = new JPanel(new GridBagLayout());
        centeringWrapper.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.CENTER;

        // --- THE FRIEND CARD ---
        // This holds the label and buttons in one row
        JPanel friendCard = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        friendCard.setOpaque(false);

        JLabel lblMessage = new JLabel("NOTIFICATION TEXT");
        lblMessage.setName("msgLabel");
        lblMessage.setForeground(Color.BLACK);
        lblMessage.setFont(headerFont);

        JButton btnAccept = new JButton("ACCEPT");
        btnAccept.setFont(btnFont);
        btnAccept.setForeground(Color.WHITE);
        btnAccept.setBackground(themeGreen);
        btnAccept.setFocusPainted(false);
        btnAccept.setBorder(BorderFactory.createEmptyBorder(5, 20, 5, 20));
        btnAccept.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton btnReject = new JButton("REJECT");
        btnReject.setFont(btnFont);
        btnReject.setForeground(Color.WHITE);
        btnReject.setBackground(themeRed);
        btnReject.setFocusPainted(false);
        btnReject.setBorder(BorderFactory.createEmptyBorder(5, 20, 5, 20));
        btnReject.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Build the Card
        friendCard.add(lblMessage);
        friendCard.add(btnAccept);
        friendCard.add(btnReject);

        // Put card in wrapper, then wrapper in host
        centeringWrapper.add(friendCard, gbc);
        NotificationHostPanel.add(centeringWrapper, "FRIEND_REQ");

        NotificationHostPanel.setVisible(false);
    }

    // MANDATORY METHOD FOR OBSERVER
    public void refreshLobbyUI(Lobby updatedLobby) {
        this.lastLobbyData = updatedLobby;

        SwingUtilities.invokeLater(() -> {
            // --- SYNC GAME OPTIONS ---
            boolean amIOwner = updatedLobby.getOwner().getUsername().equals(currentUser.getUsername());

            // Only show the panel to the Host (Matches your screenshots)
            GameOptionsPanel.setVisible(amIOwner);

            LobbyPanel.revalidate();
            LobbyPanel.repaint();

            isUpdatingFromServer = true;

            chkRollSix.setSelected(updatedLobby.isMustRollSixToStart());
            chkBlock.setSelected(updatedLobby.isCanBlock());
            chkSpectators.setSelected(updatedLobby.isAllowSpectators());

            // Spinners
            spnTurnTime.setValue(updatedLobby.getTurnTimeLimit());
            spnPiecesToWin.setValue(updatedLobby.getPiecesToWin());

            isUpdatingFromServer = false;

            // Play Button logic
            btnStartGame.setEnabled(updatedLobby.getPlayers().size() >= 2);

            // No point showing Leave Lobby when the user is alone in their own lobby.
            if (btnLeaveLobby != null) {
                btnLeaveLobby.setVisible(updatedLobby.getPlayers().size() > 1);
            }

            // --- REBUILD PLAYER LIST ---
            lobbyPlayerContainer.removeAll();

            for (String playerName : updatedLobby.getPlayers().keySet()) {
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(false);
                row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0, 0, 0, 30)));

                // 1. LEFT SIDE: Name and Host Tag
                JLabel lblName = new JLabel(playerName.toUpperCase());
                lblName.setFont(new Font("Agency FB", Font.BOLD, 22));
                lblName.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 0));

                if (updatedLobby.getOwner().getUsername().equals(playerName)) {
                    lblName.setText(lblName.getText() + " [HOST]");
                    lblName.setForeground(new Color(184, 134, 11));
                }
                row.add(lblName, BorderLayout.WEST);

                // 2. RIGHT SIDE: Color Selector + Kick Button
                JPanel rightWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 5));
                rightWrapper.setOpaque(false);

                String playerColor = updatedLobby.getPlayers().get(playerName);

                if (amIOwner) {
                    // --- COLOR DROPDOWN (HOST ONLY) ---
                    String[] colorOptions = {"RED", "BLUE", "GREEN", "YELLOW"};
                    JComboBox<String> colorPicker = new JComboBox<>(colorOptions);
                    colorPicker.setSelectedItem(playerColor);
                    colorPicker.setFont(new Font("Agency FB", Font.BOLD, 16));

                    colorPicker.addActionListener(e -> {
                        if (isUpdatingFromServer) return;
                        String selected = (String) colorPicker.getSelectedItem();
                        if (!selected.equals(updatedLobby.getPlayers().get(playerName))) {
                            try {
                                lobbyService.selectColor(currentLobbyID, currentUser.getUsername(), playerName, selected);
                            } catch (RemoteException ex) { ex.printStackTrace(); }
                        }
                    });
                    rightWrapper.add(colorPicker);

                    // --- KICK BUTTON (HOST ONLY & NOT SELF) ---
                    if (!playerName.equals(currentUser.getUsername())) {
                        JButton btnKick = createListButton("KICK", new Color(220, 53, 69));
                        btnKick.addActionListener(e -> {
                            try {
                                lobbyService.kickPlayer(currentLobbyID, currentUser.getUsername(), playerName);
                            } catch (RemoteException ex) { ex.printStackTrace(); }
                        });
                        rightWrapper.add(btnKick);
                    }
                } else {
                    // --- COLORED LABEL (FOR NON-HOSTS) ---
                    JLabel lblColor = new JLabel(playerColor);
                    lblColor.setFont(new Font("Agency FB", Font.BOLD, 18));

                    // Color the text based on the assigned color
                    switch(playerColor) {
                        case "RED": lblColor.setForeground(new Color(220, 53, 69)); break;
                        case "BLUE": lblColor.setForeground(new Color(0, 123, 255)); break;
                        case "GREEN": lblColor.setForeground(new Color(40, 167, 69)); break;
                        case "YELLOW": lblColor.setForeground(new Color(204, 160, 0)); break;
                    }
                    rightWrapper.add(lblColor);
                }

                row.add(rightWrapper, BorderLayout.EAST);
                lobbyPlayerContainer.add(row);

            }

            if (updatedLobby.isGameStarted()) {
                openGameBoard();
                return;
            }


            lobbyPlayerContainer.revalidate();
            lobbyPlayerContainer.repaint();
            refreshFriendList();
            refreshLeaderboard();
        });
    }

    // MANDATORY METHOD FOR OBSERVER
    public void updateChatArea(String message) {
        SwingUtilities.invokeLater(() -> {
            // Append the message
            chatDisplay.append(message + "\n");

            // AUTO-SCROLL TO BOTTOM:
            // This ensures the user always sees the newest message
            chatDisplay.setCaretPosition(chatDisplay.getDocument().getLength());

            // Visual feedback (optional)
            LobbyChatPanel.repaint();
        });
    }

    // DISPLAY USER STATS
    private void displayUserStats() {
        // GET CURRENT USER DATA
        int wins = currentUser.getWins();
        int losses = currentUser.getLosses();

        // CALCULATE WINRATE
        double winRate = 0;
        if ((wins + losses) > 0) {
            winRate = ((double) wins / (wins + losses)) * 100;
        }

        // UPDATE UI
        lblWins.setText("Wins: " + wins);
        lblLosses.setText("Losses: " + losses);
        lblWinRate.setText(String.format("Win Rate: %.1f%%", winRate));
    }

    private void setupLeaderboardPanel() {
        // Rebuild the left panel in code because the original .form layout gives the
        // stats holder too much vertical space, hiding anything added below it.
        StatsAndReplayPanel.removeAll();
        StatsAndReplayPanel.setLayout(new BoxLayout(StatsAndReplayPanel, BoxLayout.Y_AXIS));
        StatsAndReplayPanel.setBorder(BorderFactory.createEmptyBorder(30, 26, 26, 26));

        lblStats.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblStats.setHorizontalAlignment(SwingConstants.CENTER);
        lblStats.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));

        JPanel statsRows = new JPanel();
        statsRows.setOpaque(false);
        statsRows.setLayout(new BoxLayout(statsRows, BoxLayout.Y_AXIS));
        statsRows.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel[] statLabels = {lblWinRate, lblWins, lblLosses};
        for (JLabel label : statLabels) {
            label.setFont(new Font("Agency FB", Font.BOLD, 28));
            label.setForeground(new Color(40, 40, 40));
            label.setAlignmentX(Component.CENTER_ALIGNMENT);
            label.setHorizontalAlignment(SwingConstants.LEFT);
            label.setMaximumSize(new Dimension(220, 34));
            statsRows.add(label);
            statsRows.add(Box.createVerticalStrut(6));
        }

        btnLogout.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnLogout.setMaximumSize(new Dimension(150, 42));

        lblLeaderboard = new JLabel("LEADERBOARD", SwingConstants.CENTER);
        lblLeaderboard.setFont(new Font("Agency FB", Font.BOLD, 38));
        lblLeaderboard.setForeground(new Color(40, 40, 40));
        lblLeaderboard.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblLeaderboard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        leaderboardListPanel = new JPanel();
        leaderboardListPanel.setOpaque(false);
        leaderboardListPanel.setLayout(new BoxLayout(leaderboardListPanel, BoxLayout.Y_AXIS));
        leaderboardListPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        StatsAndReplayPanel.add(lblStats);
        StatsAndReplayPanel.add(Box.createVerticalStrut(16));
        StatsAndReplayPanel.add(statsRows);
        StatsAndReplayPanel.add(Box.createVerticalStrut(8));
        StatsAndReplayPanel.add(btnLogout);
        StatsAndReplayPanel.add(Box.createVerticalStrut(28));
        StatsAndReplayPanel.add(lblLeaderboard);
        StatsAndReplayPanel.add(Box.createVerticalStrut(8));
        StatsAndReplayPanel.add(leaderboardListPanel);
        StatsAndReplayPanel.add(Box.createVerticalGlue());

        StatsAndReplayPanel.revalidate();
        StatsAndReplayPanel.repaint();
    }

    public void refreshLeaderboard() {
        if (leaderboardListPanel == null) return;

        try {
            ArrayList<User> topPlayers = authService.getLeaderboard();

            leaderboardListPanel.removeAll();

            if (topPlayers == null || topPlayers.isEmpty()) {
                JLabel empty = createLeaderboardLabel("NO WINS YET");
                leaderboardListPanel.add(empty);
            } else {
                int rank = 1;

                for (User user : topPlayers) {
                    int wins = user.getWins();
                    int losses = user.getLosses();
                    int games = wins + losses;
                    double winRate = games == 0 ? 0 : ((double) wins / games) * 100;

                    String text = String.format(
                            "#%d  %s  %.1f%%  (%dW/%dL)",
                            rank,
                            user.getUsername().toUpperCase(),
                            winRate,
                            wins,
                            losses
                    );

                    leaderboardListPanel.add(createLeaderboardLabel(text));
                    rank++;
                }
            }

            leaderboardListPanel.revalidate();
            leaderboardListPanel.repaint();
        } catch (RemoteException e) {
            leaderboardListPanel.removeAll();
            leaderboardListPanel.add(createLeaderboardLabel("LEADERBOARD UNAVAILABLE"));
            leaderboardListPanel.revalidate();
            leaderboardListPanel.repaint();
        }
    }

    private JLabel createLeaderboardLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Agency FB", Font.BOLD, 22));
        label.setForeground(new Color(40, 40, 40));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        return label;
    }



    public void showFriendRequestPopUp(String payload) {
        SwingUtilities.invokeLater(() -> {
            String displayMsg;
            String senderName;
            boolean isInvite = payload.startsWith("INVITE:");

            if (isInvite) {
                String[] parts = payload.split(":");
                int lobbyID = Integer.parseInt(parts[1]);
                senderName = parts[2];
                displayMsg = senderName.toUpperCase() + " INVITED YOU TO LOBBY #" + lobbyID;
            } else {
                senderName = payload;
                displayMsg = senderName.toUpperCase() + " WANTS TO BE YOUR FRIEND";
            }

            // Update UI components
            JPanel wrapper = (JPanel) NotificationHostPanel.getComponent(0);
            JPanel card = (JPanel) wrapper.getComponent(0);

            for (Component comp : card.getComponents()) {
                if (comp instanceof JLabel && "msgLabel".equals(comp.getName())) {
                    ((JLabel) comp).setText(displayMsg);
                }
                // Clear old listeners from buttons
                if (comp instanceof JButton) {
                    for (ActionListener al : ((JButton) comp).getActionListeners()) {
                        ((JButton) comp).removeActionListener(al);
                    }
                }
            }

            // Setup Buttons based on context
            JButton accept = (JButton) card.getComponent(1);
            JButton reject = (JButton) card.getComponent(2);

            accept.addActionListener(e -> {
                try {
                    if (isInvite) {
                        String[] parts = payload.split(":");
                        int targetLobbyID = Integer.parseInt(parts[1]);

                        // 1. TRY TO JOIN FIRST
                        int resultID = lobbyService.joinLobby(targetLobbyID, currentUser.getUsername());

                        if (resultID >= 100) {
                            // SUCCESS: Leave old, move to new
                            lobbyService.unsubscribe(currentLobbyID, currentUser.getUsername());
                            this.currentLobbyID = resultID;

                            // 2. SUBSCRIBE TO THE NEW SUCCESSFUL ID
                            ILobbyObserver myObserver = new LobbyObserverImpl(this);
                            lobbyService.subscribe(this.currentLobbyID, currentUser.getUsername(), myObserver);
                            deliverPendingFriendRequests();
                            checkPendingReconnect();
                            refreshLobbyUI(lobbyService.getLobbyData(this.currentLobbyID));

                        } else if (resultID == -2) {
                            // FULL: Show message, do NOT change currentLobbyID
                            JOptionPane.showMessageDialog(LobbyPanel, "That lobby is already full!");
                        }
                    } else {
                        friendService.respondToRequest(senderName, currentUser.getUsername());
                    }
                    NotificationHostPanel.setVisible(false);
                } catch (RemoteException ex) { ex.printStackTrace(); }
            });

            reject.addActionListener(e -> NotificationHostPanel.setVisible(false));

            CardLayout cl = (CardLayout) NotificationHostPanel.getLayout();
            cl.show(NotificationHostPanel, "FRIEND_REQ");
            NotificationHostPanel.setVisible(true);
        });
    }

    // LobbyForm.java

    private void setupButtonActions(JPanel card, String sender) {
        JButton accept = (JButton) card.getComponent(1);
        JButton reject = (JButton) card.getComponent(2);

        accept.addActionListener(e -> {
            try {
                boolean success = friendService.respondToRequest(sender, currentUser.getUsername());
                if (success) {
                    NotificationHostPanel.setVisible(false);
                    // THE MISSING LINK: Tell the UI to reload the list immediately!
                    refreshFriendList();
                }
            } catch (RemoteException ex) { ex.printStackTrace(); }
        });

        reject.addActionListener(e -> NotificationHostPanel.setVisible(false));
    }

    // --- FRIEND LIST METHODS ---

    private void setupFriendListUI() {
        // 1. Create the container for the actual rows
        // We create it here to avoid Designer conflicts
        friendListContainer = new JPanel();
        friendListContainer.setLayout(new BoxLayout(friendListContainer, BoxLayout.Y_AXIS));
        friendListContainer.setOpaque(false);

        // 2. Wrap it in a ScrollPane
        JScrollPane scrollPane = new JScrollPane(friendListContainer);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // This makes the scroll speed feel "modern"
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // 3. THE CLEAN FIX: Explicitly assign positions
        // This ensures the label stays TOP, add-friend stays BOTTOM,
        // and the list takes up every single pixel in the MIDDLE.
        FriendPanel.setLayout(new BorderLayout());

        FriendPanel.add(lblFriendList, BorderLayout.NORTH);    // Header
        FriendPanel.add(scrollPane, BorderLayout.CENTER);       // The List (Fills the gap)
        FriendPanel.add(AddFriendPanel, BorderLayout.SOUTH);    // Footer

        // 4. Refresh to show changes
        FriendPanel.revalidate();
        FriendPanel.repaint();
    }

    public void refreshFriendList() {
        if (friendListContainer == null) return; // Safety check

        SwingUtilities.invokeLater(() -> {
            try {
                ArrayList<User> friends = friendService.getFriendList(currentUser.getUsername());

                friendListContainer.removeAll();

                if (friends != null && !friends.isEmpty()) {
                    for (User friend : friends) {
                        JPanel row = createFriendRow(friend);
                        friendListContainer.add(row);
                        // Adds a gap between friend entries
                        friendListContainer.add(Box.createRigidArea(new Dimension(0, 10)));
                    }
                } else {
                    JLabel lblNone = new JLabel("NO FRIENDS ONLINE");
                    lblNone.setFont(new Font("Agency FB", Font.BOLD, 24)); // Much bigger
                    lblNone.setForeground(new Color(150, 150, 150)); // Solid gray, not transparent
                    lblNone.setAlignmentX(Component.CENTER_ALIGNMENT);
                    friendListContainer.add(Box.createVerticalGlue()); // Push it to the middle
                    friendListContainer.add(lblNone);
                    friendListContainer.add(Box.createVerticalGlue());
                }

                friendListContainer.revalidate();
                friendListContainer.repaint();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        });
    }

    private JPanel createFriendRow(User friend) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0, 0, 0, 30)));

        // LEFT PANEL: Name and Status (Restored from your old code)
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        leftPanel.setOpaque(false);

        JLabel lblName = new JLabel(friend.getUsername().toUpperCase());
        lblName.setFont(new Font("Agency FB", Font.BOLD, 22)); // Matches Lobby size
        lblName.setForeground(new Color(40, 40, 40));

        JLabel lblStatus = new JLabel();
        lblStatus.setFont(new Font("Agency FB", Font.BOLD, 16));

        if (friend.getStatus() == 0) {
            lblStatus.setText("OFFLINE");
            lblStatus.setForeground(Color.GRAY);
        } else if (friend.getStatus() == 1) {
            lblStatus.setText("ONLINE");
            lblStatus.setForeground(new Color(40, 167, 69));
        } else if (friend.getStatus() == -1) {
            lblStatus.setText("IN GAME");
            lblStatus.setForeground(new Color(255, 165, 0));
        }

        leftPanel.add(lblName);
        leftPanel.add(lblStatus);
        row.add(leftPanel, BorderLayout.WEST);

        // RIGHT PANEL: Action Buttons
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 8));
        rightPanel.setOpaque(false);

        // LOGIC: Check if already in the lobby
        boolean isAlreadyInLobby = lastLobbyData != null && lastLobbyData.getPlayers().containsKey(friend.getUsername());

        if (friend.getStatus() == 1) { // Online
            if (isAlreadyInLobby) {
                JLabel lblIn = new JLabel("IN LOBBY");
                lblIn.setFont(new Font("Agency FB", Font.BOLD, 18));
                lblIn.setForeground(new Color(100, 100, 100));
                rightPanel.add(lblIn);
            } else {
                JButton btnInvite = createListButton("INVITE", new Color(0, 123, 255));
                btnInvite.addActionListener(e -> {
                    try {
                        String inviteMsg = "INVITE:" + currentLobbyID + ":" + currentUser.getUsername();
                        friendService.sendFriendRequest(inviteMsg, friend.getUsername());
                        btnInvite.setText("SENT");
                        btnInvite.setEnabled(false);
                    } catch (RemoteException ex) { ex.printStackTrace(); }
                });
                rightPanel.add(btnInvite);
            }
        } else if (friend.getStatus() == -1 && friend.isSpectatableGame()) {
            JButton btnSpectate = createListButton("SPECTATE", new Color(255, 165, 0));
            btnSpectate.addActionListener(e -> openSpectatorBoard(friend.getCurrentLobbyID()));
            rightPanel.add(btnSpectate);
        }

        // REMOVE BUTTON
        JButton btnRemove = createListButton("REMOVE", new Color(220, 53, 69));
        btnRemove.addActionListener(e -> {
            try {
                if (friendService.removeFriend(currentUser.getUsername(), friend.getUsername())) {
                    refreshFriendList();
                }
            } catch (RemoteException ex) { ex.printStackTrace(); }
        });
        rightPanel.add(btnRemove);

        row.add(rightPanel, BorderLayout.EAST);
        return row;
    }

    private JButton createListButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Agency FB", Font.BOLD, 16));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // FUNCTION TO SET UP THE MIDDLE CURRENT LOBBY PANEL
    private void setupCurrentLobbyPanel() {
        // 1. Initialize the container for player rows
        lobbyPlayerContainer = new JPanel();
        lobbyPlayerContainer.setLayout(new BoxLayout(lobbyPlayerContainer, BoxLayout.Y_AXIS));
        lobbyPlayerContainer.setOpaque(false);

        // 2. Wrap it in a ScrollPane (so if 4 players join, it stays clean)
        JScrollPane scroll = new JScrollPane(lobbyPlayerContainer);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // 3. Setup the Main Middle Panel (CurrentLobbyPanel)
        // We use BorderLayout to keep the "CURRENT LOBBY" text at the top
        CurrentLobbyPanel.setLayout(new BorderLayout());

        // Make sure the label is centered and looks good
        txtCurrentLobby.setHorizontalAlignment(SwingConstants.CENTER);
        txtCurrentLobby.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        CurrentLobbyPanel.add(txtCurrentLobby, BorderLayout.NORTH);
        CurrentLobbyPanel.add(scroll, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        bottomPanel.setOpaque(false);
        btnLeaveLobby = createListButton("LEAVE LOBBY", new Color(220, 53, 69));
        btnLeaveLobby.setFont(new Font("Agency FB", Font.BOLD, 18));
        btnLeaveLobby.addActionListener(e -> leaveCurrentLobby());
        bottomPanel.add(btnLeaveLobby);

        CurrentLobbyPanel.add(bottomPanel, BorderLayout.SOUTH);
    }


    private void leaveCurrentLobby() {
        int choice = JOptionPane.showConfirmDialog(
                LobbyPanel,
                "Leave this lobby and create a new one?",
                "Leave Lobby",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (choice != JOptionPane.YES_OPTION) return;

        try {
            if (currentLobbyID != -1) {
                lobbyService.leaveLobby(currentLobbyID, currentUser.getUsername());
            }

            int newID = lobbyService.joinLobby(-1, currentUser.getUsername());
            this.currentLobbyID = newID;

            ILobbyObserver myObserver = new LobbyObserverImpl(this);
            lobbyService.subscribe(newID, currentUser.getUsername(), myObserver);
            deliverPendingFriendRequests();
            checkPendingReconnect();

            Lobby data = lobbyService.getLobbyData(newID);
            refreshLobbyUI(data);
        } catch (RemoteException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(LobbyPanel, "Could not leave lobby.");
        }
    }

    public void rejoinNewLobby() {
        try {
            JOptionPane.showMessageDialog(LobbyPanel, "You have been removed from the lobby. Creating a new one...");

            // 1. Join a fresh lobby (ID -1)
            int newID = lobbyService.joinLobby(-1, currentUser.getUsername());
            this.currentLobbyID = newID;

            // 2. Re-subscribe with a new observer
            ILobbyObserver myObserver = new LobbyObserverImpl(this);
            lobbyService.subscribe(newID, currentUser.getUsername(), myObserver);
            deliverPendingFriendRequests();
            checkPendingReconnect();

            // 3. Pull fresh data and redraw
            Lobby data = lobbyService.getLobbyData(newID);
            refreshLobbyUI(data);

        } catch (RemoteException e) {
            System.err.println("Error rejoining after kick");
            handleLogout(); // Fallback
        }
    }

    private void setupChatPanelUI() {
        // 1. Setup the Display Area (where messages appear)
        chatDisplay = new JTextArea();
        chatDisplay.setEditable(false);
        chatDisplay.setLineWrap(true);
        chatDisplay.setWrapStyleWord(true);
        chatDisplay.setOpaque(false);
        chatDisplay.setFont(new Font("Agency FB", Font.BOLD, 18));
        chatDisplay.setForeground(new Color(40, 40, 40));

        // 2. Wrap display in a transparent ScrollPane
        JScrollPane chatScroll = new JScrollPane(chatDisplay);
        chatScroll.setOpaque(false);
        chatScroll.getViewport().setOpaque(false);
        chatScroll.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);

        // 3. Setup the Input Field
        chatInput = new JTextField();
        chatInput.setFont(new Font("Agency FB", Font.BOLD, 20));
        chatInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0,0,0,50)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        // --- SEND LOGIC (PRESS ENTER) ---
        chatInput.addActionListener(e -> {
            String msg = chatInput.getText().trim();
            if (!msg.isEmpty()) {
                try {
                    // RMI CALL: Send to server
                    lobbyService.sendChatMessage(currentLobbyID, currentUser.getUsername(), msg);
                    chatInput.setText(""); // Clear field
                } catch (RemoteException ex) {
                    ex.printStackTrace();
                }
            }
        });

        // 4. Assemble the LobbyChatPanel (The RoundedPanel)
        LobbyChatPanel.setLayout(new BorderLayout());

        // Header (txtLobbyChat is likely your "LOBBY CHAT" label)
        txtLobbyChat.setHorizontalAlignment(SwingConstants.CENTER);
        txtLobbyChat.setFont(new Font("Agency FB", Font.BOLD, 24));

        LobbyChatPanel.add(txtLobbyChat, BorderLayout.NORTH);
        LobbyChatPanel.add(chatScroll, BorderLayout.CENTER);
        LobbyChatPanel.add(chatInput, BorderLayout.SOUTH);

        LobbyChatPanel.revalidate();
        LobbyChatPanel.repaint();
    }

    private void setupGameOptionsPanel() {
        GameOptionsPanel.setLayout(new GridBagLayout());
        GameOptionsPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 15, 10, 15);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        Font labelFont = new Font("Agency FB", Font.BOLD, 20);
        Color textColor = new Color(40, 40, 40);

        // --- TITLE ---
        JLabel lblTitle = new JLabel("GAME OPTIONS");
        lblTitle.setFont(new Font("Agency FB", Font.BOLD, 28));
        lblTitle.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        GameOptionsPanel.add(lblTitle, gbc);

        gbc.gridwidth = 1;

        // --- CHECKBOXES ---
        chkSpectators = new JCheckBox("ALLOW SPECTATORS", true);
        chkRollSix = new JCheckBox("MUST ROLL 6 TO START");
        chkBlock = new JCheckBox("ALLOW WALLS (BLOCK)");

        JCheckBox[] boxes = {chkSpectators, chkRollSix, chkBlock};
        for (int i = 0; i < boxes.length; i++) {
            boxes[i].setFont(labelFont);
            boxes[i].setOpaque(false);
            boxes[i].setForeground(textColor);
            boxes[i].setFocusPainted(false);

            // Send update to server instantly when clicked
            boxes[i].addActionListener(e -> sendRuleUpdate());

            gbc.gridy = i + 1;
            gbc.gridx = 0;
            gbc.gridwidth = 2;
            GameOptionsPanel.add(boxes[i], gbc);
        }

        gbc.gridwidth = 1; // Reset to 1 column for spinners

        // --- SPINNERS ---
        gbc.gridy = 4; gbc.gridx = 0;
        JLabel lblTime = new JLabel("TURN TIME (SEC):");
        lblTime.setFont(labelFont);
        GameOptionsPanel.add(lblTime, gbc);

        spnTurnTime = new JSpinner(new SpinnerNumberModel(30, 5, 120, 5));
        spnTurnTime.setFont(labelFont);
        spnTurnTime.addChangeListener(e -> sendRuleUpdate());
        gbc.gridx = 1;
        GameOptionsPanel.add(spnTurnTime, gbc);

        gbc.gridy = 5; gbc.gridx = 0;
        JLabel lblPieces = new JLabel("PIECES TO WIN:");
        lblPieces.setFont(labelFont);
        GameOptionsPanel.add(lblPieces, gbc);

        spnPiecesToWin = new JSpinner(new SpinnerNumberModel(4, 1, 4, 1));
        spnPiecesToWin.setFont(labelFont);
        spnPiecesToWin.addChangeListener(e -> sendRuleUpdate());
        gbc.gridx = 1;
        GameOptionsPanel.add(spnPiecesToWin, gbc);

        // --- PLAY BUTTON ---
        btnStartGame = new JButton("PLAY GAME");
        btnStartGame.setFont(new Font("Agency FB", Font.BOLD, 30));
        btnStartGame.setBackground(new Color(40, 167, 69));
        btnStartGame.setForeground(Color.WHITE);
        btnStartGame.setFocusPainted(false);
        btnStartGame.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnStartGame.addActionListener(e -> {
            try {
                lobbyService.startGame(currentLobbyID, currentUser.getUsername());
            } catch (RemoteException ex) { ex.printStackTrace(); }
        });

        gbc.gridy = 6; gbc.gridx = 0; gbc.gridwidth = 2;
        gbc.insets = new Insets(25, 10, 10, 10); // Extra padding on top of button
        GameOptionsPanel.add(btnStartGame, gbc);

        // Hide by default until Lobby data arrives and proves this user is the Host
        GameOptionsPanel.setVisible(false);
    }

    // HELPER: Syncs the current UI state to the Server
    private void sendRuleUpdate() {
        if (isUpdatingFromServer) return; // Prevent loop when server forces an update
        try {
            lobbyService.updateGameRules(
                    currentLobbyID,
                    currentUser.getUsername(),
                    (int) spnTurnTime.getValue(),
                    (int) spnPiecesToWin.getValue(),
                    chkRollSix.isSelected(),
                    chkBlock.isSelected(),
                    chkSpectators.isSelected()
            );
        } catch (RemoteException e) { e.printStackTrace(); }
    }

    private void openSpectatorBoard(int targetLobbyID) {
        try {
            if (targetLobbyID == -1 || !lobbyService.canSpectate(targetLobbyID)) {
                JOptionPane.showMessageDialog(LobbyPanel, "This game is not available for spectating.");
                refreshFriendList();
                return;
            }

            IGameService gameService = (IGameService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/game");

            GameBoard gameBoard = new GameBoard(
                    currentUser,
                    targetLobbyID,
                    lobbyService,
                    authService,
                    friendService,
                    gameService,
                    true,
                    currentLobbyID
            );

            JFrame gameFrame = new JFrame("Ludo - Spectator Mode");
            gameFrame.setUndecorated(true);
            gameFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            gameFrame.add(gameBoard);
            gameFrame.setVisible(true);

            Window lobbyWindow = SwingUtilities.getWindowAncestor(LobbyPanel);
            if (lobbyWindow != null) {
                lobbyWindow.dispose();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(LobbyPanel, "Could not spectate this game.");
        }
    }

    private void openGameBoard() {
        // Same pattern as navigateToLobby
        try {
            // LOOKUP THE GAME SERVICE
            IGameService gameService = (IGameService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/game");

            // CREATE GAME BOARD
            GameBoard gameBoard = new GameBoard(currentUser, currentLobbyID, lobbyService, authService, friendService, gameService);

            // SET UP THE NEW FRAME
            JFrame gameFrame = new JFrame("Ludo - Game");
            gameFrame.setUndecorated(true);
            gameFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            gameFrame.add(gameBoard);

            // SHOW GAME & CLOSE LOBBY
            gameFrame.setVisible(true);

            Window lobbyWindow = SwingUtilities.getWindowAncestor(LobbyPanel);
            if (lobbyWindow != null) {
                lobbyWindow.dispose();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(LobbyPanel, "Could not start game. Game service not found.");
        }
    }
}


