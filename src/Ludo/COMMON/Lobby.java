package Ludo.COMMON;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// LOBBY IS THE SESSION THAT GROUP PLAYERS FOR A MATCH
public class Lobby implements Serializable
{
    // LOBBY ID
    private int LobbyID;

    // LOBBY LEADER
    private User Owner;

    // CONCURRENT HASHMAP TO HOLD ALL PLAYERS USERNEAME IN SESSION, AND THE STRING FIELD WILL BE FOR THE PLAYER COLOR
    private Map<String, String> players = new ConcurrentHashMap<>();

    // SIMPLE LIST TO HOLD THE USERNAMES THAT WILL SPECTATE THE MATCH
    private List<String> spectators = new CopyOnWriteArrayList<>();

    // BOOLEAN TO ALLOW SPECTATORS
    private boolean allowSpectators = true;

    // TIME LIMIT PER PLAYER
    private int turnTimeLimit;

    // BOOLEAN MUST ROLL 6 TO START
    private boolean mustRollSixToStart;

    // RULE THAT MAKE 2 PIECES OF SAME COLOR ON 1 SQUARE CREATE A WALL
    private boolean canBlock;

    // NUMBER OF REQUIRED PIECES TO WIN
    private int piecesToWin = 4;

    // LIST DEDICATED FOR CHATS
    private List<String> chatHistory = new CopyOnWriteArrayList<>();

    // Inside the class, next to the other booleans
    private boolean gameStarted = false;

    // Getter & Setter
    public boolean isGameStarted() { return gameStarted; }
    public void setGameStarted(boolean gameStarted) { this.gameStarted = gameStarted; }

    // GETTERS & SETTERS
    public int getLobbyID() { return LobbyID; }
    public void setLobbyID(int lobbyID) { LobbyID = lobbyID; }

    public User getOwner() { return Owner; }
    public void setOwner(User owner) { Owner = owner; }

    public Map<String, String> getPlayers() { return players; }

    public List<String> getSpectators() { return spectators; }

    public List<String> getChatHistory() { return chatHistory; }
    public void addChat(String msg) { this.chatHistory.add(msg); }

    // SETTERS FOR GAME OPTION
    public void setTurnTimeLimit(int limit) { this.turnTimeLimit = limit; }
    public void setMustRollSixToStart(boolean val) { this.mustRollSixToStart = val; }
    public void setCanBlock(boolean val) { this.canBlock = val; }
    public void setPiecesToWin(int count) { this.piecesToWin = count; }

    // GETTERS FOR GAME OPTIONS
    public boolean isAllowSpectators() { return allowSpectators; }
    public void setAllowSpectators(boolean allowSpectators) { this.allowSpectators = allowSpectators; }

    public int getTurnTimeLimit() { return turnTimeLimit; }
    public boolean isMustRollSixToStart() { return mustRollSixToStart; }
    public boolean isCanBlock() { return canBlock; }
    public int getPiecesToWin() { return piecesToWin; }
}
