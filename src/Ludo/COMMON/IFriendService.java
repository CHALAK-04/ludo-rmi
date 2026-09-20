package Ludo.COMMON;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface IFriendService extends Remote
{
    // SEND FRIEND REQUEST SERVICE
    boolean sendFriendRequest(String sender, String receiver) throws RemoteException;

    // ACCEPT/REJECT
    Boolean respondToRequest(String sender, String receiver) throws RemoteException;

    // LIST OF FRIEND
    ArrayList<User> getFriendList(String username) throws RemoteException;

    // REMOVE A FRIEND
    Boolean removeFriend(String user, String friend) throws RemoteException;

    // DELIVER FRIEND REQUESTS THAT ARRIVED WHILE USER WAS OFFLINE
    void deliverPendingFriendRequests(String username) throws RemoteException;

    //REFRESH THE USER UI
    void refreshUserUI(String username) throws RemoteException;

}
