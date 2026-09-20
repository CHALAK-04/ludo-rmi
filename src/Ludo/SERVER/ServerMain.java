package Ludo.SERVER;

import Ludo.COMMON.*;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

// THIS SCRIPT RUNS THE LUDO GAME SERVER
public class ServerMain
{
    // KEEP STRONG REFERENCES TO EXPORTED RMI OBJECTS.
    // WITHOUT THESE STATIC REFERENCES, THE JVM MAY GARBAGE-COLLECT A SERVICE
    // WHILE THE REGISTRY STILL HOLDS AN OLD STUB.
    private static IAuthService authService;
    private static ILobbyService lobbyService;
    private static IFriendService friendService;
    private static IGameService gameService;

    public  static void main(String[] args)
    {
        try
        {
            // CREATING A SHARED MEMORY FOR USERS
            ConcurrentHashMap<String, User> sharedUsers = new ConcurrentHashMap<>();
            DataStore.loadUsers(sharedUsers);

            ConcurrentHashMap<Integer, Map<String, ILobbyObserver>> sharedObservers = new ConcurrentHashMap<>();
            ConcurrentHashMap<Integer, Map<String, IGameObserver>> gameObservers = new ConcurrentHashMap<>();



            // IMPLEMENTING THE AUTHENTICATION AS THE FIRST SERVICE
            authService = new AuthServiceImpl(sharedUsers);

            // IMPLEMENTING THE LOBBY AS THE SECOND SERVICE
            lobbyService = new LobbyServiceImpl(sharedUsers, sharedObservers);

            // IMPLEMENTING THE FRIEND SERVICE AS THE THIRD SERVICE
            friendService = new FriendServiceImpl(sharedUsers, sharedObservers);

            // IMPLEMENTING THE GAME SERVICE
            gameService = new GameServiceImpl(sharedUsers, gameObservers);




            // RUNNING THE LUDO GAME SERVER ON PORT 2000

            // BINDING WITH AUTHENTICATION SERVICE
            Naming.rebind("rmi://127.0.0.1:2000/auth", authService);

            // BINDING WITH LOBBY SERVICE
            Naming.rebind("rmi://127.0.0.1:2000/lobby", lobbyService);

            // BINDING WITH FRIEND SERVICE
            Naming.rebind("rmi://127.0.0.1:2000/friend", friendService);

            // BINDING WITH GAME SERVICE
            Naming.rebind("rmi://127.0.0.1:2000/game", gameService);


            System.out.println("---------------");
            System.out.println("SERVER IS READY");
            System.out.println("---------------");

        }
        catch (RemoteException e)
        {

            Logger.getLogger(ServerMain.class.getName()).log(Level.SEVERE, null, e);
            throw new RuntimeException(e);
        }
        catch (MalformedURLException e1)
        {
            Logger.getLogger(ServerMain.class.getName()).log(Level.SEVERE, null, e1);
            throw new RuntimeException(e1);
        }


    }
}
