package Ludo.CLIENT;

import Ludo.COMMON.ILobbyObserver;
import Ludo.COMMON.Lobby;

import javax.swing.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class LobbyObserverImpl extends UnicastRemoteObject implements ILobbyObserver
{
    private LobbyForm gui;

    public LobbyObserverImpl(LobbyForm gui) throws RemoteException
    {
        super();
        this.gui = gui;
    }

    public boolean ping() throws RemoteException {
        return true;
    }

    public void onLobbyUpdated(Lobby updatedLobby) throws RemoteException
    {
        // Swing is not thread-safe, so always use invokeLater for RMI callbacks
        SwingUtilities.invokeLater(() -> gui.refreshLobbyUI(updatedLobby));
    }

    public void onMessageReceived(String message) throws RemoteException
    {
        SwingUtilities.invokeLater(() -> gui.updateChatArea(message));
    }

    public void notifyFriendRequest(String sender) throws RemoteException
    {
        SwingUtilities.invokeLater(() -> gui.showFriendRequestPopUp(sender));
    }

    public void notifyReconnectOffer(int lobbyID) throws RemoteException
    {
        SwingUtilities.invokeLater(() -> gui.showReconnectOffer(lobbyID));
    }

    public void onFriendListUpdated() throws RemoteException {
        // This tells the LobbyForm to go get the new list from the server immediately
        SwingUtilities.invokeLater(() -> gui.refreshFriendList());
    }

    public void onLeaderboardUpdated() throws RemoteException {
        SwingUtilities.invokeLater(() -> gui.refreshLeaderboard());
    }

    public void onKicked() throws RemoteException {
        // We tell the GUI to reset the player into a fresh lobby
        SwingUtilities.invokeLater(() -> gui.rejoinNewLobby());
    }

    public void onReturnToLobby() throws RemoteException {
        // Lobby screens can simply refresh through normal lobby updates.
    }

}
