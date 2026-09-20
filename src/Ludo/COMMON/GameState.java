package Ludo.COMMON;

import java.io.Serializable;
import java.util.*;

public class GameState implements Serializable {
    private int lobbyID;
    private String currentTurn;
    private int lastDiceRoll;
    private boolean hasRolled;
    private boolean isGameOver;
    private String winner;
    private int remainingTimeSeconds;

    private List<String> turnOrder;
    private Map<String, String> playerColors;          // username -> color
    private Map<String, int[]> piecePositions;        // username -> int[4]  (-1=base, 0..56=path)
    private Map<String, int[]> disconnectedPiecePositions; // hidden pieces while player is disconnected
    private Set<String> disconnectedPlayers;

    // FINISH ORDER IS USED TO GIVE THE WIN TO THE FIRST PLAYER WHO REACHES THE TARGET.
    private List<String> finishedPlayers;

    private List<List<String>> board;                 // 52 global squares, each a list of usernames

    private boolean mustRollSix;
    private boolean canBlock;
    private int piecesToWin;

    public GameState(int lobbyID, Map<String, String> players, boolean rollSix, boolean block, int toWin) {
        this.lobbyID = lobbyID;
        this.mustRollSix = rollSix;
        this.canBlock = block;
        this.piecesToWin = toWin;

        this.playerColors = new HashMap<>(players);
        this.turnOrder = new ArrayList<>(players.keySet());
        this.currentTurn = turnOrder.get(0);
        this.lastDiceRoll = 0;
        this.remainingTimeSeconds = 0;
        this.hasRolled = false;
        this.isGameOver = false;

        // Init piecePositions (all in base)
        this.piecePositions = new HashMap<>();
        this.disconnectedPiecePositions = new HashMap<>();
        this.disconnectedPlayers = new HashSet<>();
        this.finishedPlayers = new ArrayList<>();
        for (String user : turnOrder) {
            piecePositions.put(user, new int[]{-1, -1, -1, -1});
        }

        // Init global board (52 squares)
        this.board = new ArrayList<>(52);
        for (int i = 0; i < 52; i++) {
            board.add(new ArrayList<>());
        }
    }

    // --- Getters & Setters (simple) ---
    public int getLobbyID() { return lobbyID; }
    public String getCurrentTurn() { return currentTurn; }
    public void nextTurn() {
        if (turnOrder.isEmpty()) {
            currentTurn = null;
            hasRolled = false;
            lastDiceRoll = 0;
            return;
        }

        int idx = turnOrder.indexOf(currentTurn);
        if (idx < 0) idx = 0;

        for (int i = 0; i < turnOrder.size(); i++) {
            idx = (idx + 1) % turnOrder.size();
            String candidate = turnOrder.get(idx);

            // SKIP DISCONNECTED OR ALREADY FINISHED PLAYERS.
            if (!disconnectedPlayers.contains(candidate) && !finishedPlayers.contains(candidate)) {
                currentTurn = candidate;
                hasRolled = false;
                lastDiceRoll = 0;
                return;
            }
        }

        // NO ACTIVE PLAYER LEFT TO PLAY.
        currentTurn = turnOrder.get(0);
        hasRolled = false;
        lastDiceRoll = 0;
    }

    public int getRemainingTimeSeconds() { return remainingTimeSeconds; }
    public void setRemainingTimeSeconds(int remainingTimeSeconds) { this.remainingTimeSeconds = remainingTimeSeconds; }
    public int getLastDiceRoll() { return lastDiceRoll; }
    public void setLastDiceRoll(int val) {
        this.lastDiceRoll = val;
        this.hasRolled = val > 0; // 0 means “no active roll”, so the player may roll again
    }
    public boolean hasRolled() { return hasRolled; }
    public void setHasRolled(boolean val) { this.hasRolled = val; }

    public boolean isMustRollSix() { return mustRollSix; }
    public boolean isCanBlock() { return canBlock; }
    public int getPiecesToWin() { return piecesToWin; }
    public List<String> getTurnOrder() { return turnOrder; }

    public void removePlayerFromGame(String username) {
        int oldIndex = turnOrder.indexOf(username);
        boolean wasCurrentTurn = username != null && username.equals(currentTurn);

        turnOrder.remove(username);
        playerColors.remove(username);
        piecePositions.remove(username);
        disconnectedPiecePositions.remove(username);
        disconnectedPlayers.remove(username);
        finishedPlayers.remove(username);

        for (List<String> square : board) {
            square.removeIf(username::equals);
        }

        hasRolled = false;
        lastDiceRoll = 0;

        if (turnOrder.isEmpty()) {
            currentTurn = null;
            return;
        }

        if (wasCurrentTurn || currentTurn == null || !turnOrder.contains(currentTurn)) {
            if (oldIndex < 0 || oldIndex >= turnOrder.size()) {
                oldIndex = 0;
            }
            currentTurn = turnOrder.get(oldIndex);
        }
    }


    public boolean isDisconnected(String username) {
        return disconnectedPlayers.contains(username);
    }

    public int getConnectedPlayerCount() {
        int count = 0;
        for (String player : turnOrder) {
            if (!disconnectedPlayers.contains(player)) {
                count++;
            }
        }
        return count;
    }

    public void hideDisconnectedPlayerPieces(String username) {
        int[] positions = piecePositions.get(username);

        if (positions == null) return;

        disconnectedPiecePositions.put(username, Arrays.copyOf(positions, positions.length));
        disconnectedPlayers.add(username);

        for (List<String> square : board) {
            square.removeIf(username::equals);
        }

        piecePositions.remove(username);
        hasRolled = false;
        lastDiceRoll = 0;
    }

    public int[] getDisconnectedPiecePositions(String username) {
        int[] positions = disconnectedPiecePositions.get(username);
        if (positions == null) return null;
        return Arrays.copyOf(positions, positions.length);
    }

    public void restoreDisconnectedPlayerPieces(String username, int[] positions) {
        disconnectedPlayers.remove(username);
        disconnectedPiecePositions.remove(username);
        piecePositions.put(username, positions);
    }


    public boolean hasFinished(String username) {
        return finishedPlayers.contains(username);
    }

    public void markPlayerFinished(String username) {
        if (!finishedPlayers.contains(username)) {
            finishedPlayers.add(username);
        }
    }

    public String getFirstFinisher() {
        if (finishedPlayers.isEmpty()) return null;
        return finishedPlayers.get(0);
    }

    public int getUnfinishedPlayerCount() {
        int count = 0;

        for (String player : turnOrder) {
            if (!finishedPlayers.contains(player)) {
                count++;
            }
        }

        return count;
    }

    public String getOnlyUnfinishedPlayer() {
        String lastPlayer = null;

        for (String player : turnOrder) {
            if (!finishedPlayers.contains(player)) {
                lastPlayer = player;
            }
        }

        return lastPlayer;
    }

    public Map<String, String> getPlayerColors() { return playerColors; }
    public Map<String, int[]> getPiecePositions() { return piecePositions; }
    public List<List<String>> getBoard() { return board; }

    public boolean isGameOver() { return isGameOver; }
    public String getWinner() { return winner; }
    public void setGameOver(String winner) { this.isGameOver = true; this.winner = winner; }

    // --- Tiny helpers ---
    /** Move a piece on the global board (for captures / regular moves) */
    public void updateBoard(String username, int oldGlobal, int newGlobal) {
        if (oldGlobal >= 0 && oldGlobal < 52) {
            board.get(oldGlobal).remove(username);
        }
        if (newGlobal >= 0 && newGlobal < 52) {
            board.get(newGlobal).add(username);
        }
    }

    // CHECK IF THE PLAYER REACHED THE TARGET NUMBER OF FINISHED PIECES.
    public boolean isPlayerFinished(String username) {
        int[] pos = piecePositions.get(username);
        if (pos == null) return false;

        int finished = 0;
        for (int p : pos) {
            if (p == 56) finished++;
        }

        return finished >= piecesToWin;
    }
}