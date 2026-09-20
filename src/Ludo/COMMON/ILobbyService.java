package Ludo.COMMON;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ILobbyService extends Remote
{
    // GET LOBBY DATA
    Lobby getLobbyData(int lobbyID) throws RemoteException;

    // JOIN A LOBBY
    int joinLobby(int lobbyID, String username) throws RemoteException;

    // LEAVE A LOBBY
    Boolean leaveLobby(int lobbyID, String username) throws RemoteException;

    // CONFIGURE A MATCH
    void updateGameRules(int lobbyID, String requesterName, int timeLimit, int piecesToWin, boolean mustRollSix, boolean canBlock, boolean allowSpectators) throws RemoteException;

    // SELECT A COLOR FOR A PLAYER
    void selectColor(int lobbyID, String requesterName, String username, String color) throws RemoteException;

    // START GAME
    Boolean startGame(int lobbyID, String requesterName) throws RemoteException;

    // SPECTATOR FLOW
    boolean canSpectate(int lobbyID) throws RemoteException;
    boolean addSpectator(int lobbyID, String username, ILobbyObserver observer) throws RemoteException;
    void removeSpectator(int lobbyID, String username) throws RemoteException;

    // END-GAME FLOW
    void markGameEnded(int lobbyID) throws RemoteException;
    void returnGameToLobby(int lobbyID, String requesterName) throws RemoteException;

    // SEND TEXT MESSAGES
    void sendChatMessage(int lobbyID, String username, String message) throws RemoteException;

    // KICK PLAYERS IN LOBBY
    void kickPlayer(int lobbyID, String adminRequester, String targetUsername) throws RemoteException;

    // NOTIFICATIONS
    void notifyLeaderboardUpdated() throws RemoteException;
    void subscribe(int lobbyID, String username, ILobbyObserver observer) throws RemoteException;
    void unsubscribe(int lobbyID, String username) throws RemoteException;

}
