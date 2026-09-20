package Ludo.COMMON;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ILobbyObserver extends Remote
{
    // LIGHTWEIGHT CALL USED BY SERVER TO DETECT DEAD CLIENTS
    boolean ping() throws RemoteException;

    // THIS IS CALLED WHENEVER SOMEONE JOINS, LEAVES, CHANGE COLOR...
    void onLobbyUpdated(Lobby updatedLobby) throws RemoteException;

    // THIS IS ESPECIALLY FOR CHATBOX
    void onMessageReceived(String message) throws RemoteException;

    // DEDICATED FOR FRIEND REQUESTS
    void notifyFriendRequest(String sender) throws RemoteException;

    // DEDICATED FOR GAME RECONNECT OFFER
    void notifyReconnectOffer(int lobbyID) throws RemoteException;

    //DEDICATED FOR FRIEND LIST UPDATES
    void onFriendListUpdated() throws RemoteException;

    // DEDICATED FOR LEADERBOARD UPDATES
    void onLeaderboardUpdated() throws RemoteException;

    // DEDICATED FOR LOBBY KICKS
    void onKicked() throws RemoteException;

    // DEDICATED FOR END-GAME RETURN TO LOBBY SIGNAL
    void onReturnToLobby() throws RemoteException;




}
