package Ludo.COMMON;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

public interface IGameService extends Remote {
    // Existing
    void createNewGameSession(int lobbyID, Map<String, String> players,
                              boolean rollSixToStart, boolean canBlock, int piecesToWin,
                              int turnTimeLimit) throws RemoteException;
    void subscribe(int lobbyID, String username, IGameObserver observer) throws RemoteException;
    void unsubscribe(int lobbyID, String username) throws RemoteException;

    // Dice
    int rollDice(int lobbyID, String username) throws RemoteException;   // return the value

    // Valid moves – tells the client which of his 4 pieces can move now
    List<Integer> getValidMoves(int lobbyID, String username) throws RemoteException;

    // Move – returns success/failure
    boolean movePiece(int lobbyID, String username, int pieceID) throws RemoteException;

    // Optional: end turn manually (for timeouts)
    void endTurn(int lobbyID, String username) throws RemoteException;

    // Player leaves an active game voluntarily; server adds one loss.
    boolean forfeitGame(int lobbyID, String username) throws RemoteException;

    // Unexpected disconnect/reconnect handling
    void handleClientDisconnect(int lobbyID, String username) throws RemoteException;
    int getPendingReconnectLobby(String username) throws RemoteException;
    boolean reconnectToGame(int lobbyID, String username, IGameObserver observer) throws RemoteException;
    void rejectReconnect(String username) throws RemoteException;

    GameState getGameState(int lobbyID) throws RemoteException;
}