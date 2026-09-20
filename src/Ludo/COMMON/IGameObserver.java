package Ludo.COMMON;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IGameObserver extends Remote
{
    // LIGHTWEIGHT CALL USED BY SERVER TO DETECT DEAD CLIENTS
    boolean ping() throws RemoteException;

    // Pushes the entire state to the client for rendering
    void onGameStateUpdated(GameState newState) throws RemoteException;

    // Optional: for specific animations or sounds on the client
    void onDiceRolled(String player, int value) throws RemoteException;

    void onGameEnded(String winner, String loser) throws RemoteException;
}
