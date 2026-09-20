package Ludo.COMMON;

import java.io.Serializable;
import java.util.ArrayList;

// USERS NEED TO BE SENT OVER THE NETWORK SO WE USE "SERIALIZABLE"
public class User implements Serializable
{
    // USER USERNAME
    private String username;

    // USER PASSWORD (TRANSIENT SO IT IS NOOT SENT OVER THE NETWORK)
    private transient String password;

    // NUMBER OF USER'S WINS
    private  int wins;

    // NUMBER OF USER'S LOSSES
    private  int losses;

    // FRIENDS ARE STORED IN A ARRAYLIST
    private ArrayList<String> friends;

    // FRIEND REQUESTS RECEIVED WHILE THE USER WAS OFFLINE
    private ArrayList<String> pendingFriendRequests;

    //  STATUS: 0-->OFFLINE | 1-->ONLINE | -1-->IN GAME
    private int status;

    // LOBBY/GAME INFO USED BY FRIEND LIST AND SPECTATE FLOW
    private int currentLobbyID = -1;
    private boolean spectatableGame = false;

    // USER CONSTRUCTOR
    public User(String username, String password)
    {
        this.username = username;
        this.password = password;
        this.status = 1;
        this.friends = new ArrayList<>();
        this.pendingFriendRequests = new ArrayList<>();
        this.losses = 0;
        this.wins = 0;

    }

    // GETTERS AND SETTERS
    public String getUsername() { return username; }
    public ArrayList<String> getFriends() { return friends; }
    public ArrayList<String> getPendingFriendRequests() { return pendingFriendRequests; }
    public void addWin() { this.wins++; }
    public void addLoss() { this.losses++; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public int getWins() { return wins; }
    public int getLosses() { return losses; }
    public String getPassword() { return password; }
    public void setWins(int wins) { this.wins = wins; }
    public void setLosses(int losses) { this.losses = losses; }
    public int getCurrentLobbyID() { return currentLobbyID; }
    public void setCurrentLobbyID(int currentLobbyID) { this.currentLobbyID = currentLobbyID; }
    public boolean isSpectatableGame() { return spectatableGame; }
    public void setSpectatableGame(boolean spectatableGame) { this.spectatableGame = spectatableGame; }



}
