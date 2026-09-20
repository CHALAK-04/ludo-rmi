package Ludo.SERVER;

import Ludo.COMMON.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;

public class GameServiceImpl extends UnicastRemoteObject implements IGameService {

    private final ConcurrentHashMap<String, User> sharedUsers;
    private final ConcurrentHashMap<Integer, Map<String, IGameObserver>> gameObservers;
    private final Map<Integer, GameState> activeGames = new ConcurrentHashMap<>();

    // --- Turn Timer fields ---
    private final Map<Integer, Integer> gameTimeLimits = new ConcurrentHashMap<>();
    private final Map<Integer, ScheduledFuture<?>> turnTimers = new ConcurrentHashMap<>();
    private final Map<Integer, Long> turnStartTimes = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // PLAYERS WHO LOST CONNECTION DURING A GAME.
    // They have 2 minutes to reconnect before receiving a loss.
    private final Map<String, Integer> pendingReconnectLobby = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> reconnectTimers = new ConcurrentHashMap<>();


    // ---------- PATH HELPERS ----------
    private static final Map<String, Integer> COLOR_OFFSET = new HashMap<>();
    static {
        // These offsets must match the board image / client coordinates:
        // RED = top-left path, GREEN = top-right path, YELLOW = bottom-right path, BLUE = bottom-left path.
        COLOR_OFFSET.put("RED", 0);
        COLOR_OFFSET.put("GREEN", 13);
        COLOR_OFFSET.put("YELLOW", 26);
        COLOR_OFFSET.put("BLUE", 39);
    }
    private static final Set<Integer> SAFE_SQUARES = new HashSet<>(Arrays.asList(0, 8, 13, 21, 26, 34, 39, 47));

    private List<Integer> getPath(String color) {
        int start = COLOR_OFFSET.get(color);
        List<Integer> path = new ArrayList<>(57);
        for (int i = 0; i < 52; i++) {
            path.add((start + i) % 52);
        }
        for (int i = 0; i < 5; i++) {
            path.add(52 + i);
        }
        return path;
    }

    private int pathToGlobal(String color, int pathIndex) {
        if (pathIndex < 0 || pathIndex >= 57) return -1;
        if (pathIndex < 52) {
            return getPath(color).get(pathIndex);
        }
        return -1; // home column
    }

    // ---------- CONSTRUCTOR ----------
    public GameServiceImpl(ConcurrentHashMap<String, User> sharedUsers,
                           ConcurrentHashMap<Integer, Map<String, IGameObserver>> gameObservers) throws RemoteException {
        super();
        this.sharedUsers = sharedUsers;
        this.gameObservers = gameObservers;
    }

    // ---------- TURN TIMER ----------
    private void startTurnTimer(int lobbyID) {
        cancelTurnTimer(lobbyID);
        turnStartTimes.put(lobbyID, System.currentTimeMillis());
        int timeLimit = gameTimeLimits.getOrDefault(lobbyID, 60);
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            try {
                GameState game = activeGames.get(lobbyID);
                if (game == null || game.isGameOver()) return;

                synchronized (game) {   // <-- synchronize timer penalty
                    String current = game.getCurrentTurn();

                    // SKIP PLAYERS WHO ARE DISCONNECTED OR ALREADY FINISHED.
                    if (current == null || game.isDisconnected(current) || game.hasFinished(current)) {
                        game.nextTurn();
                        startTurnTimer(lobbyID);
                        broadcast(lobbyID);
                        return;
                    }

                    int[] pieces = game.getPiecePositions().get(current);
                    String color = game.getPlayerColors().get(current);
                    List<Integer> candidates = new ArrayList<>();
                    for (int i = 0; i < 4; i++) {
                        int pos = pieces[i];
                        if (pos >= 0 && pos < 52) {
                            candidates.add(i);
                        }
                    }
                    if (!candidates.isEmpty()) {
                        int idx = candidates.get(new Random().nextInt(candidates.size()));
                        int oldPath = pieces[idx];
                        int oldGlobal = pathToGlobal(color, oldPath);
                        game.updateBoard(current, oldGlobal, -1);
                        pieces[idx] = -1;
                        System.out.println("LOG: Timeout penalty – " + current + "'s piece " + idx + " returned to base.");
                    }
                    endTurn(lobbyID, current);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }, timeLimit, TimeUnit.SECONDS);
        turnTimers.put(lobbyID, future);
    }

    private void cancelTurnTimer(int lobbyID) {
        ScheduledFuture<?> future = turnTimers.remove(lobbyID);
        if (future != null) {
            future.cancel(false);
        }
    }



    private boolean hasWallOnSquare(GameState game, int globalSquare) {
        if (globalSquare < 0 || globalSquare >= 52) return false;

        Map<String, Integer> counts = new HashMap<>();
        for (String occupant : game.getBoard().get(globalSquare)) {
            counts.put(occupant, counts.getOrDefault(occupant, 0) + 1);
        }

        for (int count : counts.values()) {
            if (count >= 2) {
                return true;
            }
        }

        return false;
    }

    private boolean pathHasWall(GameState game, List<Integer> path, int oldPathPos, int targetPathPos) {
        if (!game.isCanBlock()) return false;

        // WALLS ONLY EXIST ON THE MAIN 52-SQUARE PATH.
        int lastMainPathSquare = Math.min(targetPathPos, 51);
        int firstCheckedSquare = Math.max(oldPathPos + 1, 0);

        for (int step = firstCheckedSquare; step <= lastMainPathSquare; step++) {
            int globalSquare = path.get(step);
            if (hasWallOnSquare(game, globalSquare)) {
                return true;
            }
        }

        return false;
    }

    private int playerProgress(GameState game, String username) {
        int[] pieces = game.getPiecePositions().get(username);
        if (pieces == null) return -1;

        int progress = 0;
        for (int pos : pieces) {
            if (pos == 56) {
                progress += 1000;
            } else if (pos >= 0) {
                progress += pos + 1;
            }
        }
        return progress;
    }

    private String findLastPlayer(GameState game, String winnerName) {
        String lastPlayer = null;
        int lowestProgress = Integer.MAX_VALUE;

        for (String player : game.getTurnOrder()) {
            if (player.equals(winnerName)) continue;

            int progress = playerProgress(game, player);
            if (lastPlayer == null || progress < lowestProgress) {
                lowestProgress = progress;
                lastPlayer = player;
            }
        }

        return lastPlayer;
    }


    private void refreshFriendsAround(String username) {
        User user = sharedUsers.get(username);
        try {
            IFriendService friendService = (IFriendService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/friend");
            friendService.refreshUserUI(username);
            if (user != null) {
                for (String friendName : user.getFriends()) {
                    friendService.refreshUserUI(friendName);
                }
            }
        } catch (Exception ignored) {}
    }

    private void applyReconnectPenalty(GameState game, String username) {
        int[] pieces = game.getPiecePositions().get(username);
        String color = game.getPlayerColors().get(username);

        if (pieces == null || color == null) return;

        List<Integer> candidates = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            // Reconnect penalty: one active piece returns to base.
            // Finished pieces at 56 are not punished.
            if (pieces[i] >= 0 && pieces[i] < 56) {
                candidates.add(i);
            }
        }

        if (candidates.isEmpty()) return;

        int idx = candidates.get(new Random().nextInt(candidates.size()));
        int oldGlobal = pathToGlobal(color, pieces[idx]);

        if (oldGlobal >= 0) {
            game.updateBoard(username, oldGlobal, -1);
        }

        pieces[idx] = -1;
        System.out.println("LOG: Reconnect penalty - " + username + "'s piece " + idx + " returned to base.");
    }

    private void restoreDisconnectedPiecesWithCollisionCheck(GameState game, String username) {
        int[] savedPositions = game.getDisconnectedPiecePositions(username);
        String color = game.getPlayerColors().get(username);

        if (savedPositions == null || color == null) {
            game.restoreDisconnectedPlayerPieces(username, new int[]{-1, -1, -1, -1});
            return;
        }

        int[] restored = Arrays.copyOf(savedPositions, savedPositions.length);
        List<Integer> path = getPath(color);

        for (int i = 0; i < restored.length; i++) {
            int pos = restored[i];

            if (pos >= 0 && pos < 52) {
                int global = path.get(pos);
                List<String> occupants = game.getBoard().get(global);

                boolean occupiedByOpponent = false;
                for (String occupant : occupants) {
                    if (!occupant.equals(username)) {
                        occupiedByOpponent = true;
                        break;
                    }
                }

                if (occupiedByOpponent && !SAFE_SQUARES.contains(global)) {
                    // If the disconnected piece would have been eaten while absent,
                    // it comes back to base instead of being placed on top of the opponent.
                    restored[i] = -1;
                } else {
                    // Empty square or safe square: restore the piece.
                    // On safe squares, multiple players may stand together.
                    game.updateBoard(username, -1, global);
                }
            }
        }

        game.restoreDisconnectedPlayerPieces(username, restored);

        // Additional reconnect penalty to prevent players from abusing disconnect.
        applyReconnectPenalty(game, username);
    }

    private void cancelReconnectTimer(String username) {
        ScheduledFuture<?> timer = reconnectTimers.remove(username);
        if (timer != null) {
            timer.cancel(false);
        }
    }

    private void notifyGameEnded(int lobbyID, String winnerName, String loserName) {
        Map<String, IGameObserver> obs = gameObservers.get(lobbyID);
        if (obs != null) {
            for (IGameObserver o : obs.values()) {
                try { o.onGameEnded(winnerName, loserName); } catch (RemoteException ignored) {}
            }
        }
    }


    private void notifyLeaderboardClients() {
        try {
            ILobbyService lobbyService = (ILobbyService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/lobby");
            lobbyService.notifyLeaderboardUpdated();
        } catch (Exception ignored) {}
    }

    private void finishGame(int lobbyID, GameState game, String winnerName, String loserName) {
        game.setGameOver(winnerName);
        cancelTurnTimer(lobbyID);

        // Final scoring rule:
        // first place gets +1 win, last place gets +1 loss, middle players get no score change.
        // No score file saving is done here; final persistence remains a separate step.
        User winner = sharedUsers.get(winnerName);
        if (winner != null) winner.addWin();

        if (loserName != null && !loserName.equals(winnerName)) {
            User loser = sharedUsers.get(loserName);
            if (loser != null) loser.addLoss();
        }

        DataStore.saveUsers(sharedUsers);

        try {
            ILobbyService lobbyService = (ILobbyService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/lobby");
            lobbyService.markGameEnded(lobbyID);
        } catch (Exception ignored) {}

        notifyGameEnded(lobbyID, winnerName, loserName);
        broadcast(lobbyID);
    }


    private boolean checkEndAfterPlayerFinished(int lobbyID, GameState game, String username) {
        // MARK THE PLAYER AS FINISHED ONCE HE REACHES THE TARGET.
        if (game.isPlayerFinished(username)) {
            game.markPlayerFinished(username);
        }

        // GAME ENDS WHEN ONLY ONE PLAYER HAS NOT FINISHED YET.
        if (game.getUnfinishedPlayerCount() <= 1 && game.getFirstFinisher() != null) {
            String winnerName = game.getFirstFinisher();
            String loserName = game.getOnlyUnfinishedPlayer();

            finishGame(lobbyID, game, winnerName, loserName);
            return true;
        }

        return false;
    }

    // ---------- GAME LOGIC ----------
    @Override
    public void createNewGameSession(int lobbyID, Map<String, String> players,
                                     boolean rollSix, boolean block, int toWin, int timeLimit) throws RemoteException {
        GameState state = new GameState(lobbyID, players, rollSix, block, toWin);
        activeGames.put(lobbyID, state);
        gameTimeLimits.put(lobbyID, timeLimit);
        startTurnTimer(lobbyID);
        System.out.println("LOG: Game Server created Match #" + lobbyID);
        broadcast(lobbyID);
    }

    @Override
    public int rollDice(int lobbyID, String username) throws RemoteException {
        GameState game = activeGames.get(lobbyID);
        if (game == null || game.isGameOver()) return -1;
        synchronized (game) {
            if (game.isDisconnected(username) || game.hasFinished(username)) return -1;
            if (!game.getCurrentTurn().equals(username) || game.hasRolled())
                return -1;

            int roll = (int)(Math.random() * 6) + 1;
            game.setLastDiceRoll(roll);
            broadcast(lobbyID);

            if (getValidMoves(lobbyID, username).isEmpty()) {
                game.nextTurn();
                startTurnTimer(lobbyID);
                broadcast(lobbyID);
            }
            return roll;
        }
    }

    @Override
    public List<Integer> getValidMoves(int lobbyID, String username) throws RemoteException {
        GameState game = activeGames.get(lobbyID);
        if (game == null || game.isGameOver()) return Collections.emptyList();
        synchronized (game) {
            if (game.isDisconnected(username) || game.hasFinished(username)) return Collections.emptyList();
            if (!game.getCurrentTurn().equals(username) || !game.hasRolled())
                return Collections.emptyList();

            int roll = game.getLastDiceRoll();
            int[] pieces = game.getPiecePositions().get(username);
            String color = game.getPlayerColors().get(username);
            List<Integer> path = getPath(color);
            List<Integer> valid = new ArrayList<>();

            for (int i = 0; i < 4; i++) {
                int pos = pieces[i];
                if (pos == 56) continue;

                if (pos == -1) {
                    if (game.isMustRollSix() && roll != 6) continue;
                    valid.add(i);
                } else {
                    int target = pos + roll;
                    if (target > 56) continue;
                    if (target == 56) {
                        valid.add(i);
                        continue;
                    }
                    if (target < 52) {
                        // WALL RULE: A WALL IS TWO OR MORE PIECES OF THE SAME PLAYER ON ONE SQUARE.
                        // WHEN WALLS ARE ENABLED, NO PIECE CAN LAND ON OR PASS THROUGH A WALL.
                        if (pathHasWall(game, path, pos, target)) {
                            continue;
                        }

                        valid.add(i);
                    } else {
                        // PLAYER MUST ALSO BE ABLE TO REACH THE HOME COLUMN WITHOUT CROSSING A WALL.
                        if (pathHasWall(game, path, pos, 51)) {
                            continue;
                        }

                        boolean occupied = false;
                        for (int p : pieces) {
                            if (p == target) { occupied = true; break; }
                        }
                        if (!occupied) valid.add(i);
                    }
                }
            }
            return valid;
        }
    }

    @Override
    public boolean movePiece(int lobbyID, String username, int pieceID) throws RemoteException {
        GameState game = activeGames.get(lobbyID);
        if (game == null || game.isGameOver()) return false;
        synchronized (game) {
            if (game.isDisconnected(username) || game.hasFinished(username)) return false;
            if (!game.getCurrentTurn().equals(username) || !game.hasRolled())
                return false;

            List<Integer> validMoves = getValidMoves(lobbyID, username);
            if (!validMoves.contains(pieceID)) return false;

            int roll = game.getLastDiceRoll();
            String color = game.getPlayerColors().get(username);
            List<Integer> path = getPath(color);
            int[] pieces = game.getPiecePositions().get(username);
            int oldPos = pieces[pieceID];
            int newPos = (oldPos == -1) ? 0 : oldPos + roll;

            if (newPos < 52) {
                int globalTarget = path.get(newPos);
                if (!SAFE_SQUARES.contains(globalTarget)) {
                    List<String> occupants = game.getBoard().get(globalTarget);
                    for (String occ : new ArrayList<>(occupants)) {
                        if (!occ.equals(username)) {
                            int[] oppPieces = game.getPiecePositions().get(occ);
                            String oppColor = game.getPlayerColors().get(occ);
                            List<Integer> oppPath = getPath(oppColor);
                            for (int j = 0; j < 4; j++) {
                                int op = oppPieces[j];
                                if (op >= 0 && op < 52 && oppPath.get(op) == globalTarget) {
                                    game.updateBoard(occ, globalTarget, -1);
                                    oppPieces[j] = -1;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            int oldGlobal = pathToGlobal(color, oldPos);
            int newGlobal = pathToGlobal(color, newPos);
            game.updateBoard(username, oldGlobal, newGlobal);
            pieces[pieceID] = newPos;

            if (checkEndAfterPlayerFinished(lobbyID, game, username)) {
                return true;
            }

            // IF PLAYER FINISHED BUT GAME CONTINUES, HE SHOULD NOT ROLL AGAIN.
            if (game.hasFinished(username)) {
                cancelTurnTimer(lobbyID);
                game.nextTurn();
                startTurnTimer(lobbyID);
                broadcast(lobbyID);
                return true;
            }

            // --- FIXED TURN LOOP TIMING LOGIC ---
            if (roll == 6) {
                // Player gets another roll!
                // CRITICAL: Must clear previous active timer scheduled task first
                cancelTurnTimer(lobbyID);

                game.setHasRolled(false);
                game.setLastDiceRoll(0);

                // Start fresh sequence for the same user
                startTurnTimer(lobbyID);
            } else {
                // Turn passes normally to the next player
                cancelTurnTimer(lobbyID);
                game.nextTurn();
                startTurnTimer(lobbyID);
            }

            broadcast(lobbyID);
            return true;
        }
    }

    @Override
    public void endTurn(int lobbyID, String username) throws RemoteException {
        GameState game = activeGames.get(lobbyID);
        if (game != null && !game.isGameOver()) {
            synchronized (game) {
                if (game.getCurrentTurn().equals(username)) {
                    cancelTurnTimer(lobbyID);
                    game.nextTurn();
                    startTurnTimer(lobbyID);
                    broadcast(lobbyID);
                }
            }
        }
    }

    @Override
    public void subscribe(int lobbyID, String username, IGameObserver observer) throws RemoteException {
        Map<String, IGameObserver> lobbyObs = gameObservers.get(lobbyID);
        if (lobbyObs == null) {
            lobbyObs = new ConcurrentHashMap<>();
            gameObservers.put(lobbyID, lobbyObs);
        }
        lobbyObs.put(username, observer);
        broadcast(lobbyID);
    }

    @Override
    public void unsubscribe(int lobbyID, String username) throws RemoteException {
        Map<String, IGameObserver> lobbyObs = gameObservers.get(lobbyID);
        if (lobbyObs != null) {
            lobbyObs.remove(username);
        }
    }

    private void broadcast(int lobbyID) {
        GameState state = activeGames.get(lobbyID);
        if (state == null) return;

        // The broadcast itself does not modify shared data other than the state’s timer,
        // but we hold the lock to ensure the timer value is read/updated consistently.
        synchronized (state) {
            int timeLimit = gameTimeLimits.getOrDefault(lobbyID, 60);
            Long startTime = turnStartTimes.get(lobbyID);
            if (startTime != null && !state.isGameOver()) {
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                int remaining = (int) (timeLimit - elapsed);
                if (remaining < 0) remaining = 0;
                state.setRemainingTimeSeconds(remaining);
            }

            Map<String, IGameObserver> lobbyObs = gameObservers.get(lobbyID);
            if (lobbyObs != null) {
                for (Map.Entry<String, IGameObserver> entry : new ArrayList<>(lobbyObs.entrySet())) {
                    String username = entry.getKey();
                    IGameObserver observer = entry.getValue();

                    try {
                        observer.onGameStateUpdated(state);
                    } catch (RemoteException ignored) {
                        try {
                            handleClientDisconnect(lobbyID, username);
                        } catch (RemoteException ignoredAgain) {}
                    }
                }
            }
        }
    }



    @Override
    public void handleClientDisconnect(int lobbyID, String username) throws RemoteException {
        GameState game = activeGames.get(lobbyID);
        if (game == null || username == null) return;

        synchronized (game) {
            Map<String, IGameObserver> obs = gameObservers.get(lobbyID);
            if (obs != null) {
                obs.remove(username);
            }

            // Spectators are present in observers but not in turnOrder.
            if (!game.getTurnOrder().contains(username)) {
                return;
            }

            // Already waiting for reconnect.
            if (pendingReconnectLobby.containsKey(username)) {
                return;
            }

            User disconnectedUser = sharedUsers.get(username);
            if (disconnectedUser != null) {
                disconnectedUser.setStatus(0);
                disconnectedUser.setSpectatableGame(false);
            }

            pendingReconnectLobby.put(username, lobbyID);
            refreshFriendsAround(username);

            // Hide all pieces while disconnected.
            // They are restored only if the player reconnects in time.
            game.hideDisconnectedPlayerPieces(username);

            // If it was his turn, do not block the whole game.
            if (username.equals(game.getCurrentTurn()) && game.getTurnOrder().size() > 1) {
                cancelTurnTimer(lobbyID);
                game.nextTurn();
                startTurnTimer(lobbyID);
            }

            ScheduledFuture<?> timeout = scheduler.schedule(() -> {
                try {
                    rejectReconnect(username);
                } catch (RemoteException ignored) {}
            }, 2, TimeUnit.MINUTES);

            reconnectTimers.put(username, timeout);

            System.out.println("LOG: " + username + " disconnected from game #" + lobbyID + ". Reconnect window started.");
            broadcast(lobbyID);
        }
    }

    @Override
    public int getPendingReconnectLobby(String username) throws RemoteException {
        Integer lobbyID = pendingReconnectLobby.get(username);
        return lobbyID == null ? -1 : lobbyID;
    }

    @Override
    public boolean reconnectToGame(int lobbyID, String username, IGameObserver observer) throws RemoteException {
        Integer expectedLobby = pendingReconnectLobby.get(username);
        GameState game = activeGames.get(lobbyID);

        if (expectedLobby == null || expectedLobby != lobbyID || game == null || game.isGameOver()) {
            return false;
        }

        synchronized (game) {
            cancelReconnectTimer(username);
            pendingReconnectLobby.remove(username);

            Map<String, IGameObserver> lobbyObs = gameObservers.get(lobbyID);
            if (lobbyObs == null) {
                lobbyObs = new ConcurrentHashMap<>();
                gameObservers.put(lobbyID, lobbyObs);
            }

            lobbyObs.put(username, observer);

            restoreDisconnectedPiecesWithCollisionCheck(game, username);

            User user = sharedUsers.get(username);
            if (user != null) {
                user.setStatus(-1);
                user.setCurrentLobbyID(lobbyID);
                user.setSpectatableGame(false);
            }

            refreshFriendsAround(username);
            broadcast(lobbyID);

            System.out.println("LOG: " + username + " reconnected to game #" + lobbyID);
            return true;
        }
    }

    @Override
    public void rejectReconnect(String username) throws RemoteException {
        Integer lobbyID = pendingReconnectLobby.remove(username);
        if (lobbyID == null) return;

        cancelReconnectTimer(username);

        User user = sharedUsers.get(username);
        if (user != null) {
            user.setSpectatableGame(false);
            // If the player is still offline, he remains offline.
            // If he logged in and rejected reconnect, keep his current ONLINE/lobby status.
        }

        // Same scoring logic as leaving the game: disconnected user loses if he does not reconnect.
        forfeitGame(lobbyID, username);
        refreshFriendsAround(username);
    }

    @Override
    public boolean forfeitGame(int lobbyID, String username) throws RemoteException {
        GameState game = activeGames.get(lobbyID);
        if (game == null || username == null) return false;

        synchronized (game) {
            if (game.isGameOver()) return false;
            if (!game.getTurnOrder().contains(username)) return false;

            boolean wasCurrentTurn = username.equals(game.getCurrentTurn());

            // The quitter already confirmed the exit on his own client.
            // He should not receive the final game-ended popup.
            Map<String, IGameObserver> obs = gameObservers.get(lobbyID);
            if (obs != null) {
                obs.remove(username);
            }

            game.removePlayerFromGame(username);

            if (game.getTurnOrder().isEmpty()) {
                cancelTurnTimer(lobbyID);
                activeGames.remove(lobbyID);
                gameTimeLimits.remove(lobbyID);
                turnStartTimes.remove(lobbyID);
                return true;
            }

            if (game.getTurnOrder().size() == 1) {
                String remainingPlayer = game.getTurnOrder().get(0);
                finishGame(lobbyID, game, remainingPlayer, username);
                return true;
            }

            // Match continues with at least two players.
            // The player who voluntarily exited receives one loss.
            User quitter = sharedUsers.get(username);
            if (quitter != null) {
                quitter.addLoss();
                DataStore.saveUsers(sharedUsers);
                notifyLeaderboardClients();
            }

            if (wasCurrentTurn) {
                cancelTurnTimer(lobbyID);
                startTurnTimer(lobbyID);
            }

            broadcast(lobbyID);
            return true;
        }
    }

    @Override
    public GameState getGameState(int lobbyID) throws RemoteException {
        GameState game = activeGames.get(lobbyID);
        if (game != null) {
            synchronized (game) {
                // Returning the same object; synchronization ensures visibility
                return game;
            }
        }
        return null;
    }
}