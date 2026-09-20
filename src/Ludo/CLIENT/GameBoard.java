package Ludo.CLIENT;

import Ludo.COMMON.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.rmi.RemoteException;
import java.util.*;
import java.util.List;

public class GameBoard extends BackgroundPanel {

    // ---------- Board dimensions & styling ----------
    private static final int BOARD_SIZE = 700;
    private static final int PIECE_RADIUS = 15;
    private static final int CENTER_X = 350;
    private static final int CENTER_Y = 350;

    private static final Color TEXT_COLOR = new Color(30, 35, 45);
    private static final Color ACTIVE_GREEN = new Color(0, 150, 0);
    private static final Color BUTTON_GREEN = new Color(40, 167, 69);
    private static final Color BUTTON_GREEN_DARK = new Color(30, 130, 55);

    private static final Set<Integer> SAFE_SQUARES = new HashSet<>(Arrays.asList(0, 8, 13, 21, 26, 34, 39, 47));

    // ---------- Coordinate maps ----------
    private static final Map<String, Point[]> PIECE_PATHS = new HashMap<>();
    private static final Map<String, Point[]> BASE_POSITIONS = new HashMap<>();

    static {
        // ============ RED (original) ============
        Point[] redPath = {
                new Point(85,306), new Point(132,305), new Point(179,307), new Point(224,306), new Point(269,305),
                new Point(316,263), new Point(317,223), new Point(317,175), new Point(318,125), new Point(317,80),
                new Point(316,27),  new Point(366,28),  new Point(415,27),  new Point(415,74),  new Point(415,125),
                new Point(413,174), new Point(415,220), new Point(415,263), new Point(461,306), new Point(506,306),
                new Point(553,307), new Point(598,309), new Point(643,304), new Point(692,307), new Point(691,354),
                new Point(694,403), new Point(647,401), new Point(597,401), new Point(552,403), new Point(507,402),
                new Point(460,402), new Point(414,450), new Point(414,497), new Point(414,544), new Point(412,590),
                new Point(414,641), new Point(414,691), new Point(363,687), new Point(315,690), new Point(312,635),
                new Point(315,589), new Point(313,540), new Point(316,491), new Point(317,446), new Point(270,403),
                new Point(222,400), new Point(177,400), new Point(131,400), new Point(85,404),  new Point(38,402),
                new Point(38,353),  new Point(84,354),  new Point(131,354), new Point(176,354), new Point(224,355),
                new Point(267,354), new Point(318,353)
        };

        Point[] redBase = {
                new Point(110,97), new Point(202,96), new Point(200,186), new Point(109,187)
        };

        for (int i = 0; i < redPath.length; i++) {
            redPath[i].x -= 12;
            redPath[i].y -= 5;
        }
        for (int i = 0; i < redBase.length; i++) {
            redBase[i].x -= 12;
            redBase[i].y -= 5;
        }

        PIECE_PATHS.put("RED", redPath);
        BASE_POSITIONS.put("RED", redBase);

        // ============ BLUE (90° clockwise) ============
        Point[] bluePath = new Point[57];
        for (int i = 0; i < 57; i++) {
            int x = redPath[i].x, y = redPath[i].y;
            bluePath[i] = new Point(CENTER_X + (y - CENTER_Y), CENTER_Y - (x - CENTER_X));
        }
        Point[] blueBase = new Point[4];
        for (int i = 0; i < 4; i++) {
            int x = redBase[i].x, y = redBase[i].y;
            blueBase[i] = new Point(CENTER_X + (y - CENTER_Y), CENTER_Y - (x - CENTER_X));
        }
        PIECE_PATHS.put("BLUE", bluePath);
        BASE_POSITIONS.put("BLUE", blueBase);

        // ============ YELLOW (180°) ============
        Point[] yellowPath = new Point[57];
        for (int i = 0; i < 57; i++) {
            yellowPath[i] = new Point(700 - redPath[i].x, 700 - redPath[i].y);
        }
        Point[] yellowBase = new Point[4];
        for (int i = 0; i < 4; i++) {
            yellowBase[i] = new Point(700 - redBase[i].x, 700 - redBase[i].y);
        }
        PIECE_PATHS.put("YELLOW", yellowPath);
        BASE_POSITIONS.put("YELLOW", yellowBase);

        // ============ GREEN (270° clockwise) ============
        Point[] greenPath = new Point[57];
        for (int i = 0; i < 57; i++) {
            int x = redPath[i].x, y = redPath[i].y;
            greenPath[i] = new Point(CENTER_X - (y - CENTER_Y), CENTER_Y + (x - CENTER_X));
        }
        Point[] greenBase = new Point[4];
        for (int i = 0; i < 4; i++) {
            int x = redBase[i].x, y = redBase[i].y;
            greenBase[i] = new Point(CENTER_X - (y - CENTER_Y), CENTER_Y + (x - CENTER_X));
        }
        PIECE_PATHS.put("GREEN", greenPath);
        BASE_POSITIONS.put("GREEN", greenBase);
    }

    // ---------- Game state ----------
    private IGameService gameService;
    private ILobbyService lobbyService;
    private IAuthService authService;
    private IFriendService friendService;
    private User currentUser;
    private String myUsername;
    private int lobbyID;
    private GameState currentState;
    private List<Integer> validMoves;
    private int selectedPiece = -1;

    // Static observer to prevent garbage collection
    private static GameObserverImpl gameObserver;

    // ---------- UI components ----------
    private JPanel boardPanel;
    private JButton rollButton;
    private JButton exitButton;
    private JButton returnToLobbyButton;
    private JButton sendButton;
    private JLabel turnLabel, diceLabel;
    private JLabel playerInfoLabel;
    private JLabel timerLabel;
    private JLabel playersLabel;
    private JLabel rulesLabel;
    private JTextArea chatDisplay;
    private JTextField chatInput;

    // ---------- Local timer sync ----------
    private long lastUpdateTime;
    private int serverRemainingAtUpdate;
    private javax.swing.Timer localTimer;
    private javax.swing.Timer serverHeartbeat;

    // ---------- Board drawing ----------
    private Image boardImage;
    private Rectangle boardBounds = new Rectangle(0, 0, BOARD_SIZE, BOARD_SIZE);
    private int spectatorCount = 0;
    private boolean localWinApplied = false;
    private boolean localLossApplied = false;
    private String lobbyOwnerName = null;
    private JDialog activeEndDialog = null;
    private boolean spectatorMode = false;
    private boolean reconnectMode = false;
    private int returnLobbyID = -1;

    // ---------- Constructor ----------
    public GameBoard(User currentUser, int lobbyID, ILobbyService lobbyService,
                     IAuthService authService, IFriendService friendService,
                     IGameService gameService) {
        this(currentUser, lobbyID, lobbyService, authService, friendService, gameService, false, -1, false);
    }

    public GameBoard(User currentUser, int lobbyID, ILobbyService lobbyService,
                     IAuthService authService, IFriendService friendService,
                     IGameService gameService, boolean spectatorMode, int returnLobbyID) {
        this(currentUser, lobbyID, lobbyService, authService, friendService, gameService, spectatorMode, returnLobbyID, false);
    }

    public GameBoard(User currentUser, int lobbyID, ILobbyService lobbyService,
                     IAuthService authService, IFriendService friendService,
                     IGameService gameService, boolean spectatorMode, int returnLobbyID, boolean reconnectMode) {
        // Reuse the same high-quality background loader used by the lobby/authentication screens.
        super("src/Ludo/CLIENT/ASSETS/BACKGROUND#1.png");

        this.currentUser = currentUser;
        this.myUsername = currentUser.getUsername();
        this.lobbyID = lobbyID;
        this.lobbyService = lobbyService;
        this.authService = authService;
        this.friendService = friendService;
        this.gameService = gameService;
        this.spectatorMode = spectatorMode;
        this.reconnectMode = reconnectMode;
        this.returnLobbyID = returnLobbyID;
        this.boardImage = new ImageIcon("src/Ludo/CLIENT/ASSETS/BOARD.png").getImage();

        setLayout(new BorderLayout(24, 24));
        setBorder(new EmptyBorder(24, 24, 24, 24));
        setOpaque(false);

        JPanel leftWrapper = new JPanel(new GridBagLayout());
        leftWrapper.setOpaque(false);
        leftWrapper.add(createGameInfoPanel());
        add(leftWrapper, BorderLayout.WEST);

        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);
        boardPanel = createBoardPanel();
        centerWrapper.add(boardPanel);
        add(centerWrapper, BorderLayout.CENTER);

        JPanel rightWrapper = new JPanel(new GridBagLayout());
        rightWrapper.setOpaque(false);
        rightWrapper.add(createChatPanel());
        add(rightWrapper, BorderLayout.EAST);

        JPanel bottomWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        bottomWrapper.setOpaque(false);
        bottomWrapper.setBorder(new EmptyBorder(0, 40, 40, 0));

        // Bottom-left action buttons, kept away from screen borders.
        exitButton.setPreferredSize(new Dimension(130, 42));
        exitButton.setFont(new Font("Agency FB", Font.BOLD, 20));
        bottomWrapper.add(exitButton);

        returnToLobbyButton = createLobbyStyleButton("RETURN TO LOBBY", BUTTON_GREEN);
        returnToLobbyButton.setFont(new Font("Agency FB", Font.BOLD, 18));
        returnToLobbyButton.setPreferredSize(new Dimension(170, 42));
        returnToLobbyButton.setVisible(false);
        returnToLobbyButton.addActionListener(e -> returnWinnerToLobby());
        bottomWrapper.add(returnToLobbyButton);

        add(bottomWrapper, BorderLayout.SOUTH);

        if (spectatorMode) {
            exitButton.setText("EXIT GAME");
            rollButton.setEnabled(false);
            chatInput.setText("READ ONLY");
            chatInput.setEnabled(false);
            sendButton.setEnabled(false);
        }

        try {
            gameObserver = new GameObserverImpl(this);   // static – never garbage collected

            if (spectatorMode) {
                boolean added = lobbyService.addSpectator(lobbyID, myUsername, gameObserver);
                if (!added) {
                    JOptionPane.showMessageDialog(this, "This game is no longer available for spectating.");
                    returnToLobbyButton.setVisible(true);
                } else {
                    gameService.subscribe(lobbyID, myUsername, gameObserver);
                }
            } else if (reconnectMode) {
                boolean reconnected = gameService.reconnectToGame(lobbyID, myUsername, gameObserver);
                if (!reconnected) {
                    JOptionPane.showMessageDialog(this, "Reconnect window expired or game already ended.");
                    returnToLobbyButton.setVisible(true);
                } else {
                    lobbyService.subscribe(lobbyID, myUsername, gameObserver);
                }
            } else {
                gameService.subscribe(lobbyID, myUsername, gameObserver);
                lobbyService.subscribe(lobbyID, myUsername, gameObserver);
            }

            Lobby lobby = lobbyService.getLobbyData(lobbyID);
            if (lobby != null) {
                refreshLobbyInfo(lobby);

                if (lobby.getChatHistory() != null) {
                    for (String msg : lobby.getChatHistory()) {
                        chatDisplay.append(msg + "\n");
                    }
                    chatDisplay.setCaretPosition(chatDisplay.getDocument().getLength());
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Cannot connect to game server.");
        }

        localTimer = new javax.swing.Timer(1000, e -> updateTimerLabel());
        localTimer.start();

        startServerHeartbeat();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        drawSpectatorCounter(g);
    }

    private void drawSpectatorCounter(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        String text = "SPECTATORS : " + spectatorCount;
        Font font = new Font("Agency FB", Font.BOLD, 26);
        g2.setFont(font);

        FontMetrics fm = g2.getFontMetrics();
        int margin = 40;
        int x = getWidth() - fm.stringWidth(text) - margin;
        int y = getHeight() - margin;

        // Small shadow keeps the white Agency text readable on bright parts of the background.
        g2.setColor(new Color(0, 0, 0, 110));
        g2.drawString(text, x + 2, y + 2);

        g2.setColor(Color.WHITE);
        g2.drawString(text, x, y);

        g2.dispose();
    }



    private JButton createLobbyStyleButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Agency FB", Font.BOLD, 16));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }


    private void startServerHeartbeat() {
        serverHeartbeat = new javax.swing.Timer(5000, e -> {
            try {
                gameService.getGameState(lobbyID);
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

        if (localTimer != null) {
            localTimer.stop();
        }

        JOptionPane.showMessageDialog(this, "Server connection lost. Returning to login screen.");

        LoginForm loginForm = new LoginForm(authService);

        JFrame loginFrame = new JFrame("Ludo Game - Login");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setUndecorated(true);
        loginFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        loginFrame.add(loginForm.getMainPanel());
        loginFrame.setVisible(true);

        Window gameWindow = SwingUtilities.getWindowAncestor(this);
        if (gameWindow != null) {
            gameWindow.dispose();
        }
    }

    private JPanel createBoardPanel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawBoard(g);
                if (currentState != null) {
                    drawValidMoves(g);
                    drawPieces(g);
                }
            }
        };
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(740, 740));
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Point logicalPoint = screenToBoardPoint(e.getX(), e.getY());
                if (logicalPoint != null) {
                    handleBoardClick(logicalPoint.x, logicalPoint.y);
                } else if (selectedPiece != -1) {
                    selectedPiece = -1;
                    repaint();
                }
            }
        });
        return panel;
    }

    private JPanel createGameInfoPanel() {
        RoundedPanel panel = new RoundedPanel();
        panel.setPreferredSize(new Dimension(320, 680));
        panel.setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(22, 24, 22, 24));
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("GAME INFO", SwingConstants.CENTER);
        title.setFont(new Font("Agency FB", Font.BOLD, 32));
        title.setForeground(TEXT_COLOR);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        exitButton = createLobbyStyleButton("EXIT GAME", new Color(220, 53, 69));
        exitButton.setFont(new Font("Agency FB", Font.BOLD, 18));
        exitButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        exitButton.addActionListener(e -> confirmExitGame());

        rollButton = new JButton("ROLL DICE");
        rollButton.setFont(new Font("Agency FB", Font.BOLD, 24));
        rollButton.setFocusPainted(false);
        rollButton.setBorderPainted(false);
        rollButton.setOpaque(true);
        rollButton.setBackground(BUTTON_GREEN);
        rollButton.setForeground(Color.WHITE);
        rollButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        rollButton.setPreferredSize(new Dimension(150, 42));
        rollButton.setMaximumSize(new Dimension(150, 42));
        rollButton.setEnabled(false);
        rollButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        rollButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (rollButton.isEnabled()) rollButton.setBackground(BUTTON_GREEN_DARK);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                rollButton.setBackground(BUTTON_GREEN);
            }
        });
        rollButton.addActionListener(e -> rollDice());

        playerInfoLabel = infoLabel("You: " + myUsername);
        turnLabel = infoLabel("Current Turn: -");
        diceLabel = infoLabel("Dice: -");
        timerLabel = infoLabel("Time: --");

        JLabel playersTitle = sectionLabel("PLAYERS");
        playersLabel = new JLabel("<html>Waiting for players...</html>");
        playersLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        playersLabel.setForeground(TEXT_COLOR);
        playersLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel rulesTitle = sectionLabel("RULES");
        rulesLabel = new JLabel("<html>Rules will appear here.</html>");
        rulesLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        rulesLabel.setForeground(TEXT_COLOR);
        rulesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(title);
        content.add(Box.createVerticalStrut(16));
        content.add(rollButton);
        content.add(Box.createVerticalStrut(18));
        content.add(playerInfoLabel);
        content.add(Box.createVerticalStrut(8));
        content.add(turnLabel);
        content.add(Box.createVerticalStrut(8));
        content.add(diceLabel);
        content.add(Box.createVerticalStrut(8));
        content.add(timerLabel);
        content.add(Box.createVerticalStrut(20));
        content.add(playersTitle);
        content.add(Box.createVerticalStrut(8));
        content.add(playersLabel);
        content.add(Box.createVerticalStrut(20));
        content.add(rulesTitle);
        content.add(Box.createVerticalStrut(8));
        content.add(rulesLabel);
        content.add(Box.createVerticalGlue());

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createChatPanel() {
        RoundedPanel panel = new RoundedPanel();
        panel.setPreferredSize(new Dimension(330, 680));
        panel.setLayout(new BorderLayout());

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(22, 24, 22, 24));

        JLabel title = new JLabel("CHAT", SwingConstants.CENTER);
        title.setFont(new Font("Agency FB", Font.BOLD, 32));
        title.setForeground(TEXT_COLOR);

        chatDisplay = new JTextArea();
        chatDisplay.setEditable(false);
        chatDisplay.setLineWrap(true);
        chatDisplay.setWrapStyleWord(true);
        chatDisplay.setOpaque(false);
        chatDisplay.setFont(new Font("Agency FB", Font.BOLD, 18));
        chatDisplay.setForeground(TEXT_COLOR);

        JScrollPane chatScroll = new JScrollPane(chatDisplay);
        chatScroll.setOpaque(false);
        chatScroll.getViewport().setOpaque(false);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());

        chatInput = new JTextField();
        chatInput.setFont(new Font("Agency FB", Font.BOLD, 18));
        chatInput.addActionListener(e -> sendChat());

        sendButton = createLobbyStyleButton("SEND", BUTTON_GREEN);
        sendButton.addActionListener(e -> sendChat());

        JPanel inputPanel = new JPanel(new BorderLayout(8, 0));
        inputPanel.setOpaque(false);
        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        content.add(title, BorderLayout.NORTH);
        content.add(chatScroll, BorderLayout.CENTER);
        content.add(inputPanel, BorderLayout.SOUTH);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JLabel infoLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Agency FB", Font.BOLD, 22));
        label.setForeground(TEXT_COLOR);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Agency FB", Font.BOLD, 24));
        label.setForeground(TEXT_COLOR);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    // ---------- Called by the server (the ONLY external update path) ----------
    public void refreshBoard(GameState newState) {
        SwingUtilities.invokeLater(() -> {
            this.currentState = newState;
            serverRemainingAtUpdate = newState.getRemainingTimeSeconds();
            lastUpdateTime = System.currentTimeMillis();
            updateGameUI();
            repaint();
        });
    }

    public void showDiceRoll(String player, int value) {
        // Dice value is already displayed from GameState in updateGameUI().
        // Method kept for the RMI callback contract.
    }

    public void showGameEnded(String winner, String loser) {
        SwingUtilities.invokeLater(() -> {
            boolean iWon = winner != null && winner.equals(myUsername);
            boolean iLost = loser != null && loser.equals(myUsername);
            boolean amHost = !spectatorMode && lobbyOwnerName != null && lobbyOwnerName.equals(myUsername);

            if (!spectatorMode && iWon && !localWinApplied) {
                currentUser.addWin();
                localWinApplied = true;
            }

            if (!spectatorMode && iLost && !localLossApplied) {
                currentUser.addLoss();
                localLossApplied = true;
            }

            exitButton.setVisible(false);

            boolean hostLeftTheGame = !spectatorMode
                    && iWon
                    && lobbyOwnerName != null
                    && loser != null
                    && loser.equals(lobbyOwnerName)
                    && !amHost;

            if (spectatorMode) {
                returnToLobbyButton.setVisible(true);
                showEndDialog(winner, loser, false, true);
            } else if (amHost) {
                showEndDialog(winner, loser, true, false);
            } else if (hostLeftTheGame) {
                showEndDialog(winner, loser, false, true);
            } else {
                showEndDialog(winner, loser, false, false);
            }

            returnToLobbyButton.getParent().revalidate();
            returnToLobbyButton.getParent().repaint();
        });
    }

    private void showEndDialog(String winner, String loser, boolean hostControlsReturn, boolean allowSingleReturn) {
        if (activeEndDialog != null) {
            activeEndDialog.dispose();
        }

        JDialog dialog = new JDialog((Frame) null, "Game Results", false);
        activeEndDialog = dialog;
        dialog.setUndecorated(true);

        RoundedPanel card = new RoundedPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(26, 34, 26, 34));

        JLabel title = new JLabel("GAME FINISHED", SwingConstants.CENTER);
        title.setFont(new Font("Agency FB", Font.BOLD, 34));
        title.setForeground(TEXT_COLOR);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel winnerLabel = new JLabel("WINNER: " + (winner == null ? "-" : winner.toUpperCase()), SwingConstants.CENTER);
        winnerLabel.setFont(new Font("Agency FB", Font.BOLD, 26));
        winnerLabel.setForeground(BUTTON_GREEN);
        winnerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel loserLabel = new JLabel("LAST PLACE: " + (loser == null ? "-" : loser.toUpperCase()), SwingConstants.CENTER);
        loserLabel.setFont(new Font("Agency FB", Font.BOLD, 24));
        loserLabel.setForeground(new Color(220, 53, 69));
        loserLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel infoLabel = new JLabel("<html><div style='text-align:center;'>Winner gets +1 win.<br>Last place gets +1 loss.<br>Middle players receive no score change.</div></html>", SwingConstants.CENTER);
        infoLabel.setFont(new Font("Agency FB", Font.BOLD, 18));
        infoLabel.setForeground(TEXT_COLOR);
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(title);
        card.add(Box.createVerticalStrut(14));
        card.add(winnerLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(loserLabel);
        card.add(Box.createVerticalStrut(14));
        card.add(infoLabel);
        card.add(Box.createVerticalStrut(18));

        JButton btn;
        if (hostControlsReturn) {
            btn = createLobbyStyleButton("RETURN ALL TO LOBBY", BUTTON_GREEN);
            btn.setFont(new Font("Agency FB", Font.BOLD, 20));
            btn.addActionListener(e -> {
                dialog.dispose();
                hostReturnGameToLobby();
            });
        } else if (allowSingleReturn || spectatorMode) {
            btn = createLobbyStyleButton("RETURN TO LOBBY", BUTTON_GREEN);
            btn.setFont(new Font("Agency FB", Font.BOLD, 20));
            btn.addActionListener(e -> {
                dialog.dispose();
                returnWinnerToLobby();
            });
        } else {
            btn = createLobbyStyleButton("WAIT FOR HOST", new Color(120, 120, 120));
            btn.setFont(new Font("Agency FB", Font.BOLD, 20));
            btn.setEnabled(false);
        }

        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(btn);

        dialog.setContentPane(card);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public void refreshLobbyInfo(Lobby lobby) {
        if (lobby == null || lobby.getSpectators() == null) {
            spectatorCount = 0;
        } else {
            spectatorCount = lobby.getSpectators().size();
            if (lobby.getOwner() != null) {
                lobbyOwnerName = lobby.getOwner().getUsername();
            }
        }
        repaint();
    }

    // ---------- UI updates ----------
    private void updateGameUI() {
        if (currentState == null) return;
        String currentTurn = currentState.getCurrentTurn();
        boolean myTurn = currentTurn != null && currentTurn.equals(myUsername);
        boolean rolled = currentState.hasRolled();

        rollButton.setEnabled(!spectatorMode && myTurn && !rolled);

        turnLabel.setText("Current Turn: " + (currentTurn != null ? currentTurn.toUpperCase() : "-"));
        diceLabel.setText("Dice: " + (currentState.getLastDiceRoll() > 0 ? currentState.getLastDiceRoll() : "-"));

        if (spectatorMode) {
            playerInfoLabel.setText("<html>Spectating Lobby #" + lobbyID + "</html>");
        } else {
            String myColor = currentState.getPlayerColors().get(myUsername);
            playerInfoLabel.setText("<html>You: " + escapeHtml(myUsername) + "  " + colorDot(myColor) + " " + escapeHtml(myColor) + "</html>");
        }

        updatePlayersPanel();
        updateRulesPanel();

        if (!spectatorMode && myTurn && rolled) {
            try {
                validMoves = gameService.getValidMoves(lobbyID, myUsername);
            } catch (RemoteException e) {
                validMoves = List.of();
            }
        } else {
            validMoves = List.of();
        }

        updateTimerLabel();
    }

    private void updatePlayersPanel() {
        StringBuilder html = new StringBuilder("<html><body style='width:245px;'>");

        for (String player : currentState.getTurnOrder()) {
            String colorName = currentState.getPlayerColors().get(player);
            boolean isCurrent = player.equals(currentState.getCurrentTurn());
            int score = countFinishedPieces(player);

            html.append("<div style='margin-bottom:8px;'>")
                    .append(colorDot(colorName))
                    .append(" ");

            if (isCurrent) {
                html.append("<span style='color:#009600;'><b>")
                        .append(escapeHtml(player))
                        .append(" - current</b></span>");
            } else {
                html.append("<b>").append(escapeHtml(player)).append("</b>");
            }

            html.append("<br>&nbsp;&nbsp;Score: ")
                    .append(score)
                    .append("/")
                    .append(currentState.getPiecesToWin())
                    .append(" | ")
                    .append(escapeHtml(colorName))
                    .append("</div>");
        }

        html.append("</body></html>");
        playersLabel.setText(html.toString());
    }

    private void updateRulesPanel() {
        String html = "<html><body style='width:245px;'>"
                + "• Roll 6 to leave base: <b>" + (currentState.isMustRollSix() ? "Yes" : "No") + "</b><br>"
                + "• A 6 gives another roll.<br>"
                + "• Safe squares cannot be eaten.<br>"
                + "• Blocks: <b>" + (currentState.isCanBlock() ? "Enabled" : "Disabled") + "</b><br>"
                + "• Pieces to win: <b>" + currentState.getPiecesToWin() + "</b><br>"
                + "• Click a piece, then click its highlighted destination."
                + "</body></html>";

        rulesLabel.setText(html);
    }

    private void updateTimerLabel() {
        if (currentState == null || currentState.isGameOver()) {
            timerLabel.setText("Time: --");
            return;
        }
        long elapsed = (System.currentTimeMillis() - lastUpdateTime) / 1000;
        long remaining = serverRemainingAtUpdate - elapsed;
        if (remaining < 0) remaining = 0;
        timerLabel.setText("Time: " + remaining + "s");
    }

    private int countFinishedPieces(String username) {
        int[] pieces = currentState.getPiecePositions().get(username);
        if (pieces == null) return 0;

        int score = 0;
        for (int p : pieces) {
            if (p == 56) score++;
        }
        return score;
    }

    // ---------- Drawing ----------
    private void drawBoard(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int margin = 20;
        int maxSize = Math.min(boardPanel.getWidth() - margin * 2, boardPanel.getHeight() - margin * 2);

        // Keep BOARD.png at its native 700x700 whenever possible.
        // This avoids stretching a 700px image and preserves the best available quality.
        int renderSize = Math.min(BOARD_SIZE, maxSize);

        int x = (boardPanel.getWidth() - renderSize) / 2;
        int y = (boardPanel.getHeight() - renderSize) / 2;
        boardBounds = new Rectangle(x, y, renderSize, renderSize);

        g2.setColor(new Color(0, 0, 0, 35));
        g2.fillRoundRect(x + 8, y + 10, renderSize, renderSize, 28, 28);

        g2.drawImage(boardImage, x, y, renderSize, renderSize, null);

        g2.dispose();
    }

    private void drawPieces(Graphics g) {
        Graphics2D raw = (Graphics2D) g.create();
        raw.translate(boardBounds.x, boardBounds.y);

        double scale = boardBounds.width / (double) BOARD_SIZE;
        raw.scale(scale, scale);

        Graphics2D g2 = raw;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Map.Entry<String, int[]> entry : currentState.getPiecePositions().entrySet()) {
            String player = entry.getKey();
            int[] pieces = entry.getValue();
            String colorName = currentState.getPlayerColors().get(player);
            Color pieceColor = getColor(colorName);
            Map<Integer, List<Integer>> groups = new HashMap<>();

            for (int i = 0; i < 4; i++) {
                groups.computeIfAbsent(pieces[i], k -> new ArrayList<>()).add(i);
            }

            for (Map.Entry<Integer, List<Integer>> group : groups.entrySet()) {
                int pos = group.getKey();
                List<Integer> indices = group.getValue();
                int count = indices.size();

                if (pos == -1) {
                    for (int idx : indices) {
                        Point p = BASE_POSITIONS.get(colorName)[idx];
                        drawPieceCircle(g2, p, pieceColor, null);
                    }
                } else {
                    Point p = PIECE_PATHS.get(colorName)[pos];
                    String label = count > 1 ? "x" + count : null;
                    drawPieceCircle(g2, p, pieceColor, label);
                }
            }
        }

        if (selectedPiece != -1 && currentState.getCurrentTurn().equals(myUsername)) {
            int[] myPieces = currentState.getPiecePositions().get(myUsername);
            String myColor = currentState.getPlayerColors().get(myUsername);
            int pos = myPieces[selectedPiece];
            Point p = (pos == -1) ? BASE_POSITIONS.get(myColor)[selectedPiece] : PIECE_PATHS.get(myColor)[pos];

            g2.setColor(Color.CYAN);
            g2.setStroke(new BasicStroke(3));
            g2.drawOval(p.x - PIECE_RADIUS - 3, p.y - PIECE_RADIUS - 3, 2 * PIECE_RADIUS + 6, 2 * PIECE_RADIUS + 6);
        }

        raw.dispose();
    }

    private void drawPieceCircle(Graphics2D g2, Point p, Color color, String label) {
        g2.setColor(color);
        g2.fillOval(p.x - PIECE_RADIUS, p.y - PIECE_RADIUS, 2 * PIECE_RADIUS, 2 * PIECE_RADIUS);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2));
        g2.drawOval(p.x - PIECE_RADIUS, p.y - PIECE_RADIUS, 2 * PIECE_RADIUS, 2 * PIECE_RADIUS);

        if (label != null) {
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Agency FB", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int textX = p.x - fm.stringWidth(label) / 2;
            int textY = p.y + fm.getAscent() / 2 - 2;
            g2.drawString(label, textX, textY);
        }
    }

    private void drawValidMoves(Graphics g) {
        if (validMoves == null || validMoves.isEmpty()) return;
        if (selectedPiece == -1 || !validMoves.contains(selectedPiece)) return;

        Graphics2D raw = (Graphics2D) g.create();
        raw.translate(boardBounds.x, boardBounds.y);

        double scale = boardBounds.width / (double) BOARD_SIZE;
        raw.scale(scale, scale);

        Graphics2D g2 = raw;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        String myColor = currentState.getPlayerColors().get(myUsername);
        int[] myPieces = currentState.getPiecePositions().get(myUsername);
        List<Integer> path = getPath(myColor);
        int pos = myPieces[selectedPiece];
        Point p;

        int targetGlobal = -1;
        boolean capture = false, stacking = false;

        if (pos == -1) {
            p = PIECE_PATHS.get(myColor)[0];
        } else {
            int target = pos + currentState.getLastDiceRoll();
            if (target > 56) {
                raw.dispose();
                return;
            }

            p = PIECE_PATHS.get(myColor)[target];

            if (target < 52) {
                targetGlobal = path.get(target);
                List<String> occupants = currentState.getBoard().get(targetGlobal);

                if (!SAFE_SQUARES.contains(targetGlobal)) {
                    for (String occ : occupants) {
                        if (!occ.equals(myUsername)) {
                            capture = true;
                            break;
                        }
                    }
                }

                if (!capture) {
                    for (String occ : occupants) {
                        if (occ.equals(myUsername)) {
                            stacking = true;
                            break;
                        }
                    }
                }
            }
        }

        if (capture) g2.setColor(new Color(220, 53, 69, 140));
        else if (stacking) g2.setColor(new Color(255, 165, 0, 140));
        else g2.setColor(new Color(128, 128, 128, 100));

        g2.fillOval(p.x - PIECE_RADIUS, p.y - PIECE_RADIUS, 2 * PIECE_RADIUS, 2 * PIECE_RADIUS);

        raw.dispose();
    }

    // ---------- Helpers ----------
    private Point screenToBoardPoint(int x, int y) {
        if (!boardBounds.contains(x, y)) return null;

        double scale = boardBounds.width / (double) BOARD_SIZE;
        int boardX = (int) Math.round((x - boardBounds.x) / scale);
        int boardY = (int) Math.round((y - boardBounds.y) / scale);

        return new Point(boardX, boardY);
    }

    private List<Integer> getPath(String color) {
        int start = switch (color) {
            case "RED" -> 0;
            case "GREEN" -> 13;
            case "YELLOW" -> 26;
            case "BLUE" -> 39;
            default -> 0;
        };

        List<Integer> path = new ArrayList<>(57);
        for (int i = 0; i < 52; i++) path.add((start + i) % 52);
        for (int i = 0; i < 5; i++) path.add(52 + i);

        return path;
    }

    private Color getColor(String colorName) {
        return switch (colorName) {
            case "RED" -> new Color(220, 53, 69);
            case "BLUE" -> new Color(0, 123, 255);
            case "GREEN" -> new Color(40, 167, 69);
            case "YELLOW" -> new Color(255, 193, 7);
            default -> Color.GRAY;
        };
    }

    private String colorDot(String colorName) {
        String hex = switch (colorName) {
            case "RED" -> "#dc3545";
            case "BLUE" -> "#007bff";
            case "GREEN" -> "#28a745";
            case "YELLOW" -> "#ffc107";
            default -> "#808080";
        };

        return "<span style='color:" + hex + "; font-size:16px;'>●</span>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";

        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // ---------- Interaction ----------
    private void handleBoardClick(int clickX, int clickY) {
        if (spectatorMode) return;
        if (currentState == null) return;
        if (!currentState.getCurrentTurn().equals(myUsername)) return;

        int[] myPieces = currentState.getPiecePositions().get(myUsername);
        String myColor = currentState.getPlayerColors().get(myUsername);

        // 1. If a piece is already selected, check if the player clicked its final destination square
        if (selectedPiece != -1 && validMoves != null && validMoves.contains(selectedPiece)) {
            int pos = myPieces[selectedPiece];

            // If piece is in base (-1), target index is 0 (start square). Otherwise, current position + roll.
            int targetPos = (pos == -1) ? 0 : pos + currentState.getLastDiceRoll();

            if (targetPos <= 56) {
                Point targetPoint = PIECE_PATHS.get(myColor)[targetPos];
                double dist = Math.hypot(clickX - targetPoint.x, clickY - targetPoint.y);

                // Increased tolerance to ~28 pixels to completely cover the grid square area
                if (dist <= 28) {
                    int pieceToMove = selectedPiece;
                    selectedPiece = -1;
                    validMoves = null;
                    repaint();

                    try {
                        boolean success = gameService.movePiece(lobbyID, myUsername, pieceToMove);
                        if (success) {
                            applyOwnMove(pieceToMove);   // Instant local optimistic UI update
                        } else {
                            JOptionPane.showMessageDialog(this, "Invalid move!");
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                    return;
                }
            }
        }

        // 2. Select a piece to preview its move, or switch selection directly to another piece
        for (int i = 0; i < 4; i++) {
            int pos = myPieces[i];
            Point p = (pos == -1) ? BASE_POSITIONS.get(myColor)[i] : PIECE_PATHS.get(myColor)[pos];
            double dist = Math.hypot(clickX - p.x, clickY - p.y);

            // Generous piece selection hitbox
            if (dist <= PIECE_RADIUS + 8) {
                if (selectedPiece == i) {
                    selectedPiece = -1; // Clicked the same piece again -> Deselect
                } else {
                    selectedPiece = i;  // Select or switch directly to this piece
                }

                repaint();
                return;
            }
        }

        // 3. Clicked empty space on the board -> Clear active selection to keep UI pristine
        if (selectedPiece != -1) {
            selectedPiece = -1;
            repaint();
        }
    }



    private void openLobbyWindow(int targetLobbyID) throws RemoteException {
        LobbyForm lobbyUI = new LobbyForm(currentUser, lobbyService, authService, friendService, targetLobbyID);

        JFrame lobbyFrame = new JFrame("Ludo - Game Lobby");
        lobbyFrame.setUndecorated(true);
        lobbyFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        lobbyFrame.add(lobbyUI.getMainPanel());
        lobbyFrame.setVisible(true);

        Window gameWindow = SwingUtilities.getWindowAncestor(this);
        if (gameWindow != null) {
            gameWindow.dispose();
        }
    }

    private void openFreshLobbyWindow() throws RemoteException {
        openLobbyWindow(-1);
    }

    private void returnWinnerToLobby() {
        returnToLobbyButton.setEnabled(false);

        try {
            if (spectatorMode) {
                lobbyService.removeSpectator(lobbyID, myUsername);
                gameService.unsubscribe(lobbyID, myUsername);
                openLobbyWindow(returnLobbyID);
            } else {
                gameService.unsubscribe(lobbyID, myUsername);
                lobbyService.leaveLobby(lobbyID, myUsername);
                openFreshLobbyWindow();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Connection error while returning to lobby.");
            returnToLobbyButton.setEnabled(true);
        }
    }

    private void hostReturnGameToLobby() {
        try {
            lobbyService.returnGameToLobby(lobbyID, myUsername);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Connection error while returning everyone to lobby.");
        }
    }

    public void returnToLobbyFromHostSignal() {
        try {
            if (activeEndDialog != null) {
                activeEndDialog.dispose();
            }

            gameService.unsubscribe(lobbyID, myUsername);

            if (spectatorMode) {
                lobbyService.removeSpectator(lobbyID, myUsername);
                openLobbyWindow(returnLobbyID);
            } else {
                openLobbyWindow(lobbyID);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Connection error while returning to lobby.");
        }
    }

    private void confirmExitGame() {
        if (spectatorMode) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    "Stop spectating and return to your lobby?",
                    "Exit Game",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                returnWinnerToLobby();
            }
            return;
        }

        int choice = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to leave this game?\nA loss will be added into your account.",
                "Exit Game",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (choice == JOptionPane.YES_OPTION) {
            exitGameAndReturnToLobby();
        }
    }

    private void exitGameAndReturnToLobby() {
        exitButton.setEnabled(false);
        rollButton.setEnabled(false);

        try {
            boolean forfeited = gameService.forfeitGame(lobbyID, myUsername);

            if (!forfeited) {
                JOptionPane.showMessageDialog(this, "Could not exit the game right now.");
                exitButton.setEnabled(true);
                updateGameUI();
                return;
            }

            // Update the local serialized User object so the stats panel is correct immediately.
            // The server also updated its shared User object through the RMI call above.
            currentUser.addLoss();

            // Leave the old game lobby. The new LobbyForm constructor will create a fresh solo lobby.
            lobbyService.leaveLobby(lobbyID, myUsername);
            openFreshLobbyWindow();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Connection error while exiting the game.");
            exitButton.setEnabled(true);
            updateGameUI();
        }
    }

    private void applyOwnMove(int pieceID) {
        // Completely purge selection properties so the player doesn't hold lingering states
        selectedPiece = -1;
        validMoves = null;

        // Repaint UI now. The incoming asynchronous server broadcast will cleanly populate the updated positions.
        repaint();
    }

    private void rollDice() {
        if (spectatorMode) return;
        try {
            gameService.rollDice(lobbyID, myUsername);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void sendChat() {
        if (spectatorMode) return;
        String msg = chatInput.getText().trim();

        if (!msg.isEmpty()) {
            try {
                lobbyService.sendChatMessage(lobbyID, myUsername, msg);
                chatInput.setText("");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void addChatMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatDisplay.append(message + "\n");
            chatDisplay.setCaretPosition(chatDisplay.getDocument().getLength());
        });
    }
}
