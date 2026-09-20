package Ludo.CLIENT;

import Ludo.COMMON.GameState;
import Ludo.COMMON.IGameObserver;
import Ludo.COMMON.ILobbyObserver;
import Ludo.COMMON.Lobby;
import javax.swing.*;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class GameObserverImpl extends UnicastRemoteObject implements IGameObserver, ILobbyObserver {

    private GameBoard gui;

    public GameObserverImpl(GameBoard gui) throws RemoteException {
        super();
        this.gui = gui;
    }

    public boolean ping() throws RemoteException {
        return true;
    }

    // ---------- IGameObserver ----------
    @Override
    public void onGameStateUpdated(GameState newState) throws RemoteException {
        SwingUtilities.invokeLater(() -> gui.refreshBoard(newState));
    }

    @Override
    public void onDiceRolled(String player, int value) throws RemoteException {
        SwingUtilities.invokeLater(() -> gui.showDiceRoll(player, value));
    }

    @Override
    public void onGameEnded(String winner, String loser) throws RemoteException {
        SwingUtilities.invokeLater(() -> gui.showGameEnded(winner, loser));
    }

    // ---------- ILobbyObserver (chat only) ----------
    @Override
    public void onMessageReceived(String message) throws RemoteException {
        SwingUtilities.invokeLater(() -> gui.addChatMessage(message));
    }

    // Keep lightweight lobby information, such as spectator count, synced during the game.
    @Override public void onLobbyUpdated(Lobby updatedLobby) throws RemoteException {
        SwingUtilities.invokeLater(() -> gui.refreshLobbyInfo(updatedLobby));
    }
    @Override public void notifyFriendRequest(String sender) throws RemoteException {}
    @Override public void notifyReconnectOffer(int lobbyID) throws RemoteException {}
    @Override public void onFriendListUpdated() throws RemoteException {}
    @Override public void onLeaderboardUpdated() throws RemoteException {}
    @Override public void onKicked() throws RemoteException {}

    @Override
    public void onReturnToLobby() throws RemoteException {
        SwingUtilities.invokeLater(() -> gui.returnToLobbyFromHostSignal());
    }
}