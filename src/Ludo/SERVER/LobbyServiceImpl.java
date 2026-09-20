package Ludo.SERVER;


import Ludo.COMMON.*;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LobbyServiceImpl extends UnicastRemoteObject implements ILobbyService
{
    // COUNTER FOR AUTO INCREMENTING IDS
    private final AtomicInteger lobbyIdCounter = new AtomicInteger(100);

    // NEEDED FOR LOGS TIMESTAMPS
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    // STORAGE FOR ALL ACTIVE LOBBIES
    private Map<Integer, Lobby> activeLobbies = new ConcurrentHashMap<>();

    // STORAGE FOR OBSERVERS SO WE CAN PUSH UPDATES FOR CLIENT
    private Map<Integer, Map<String, ILobbyObserver>> lobbyObservers;

    // REFERENCE TO GLOBAL USERS LIST
    private ConcurrentHashMap<String, User> globalUsers;

    // SERVER SIDE HEARTBEAT FOR DEAD CLIENT DETECTION
    private final ScheduledExecutorService disconnectMonitor = Executors.newSingleThreadScheduledExecutor();

    public Lobby getLobbyData(int lobbyID) throws RemoteException {
        // RMI returns a serialized copy of this object to the client
        return activeLobbies.get(lobbyID);
    }

    public LobbyServiceImpl(ConcurrentHashMap<String, User> sharedUsers, Map<Integer, Map<String, ILobbyObserver>> sharedObservers) throws RemoteException
    {
        this.globalUsers = sharedUsers;
        this.lobbyObservers = sharedObservers;
        startDisconnectMonitor();
    }

    // PLAYER SUBSCRIPTION SCRIPT FOR LOBBY UPDATES
    public void subscribe(int lobbyID, String username, ILobbyObserver observer) throws RemoteException
    {

        // GETTING CURRENT TIME FOR LOGS (Matches your AuthServiceImpl style)
        String now = dtf.format(LocalDateTime.now());

        // ACCESS THE LOBBY'S NOTIFICATION LIST
        Map<String, ILobbyObserver> playersInThisLobby = lobbyObservers.get(lobbyID);

        // CREATE A NEW ENTRY IF NO ONE IS YET IN THE LOBBY
        if (playersInThisLobby == null)
        {
            playersInThisLobby = new ConcurrentHashMap<String, ILobbyObserver>();
            lobbyObservers.put(lobbyID, playersInThisLobby);
        }

        // REGISTER THE CLIENT CALLBACK
        playersInThisLobby.put(username, observer);

        // LOG THE SUCCESSFUL SUBSCRIPTION ON THE SERVER CONSOLE
        System.out.println("[" + now + "] LOG: " + username + " SUBSCRIBED TO REAL-TIME UPDATES FOR LOBBY #" + lobbyID);
    }

    // PLAYER UNSUBSCRIBE SCRIPT FOR LOBBY UPDATES
    public void unsubscribe(int lobbyID, String username) throws RemoteException {
        String now = dtf.format(LocalDateTime.now());

        // 1. REMOVE FROM DATA STRUCTURE (The "Physical" Lobby)
        Lobby lobby = activeLobbies.get(lobbyID);
        if (lobby != null) {
            lobby.getPlayers().remove(username);

            // OWNER LOGIC: If the owner leaves, appoint a new one or close lobby
            if (lobby.getOwner().getUsername().equals(username)) {
                if (lobby.getPlayers().isEmpty()) {
                    activeLobbies.remove(lobbyID);
                } else {
                    // Pick the first person available to be the new owner
                    String nextOwnerName = lobby.getPlayers().keySet().iterator().next();
                    User nextOwner = globalUsers.get(nextOwnerName);
                    lobby.setOwner(nextOwner);
                    System.out.println("[" + now + "] LOG: Host left. " + nextOwnerName + " is the new owner of #" + lobbyID);
                }
            }
        }

        // 2. REMOVE FROM NOTIFICATION LIST (The "Phone Line")
        Map<String, ILobbyObserver> playersInThisLobby = lobbyObservers.get(lobbyID);
        if (playersInThisLobby != null) {
            playersInThisLobby.remove(username);

            if (playersInThisLobby.isEmpty()) {
                lobbyObservers.remove(lobbyID);
                System.out.println("[" + now + "] LOG: LOBBY #" + lobbyID + " IS EMPTY, NOTIFICATION LIST CLEARED");
            } else {
                // 3. BROADCAST TO REMAINING PLAYERS
                notifyAllPlayers(lobbyID, username + " LEFT THE LOBBY");
            }
        }
        System.out.println("[" + now + "] LOG: " + username + " FULLY REMOVED FROM LOBBY #" + lobbyID);
    }

    // NOTIFICATION FUNCTION - BROADCASTS UPDATES TO ALL PLAYERS IN A SPECIFIC LOBBY
    private void notifyAllPlayers(int lobbyID, String ActionTaken)
    {
        // GETTING CURRENT TIME FOR LOGS
        String now = dtf.format(LocalDateTime.now());

        // PULLING THE LOBBY DATA AND THE LIST OF SUBSCRIBERS
        Lobby currentLobby = activeLobbies.get(lobbyID);
        Map<String, ILobbyObserver> observersInThisLobby = lobbyObservers.get(lobbyID);

        // ONLY PROCEED IF THE LOBBY EXISTS AND HAS PEOPLE LISTENING
        if (observersInThisLobby != null && currentLobby != null)
        {
            // LOOP THROUGH EVERY REGISTERED OBSERVER IN THIS ROOM
            for (Map.Entry<String, ILobbyObserver> entry : observersInThisLobby.entrySet())
            {
                String name = entry.getKey();
                ILobbyObserver obs = entry.getValue();

                try
                {
                    // PUSH THE UPDATED LOBBY OBJECT TO THE CLIENT'S MACHINE
                    obs.onLobbyUpdated(currentLobby);
                }
                catch (RemoteException e)
                {
                    // LOG THE CONNECTION FAILURE AND REMOVE THE DEAD OBSERVER
                    System.out.println("[" + now + "] LOG: CONNECTION LOST TO " + name + " - REMOVING FROM LOBBY #" + lobbyID);
                    observersInThisLobby.remove(name);
                }
            }
            // SEND A LOG WITH THE CHANGE
            System.out.println("[" + now + "] LOG: BROADCAST COMPLETED FOR LOBBY #" + lobbyID + " [ACTION: " + ActionTaken + "]");
        }

    }

    private void notifyFriendListRefresh(String username) {
        if (username == null) return;

        for (Map<String, ILobbyObserver> room : lobbyObservers.values()) {
            ILobbyObserver observer = room.get(username);
            if (observer != null) {
                try {
                    observer.onFriendListUpdated();
                } catch (RemoteException ignored) {}
            }
        }
    }

    private void refreshFriendListsAround(String username) {
        User user = globalUsers.get(username);
        notifyFriendListRefresh(username);

        if (user != null) {
            for (String friendName : user.getFriends()) {
                notifyFriendListRefresh(friendName);
            }
        }
    }


    private void startDisconnectMonitor() {
        disconnectMonitor.scheduleAtFixedRate(() -> {
            for (Map.Entry<Integer, Map<String, ILobbyObserver>> lobbyEntry : new ArrayList<>(lobbyObservers.entrySet())) {
                int lobbyID = lobbyEntry.getKey();
                Map<String, ILobbyObserver> observers = lobbyEntry.getValue();

                if (observers == null) continue;

                for (Map.Entry<String, ILobbyObserver> obsEntry : new ArrayList<>(observers.entrySet())) {
                    String username = obsEntry.getKey();
                    ILobbyObserver observer = obsEntry.getValue();

                    try {
                        observer.ping();
                    } catch (RemoteException e) {
                        handleDeadLobbyObserver(lobbyID, username);
                    }
                }
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void handleDeadLobbyObserver(int lobbyID, String username) {
        String now = dtf.format(LocalDateTime.now());
        User user = globalUsers.get(username);

        Map<String, ILobbyObserver> observers = lobbyObservers.get(lobbyID);
        if (observers != null) {
            observers.remove(username);
        }

        if (user == null) return;

        // If the user was in a game, let GameService start the reconnect window.
        if (user.getStatus() == -1) {
            try {
                IGameService gameService = (IGameService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/game");
                gameService.handleClientDisconnect(lobbyID, username);
            } catch (Exception ignored) {}

            return;
        }

        // Normal lobby/spectator disconnect: user goes offline and leaves the lobby observer/data.
        user.setStatus(0);
        user.setCurrentLobbyID(-1);
        user.setSpectatableGame(false);

        Lobby lobby = activeLobbies.get(lobbyID);
        if (lobby != null) {
            lobby.getSpectators().remove(username);
            lobby.getPlayers().remove(username);

            if (lobby.getOwner() != null && lobby.getOwner().getUsername().equals(username)) {
                if (lobby.getPlayers().isEmpty()) {
                    activeLobbies.remove(lobbyID);
                    lobbyObservers.remove(lobbyID);
                } else {
                    String nextOwnerName = lobby.getPlayers().keySet().iterator().next();
                    lobby.setOwner(globalUsers.get(nextOwnerName));
                    System.out.println("[" + now + "] LOG: DISCONNECTED HOST " + username + ". NEW HOST IS " + nextOwnerName);
                }
            }

            notifyAllPlayers(lobbyID, username + " DISCONNECTED");
        }

        DataStore.saveUsers(globalUsers);
        refreshFriendListsAround(username);
        System.out.println("[" + now + "] LOG: " + username + " DISCONNECTED AND WAS SET OFFLINE");
    }

    // USER JOIN LOBBY SERVICE
    public int joinLobby(int lobbyID, String username) throws RemoteException
    {
        String now = dtf.format(LocalDateTime.now());
        User existingUser = globalUsers.get(username);

        if (existingUser == null)
        {
            System.out.println("[" + now + "] LOG: JOIN FAILED - USER " + username + " NOT FOUND");
            return -1;
        }

        // --- 1. LOBBY CREATION ---
        if (lobbyID == -1)
        {
            lobbyID = lobbyIdCounter.getAndIncrement();
            Lobby nl = new Lobby();
            nl.setLobbyID(lobbyID);
            nl.setOwner(existingUser);

            // Directly assign RED to the creator
            nl.getPlayers().put(username, "RED");

            existingUser.setStatus(1);
            existingUser.setCurrentLobbyID(lobbyID);
            existingUser.setSpectatableGame(false);

            activeLobbies.put(lobbyID, nl);
            refreshFriendListsAround(username);
            System.out.println("[" + now + "] LOG: LOBBY #" + lobbyID + " CREATED. OWNER " + username + " SET TO RED");
            return lobbyID;
        }

        // --- 2. JOINING EXISTING LOBBY ---
        Lobby lobby = activeLobbies.get(lobbyID);
        if (lobby == null) return -1;

        if (!lobby.getPlayers().containsKey(username))
        {
            if (lobby.getPlayers().size() < 4)
            {
                // --- INTEGRATED AUTO-COLOR LOGIC ---
                String[] allColors = {"RED", "BLUE", "GREEN", "YELLOW"};
                String assignedColor = "NONE";

                // Find the first color not currently in the values of the players map
                for (String color : allColors) {
                    if (!lobby.getPlayers().containsValue(color)) {
                        assignedColor = color;
                        break;
                    }
                }

                lobby.getPlayers().put(username, assignedColor);
                existingUser.setStatus(1);
                existingUser.setCurrentLobbyID(lobbyID);
                existingUser.setSpectatableGame(false);
                System.out.println("[" + now + "] LOG: " + username + " JOINED LOBBY #" + lobbyID + " AS " + assignedColor);

                notifyAllPlayers(lobbyID, username + " JOINED AS " + assignedColor);
                refreshFriendListsAround(username);
                return lobbyID;
            }
            else
            {
                return -2; // Full
            }
        }

        existingUser.setStatus(1);
        existingUser.setCurrentLobbyID(lobbyID);
        existingUser.setSpectatableGame(false);
        refreshFriendListsAround(username);
        return lobbyID;
    }

    // PLAYER LEAVE LOBBY SERVICE
    public Boolean leaveLobby(int lobbyID, String username) throws RemoteException
    {
        // We already fixed unsubscribe to handle everything, so just call it!
        unsubscribe(lobbyID, username);
        return true;
    }

    // UPDATE GAME RULES (ONLY BY LOBBY OWNER)
    public void updateGameRules(int lobbyID, String requesterName, int timeLimit, int piecesToWin, boolean mustRollSix, boolean canBlock, boolean allowSpectators) throws RemoteException
    {
        String now = dtf.format(LocalDateTime.now());
        Lobby lobby = activeLobbies.get(lobbyID);

        if (lobby != null) {
            // CHECKING IF USERNAME MATCH OWNER NAME
            if (lobby.getOwner().getUsername().equals(requesterName))
            {
                lobby.setTurnTimeLimit(timeLimit);
                lobby.setPiecesToWin(piecesToWin);
                lobby.setMustRollSixToStart(mustRollSix);
                lobby.setCanBlock(canBlock);
                lobby.setAllowSpectators(allowSpectators);

                System.out.println("[" + now + "] LOG: RULES UPDATED FOR LOBBY #" + lobbyID + " BY HOST " + requesterName);

                notifyAllPlayers(lobbyID, "RULES_UPDATED");
            }
            else
            {
                System.out.println("[" + now + "] LOG: UNAUTHORIZED RULE CHANGE ATTEMPT BY " + requesterName + " IN LOBBY #" + lobbyID);
            }
        }
    }

    // SET COLOR
    public void selectColor(int lobbyID, String adminRequester, String targetUsername, String color) throws RemoteException
    {
        Lobby lobby = activeLobbies.get(lobbyID);

        if (lobby != null) {
            // OWNER CHECK
            if (!lobby.getOwner().getUsername().equals(adminRequester)) {
                System.out.println("LOG: UNAUTHORIZED COLOR CHANGE ATTEMPT BY " + adminRequester);
                return;
            }

            String currentPlayerColor = lobby.getPlayers().get(targetUsername);
            String playerWhoHadThatColor = null;

            // SWAP LOGIC IF SOMEONE ELSE HAS THE CURRENT CHOSEN COLOR
            for (Map.Entry<String, String> entry : lobby.getPlayers().entrySet()) {
                if (entry.getValue().equals(color) && !entry.getKey().equals(targetUsername) && !color.equals("NONE")) {
                    playerWhoHadThatColor = entry.getKey();
                    break;
                }
            }

            if (playerWhoHadThatColor != null) {
                lobby.getPlayers().put(playerWhoHadThatColor, currentPlayerColor);
            }

            lobby.getPlayers().put(targetUsername, color);

            notifyAllPlayers(lobbyID, "OWNER SET " + targetUsername + " TO " + color);
        }
    }

    // START MATCH SERVICE - TRIGGERS THE TRANSITION FROM LOBBY TO GAMEBOARD
    public Boolean startGame(int lobbyID, String requesterName) throws RemoteException {
        Lobby lobby = activeLobbies.get(lobbyID);
        if (lobby == null) return false;

        // 1. STANDARD VALIDATIONS
        if (!lobby.getOwner().getUsername().equals(requesterName)) return false;
        if (lobby.getPlayers().size() < 2) return false;

        try {
            // 2. THE MULTI-PC BRIDGE
            // Even if this is on another PC, Naming.lookup finds it via the IP
            // In a real multi-PC setup, you'd replace 127.0.0.1 with the Game Server's IP
            IGameService gameService = (IGameService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/game");

            // 3. INHERIT RULES FROM LOBBY
            gameService.createNewGameSession(
                    lobby.getLobbyID(),
                    lobby.getPlayers(),
                    lobby.isMustRollSixToStart(),
                    lobby.isCanBlock(),
                    lobby.getPiecesToWin(),
                    lobby.getTurnTimeLimit()
            );

            lobby.setGameStarted(true);

            for (String playerName : lobby.getPlayers().keySet()) {
                User player = globalUsers.get(playerName);
                if (player != null) {
                    player.setStatus(-1);
                    player.setCurrentLobbyID(lobbyID);
                    player.setSpectatableGame(lobby.isAllowSpectators());
                    refreshFriendListsAround(playerName);
                }
            }

            // 4. SIGNAL CLIENTS
            notifyAllPlayers(lobbyID, "MATCH_START_SIGNAL");
            return true;

        } catch (Exception e) {
            System.err.println("CRITICAL: Lobby could not contact Game Service. Is the Game Server running?");
            e.printStackTrace();
            return false;
        }
    }

    // SPECTATOR FLOW - WATCH A GAME WITHOUT JOINING THE PLAYERS MAP
    @Override
    public boolean canSpectate(int lobbyID) throws RemoteException {
        Lobby lobby = activeLobbies.get(lobbyID);
        return lobby != null && lobby.isGameStarted() && lobby.isAllowSpectators();
    }

    @Override
    public boolean addSpectator(int lobbyID, String username, ILobbyObserver observer) throws RemoteException {
        String now = dtf.format(LocalDateTime.now());
        Lobby lobby = activeLobbies.get(lobbyID);

        if (lobby == null || !lobby.isGameStarted() || !lobby.isAllowSpectators()) {
            return false;
        }

        // A real player in this match is not a spectator.
        if (lobby.getPlayers().containsKey(username)) {
            return false;
        }

        Map<String, ILobbyObserver> roomObservers = lobbyObservers.get(lobbyID);
        if (roomObservers == null) {
            roomObservers = new ConcurrentHashMap<String, ILobbyObserver>();
            lobbyObservers.put(lobbyID, roomObservers);
        }

        if (!lobby.getSpectators().contains(username)) {
            lobby.getSpectators().add(username);
        }
        roomObservers.put(username, observer);

        System.out.println("[" + now + "] LOG: " + username + " IS NOW SPECTATING LOBBY #" + lobbyID);
        notifyAllPlayers(lobbyID, username + " STARTED SPECTATING");
        return true;
    }

    @Override
    public void removeSpectator(int lobbyID, String username) throws RemoteException {
        String now = dtf.format(LocalDateTime.now());
        Lobby lobby = activeLobbies.get(lobbyID);

        if (lobby != null) {
            lobby.getSpectators().remove(username);
        }

        Map<String, ILobbyObserver> roomObservers = lobbyObservers.get(lobbyID);
        if (roomObservers != null) {
            roomObservers.remove(username);
        }

        System.out.println("[" + now + "] LOG: " + username + " STOPPED SPECTATING LOBBY #" + lobbyID);
        notifyAllPlayers(lobbyID, username + " STOPPED SPECTATING");
    }


    @Override
    public void markGameEnded(int lobbyID) throws RemoteException {
        Lobby lobby = activeLobbies.get(lobbyID);
        if (lobby == null) return;

        // Keep player status as IN GAME until the host sends them back,
        // but stop showing the SPECTATE option after the match ended.
        lobby.setGameStarted(false);
        for (String playerName : lobby.getPlayers().keySet()) {
            User player = globalUsers.get(playerName);
            if (player != null) {
                if (player.getStatus() != 0) {
                    player.setStatus(-1);
                    player.setCurrentLobbyID(lobbyID);
                }
                player.setSpectatableGame(false);
                refreshFriendListsAround(playerName);
            }
        }

        notifyLeaderboardUpdated();
        notifyAllPlayers(lobbyID, "GAME_ENDED");
    }

    @Override
    public void returnGameToLobby(int lobbyID, String requesterName) throws RemoteException {
        String now = dtf.format(LocalDateTime.now());
        Lobby lobby = activeLobbies.get(lobbyID);

        if (lobby == null) return;
        if (lobby.getOwner() == null || !lobby.getOwner().getUsername().equals(requesterName)) {
            System.out.println("[" + now + "] LOG: UNAUTHORIZED RETURN-TO-LOBBY ATTEMPT BY " + requesterName + " IN LOBBY #" + lobbyID);
            return;
        }

        lobby.setGameStarted(false);

        for (String playerName : lobby.getPlayers().keySet()) {
            User player = globalUsers.get(playerName);
            if (player != null) {
                if (player.getStatus() != 0) {
                    player.setStatus(1);
                    player.setCurrentLobbyID(lobbyID);
                }
                player.setSpectatableGame(false);
                refreshFriendListsAround(playerName);
            }
        }

        Map<String, ILobbyObserver> observersInThisLobby = lobbyObservers.get(lobbyID);
        if (observersInThisLobby != null) {
            for (ILobbyObserver obs : new java.util.ArrayList<>(observersInThisLobby.values())) {
                try {
                    obs.onReturnToLobby();
                } catch (RemoteException ignored) {}
            }
        }

        lobby.getSpectators().clear();

        System.out.println("[" + now + "] LOG: HOST RETURNED ALL GAME CLIENTS TO LOBBY #" + lobbyID);
        notifyAllPlayers(lobbyID, "RETURN_TO_LOBBY");
    }


    @Override
    public void notifyLeaderboardUpdated() throws RemoteException {
        // Push leaderboard refresh to every connected lobby client.
        // This uses the same callback style as friend list and lobby updates.
        for (Map<String, ILobbyObserver> room : lobbyObservers.values()) {
            for (ILobbyObserver observer : room.values()) {
                try {
                    observer.onLeaderboardUpdated();
                } catch (RemoteException ignored) {}
            }
        }
    }

    // SEND CHAT MESSAGE
    public void sendChatMessage(int lobbyID, String username, String message) throws RemoteException
    {
        String now = dtf.format(LocalDateTime.now());
        Lobby lobby = activeLobbies.get(lobbyID);
        Map<String, ILobbyObserver> observers = lobbyObservers.get(lobbyID);

        // STOP IF LOBBY DON'T EXIST
        if (lobby != null && observers != null)
        {
            // FORMAT THE MESSAGE
            String formattedMsg = username + ": " + message;

            // PERSIST IN LOBBY DATA (Optional, but good for history)
            lobby.addChat(formattedMsg);

            // BROADCAST TO ALL OBS
            for (Map.Entry<String, ILobbyObserver> entry : observers.entrySet())
            {
                try
                {
                    entry.getValue().onMessageReceived(formattedMsg);
                }
                catch (RemoteException e)
                {
                    // CONNECTION DEAD
                }
            }

            System.out.println("[" + now + "] CHAT LOG: [Lobby #" + lobbyID + "] " + formattedMsg);
        }
    }

    public void kickPlayer(int lobbyID, String adminRequester, String targetUsername) throws RemoteException {
        String now = dtf.format(LocalDateTime.now());
        Lobby lobby = activeLobbies.get(lobbyID);

        if (lobby == null || !lobby.getOwner().getUsername().equals(adminRequester)) return;

        // 1. SIGNAL THE KICKED PLAYER FIRST
        Map<String, ILobbyObserver> observers = lobbyObservers.get(lobbyID);
        if (observers != null && observers.containsKey(targetUsername)) {
            try {
                observers.get(targetUsername).onKicked(); // <--- DEDICATED SIGNAL
            } catch (RemoteException e) {
                // Already disconnected
            }
        }

        // 2. CLEANUP ON SERVER
        lobby.getPlayers().remove(targetUsername);
        unsubscribe(lobbyID, targetUsername);

        // 3. REFRESH FOR OTHERS
        System.out.println("[" + now + "] LOG: " + targetUsername + " WAS KICKED FROM LOBBY #" + lobbyID);
        notifyAllPlayers(lobbyID, targetUsername + " WAS KICKED BY OWNER");
    }




}
