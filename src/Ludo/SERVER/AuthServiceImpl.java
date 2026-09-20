package Ludo.SERVER;

import Ludo.COMMON.IAuthService;
import Ludo.COMMON.IFriendService;
import Ludo.COMMON.User;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;

public class AuthServiceImpl extends UnicastRemoteObject implements IAuthService
{
    // NEEDED FOR LOGS TIMESTAMPS
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");


    // WE WILL HOLD THE LIST OF USERS IN A CONCURRENT HASHMAP
    // STRING FIELD IN THE HASHMAP WILL HOLD USERNAME OF THE PLAYER
    private ConcurrentHashMap<String, User> users;



    public AuthServiceImpl(ConcurrentHashMap<String, User> sharedUsers) throws RemoteException
    {
        this.users = sharedUsers;

    }


    // IMPLEMENTATION OF THE USER REGISTRATION SERVICE
    public User register(String username, String password) throws RemoteException
    {
        //GETTING CURRENT TIME FOR LOGS
        String now = dtf.format(LocalDateTime.now());

        // USERNAME ALREADY IN USE
        if (users.containsKey(username))
        {
            System.out.println("[" + now + "] LOG: " + username + " HAS BEEN UNSUCCESSFULLY REGISTERED, USERNAME ALREADY EXISTS");
            return null;
        }

        // CREATING A NEW USER
        User newUser = new User(username, password);

        // ADDING THE USER TO THE HASHMAP
        users.put(username, newUser);

        // SETTING THE USER TO ONLINE
        newUser.setStatus(1);

        // SAVE NEW ACCOUNT ON THE SERVER FILE
        DataStore.saveUsers(users);

        System.out.println("[" + now + "] LOG: " + username + " HAS BEEN SUCCESSFULLY REGISTERED AND LOGGED IN");

        triggerFriendRefresh(newUser);

        return newUser;
    }

    // IMPLEMENTATION OF THE USER LOGIN SERVICE
    public User login(String username, String password) throws RemoteException
    {
        //GETTING CURRENT TIME FOR LOGS
        String now = dtf.format(LocalDateTime.now());

        // PULLING THE USER FROM THE MAP
        User u = users.get(username);

        // CHECK IF USER EXISTS AND PASSWORD MATCHES
        if (u != null && u.getPassword().equals(password))
        {
            // CHECK IF THE USER IS NOT OFFLINE
            if (u.getStatus() != 0) {
                System.out.println("[" + now + "] LOG: " + username + " FAILED LOGIN - ALREADY ONLINE/IN GAME");
                throw new RemoteException("ALREADY_LOGGED_IN");
            }

            // SETTING THE USER TO ONLINE
            u.setStatus(1);

            System.out.println("[" + now + "] LOG: " + username + " HAS BEEN SUCCESSFULLY LOGGED IN");

            triggerFriendRefresh(u);

            // SENDS THE USER OBJECT (PASSWORD WILL BE NULL ON CLIENT SIDE BECAUSE OF TRANSIENT)
            return u;
        }

        // 3. LOG THE FAILURE
        System.out.println("[" + now + "] LOG: " + username + " FAILED TO LOGIN - INVALID CREDENTIALS");
        return null;
    }


    public boolean logout(String username) throws RemoteException {
        String now = dtf.format(LocalDateTime.now());
        User u = users.get(username);

        if (u != null) {
            u.setStatus(0);
            u.setCurrentLobbyID(-1);
            u.setSpectatableGame(false);

            // SAVE LATEST USER DATA ON LOGOUT
            DataStore.saveUsers(users);

            System.out.println("[" + now + "] LOG: " + username + " HAS BEEN SUCCESSFULLY LOGGED OUT");

            // Force refresh for all friends so they see "OFFLINE" status immediately
            triggerFriendRefresh(u);
            return true;
        }
        return false;
    }


    public ArrayList<User> getLeaderboard() throws RemoteException {
        ArrayList<User> leaderboard = new ArrayList<>();

        // ONLY PLAYERS WHO WON AT LEAST ONCE SHOULD APPEAR ON THE LEADERBOARD.
        // THIS PREVENTS NEW ACCOUNTS OR LOSS-ONLY PLAYERS FROM LOOKING RANKED.
        for (User user : users.values()) {
            if (user.getWins() > 0) {
                leaderboard.add(user);
            }
        }

        leaderboard.sort(new Comparator<User>() {
            @Override
            public int compare(User a, User b) {
                int gamesA = a.getWins() + a.getLosses();
                int gamesB = b.getWins() + b.getLosses();

                double rateA = gamesA == 0 ? 0 : (double) a.getWins() / gamesA;
                double rateB = gamesB == 0 ? 0 : (double) b.getWins() / gamesB;

                // FIRST RANKING: HIGHEST WIN RATE.
                int cmp = Double.compare(rateB, rateA);
                if (cmp != 0) return cmp;

                // SECOND RANKING: MORE WINS.
                cmp = Integer.compare(b.getWins(), a.getWins());
                if (cmp != 0) return cmp;

                // THIRD RANKING: FEWER LOSSES.
                cmp = Integer.compare(a.getLosses(), b.getLosses());
                if (cmp != 0) return cmp;

                // FINAL RANKING: STABLE ALPHABETICAL ORDER.
                return a.getUsername().compareToIgnoreCase(b.getUsername());
            }
        });

        ArrayList<User> topThree = new ArrayList<>();

        for (User user : leaderboard) {
            if (topThree.size() == 3) break;
            topThree.add(user);
        }

        return topThree;
    }

    // HELPER METHOD TO REFRESH FRIEND LIST
    private void triggerFriendRefresh(User user) {
        try {
            IFriendService friendService = (IFriendService) java.rmi.Naming.lookup("rmi://127.0.0.1:2000/friend");
            for (String friendName : user.getFriends()) {
                friendService.refreshUserUI(friendName);
            }
        } catch (Exception e) {
            // Silently handle if registry isn't ready or friendService is down
        }
    }
}
