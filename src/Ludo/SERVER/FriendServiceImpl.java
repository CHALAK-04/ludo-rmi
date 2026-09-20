package Ludo.SERVER;

import Ludo.COMMON.IFriendService;
import Ludo.COMMON.ILobbyObserver;
import Ludo.COMMON.User;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FriendServiceImpl extends UnicastRemoteObject implements IFriendService {

    private ConcurrentHashMap<String, User> globalUsers;
    private Map<Integer, Map<String, ILobbyObserver>> sharedObservers;
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public FriendServiceImpl(ConcurrentHashMap<String, User> sharedUsers, Map<Integer, Map<String, ILobbyObserver>> sharedObservers) throws RemoteException
    {
        this.globalUsers = sharedUsers;
        this.sharedObservers = sharedObservers;
    }


    private ILobbyObserver findObserver(String username) {
        for (Map<String, ILobbyObserver> room : sharedObservers.values()) {
            if (room.containsKey(username)) {
                return room.get(username);
            }
        }
        return null;
    }

    private boolean pushFriendNotification(String receiver, String payload) {
        ILobbyObserver observer = findObserver(receiver);

        if (observer == null) {
            return false;
        }

        try {
            observer.notifyFriendRequest(payload);
            return true;
        } catch (RemoteException e) {
            return false;
        }
    }

    // FUNCTION TO SEND FRIEND REQUEST
    public boolean sendFriendRequest(String sender, String receiver) throws RemoteException
    {
        String now = dtf.format(LocalDateTime.now());
        User receiverUser = globalUsers.get(receiver);

        // 1. Check if receiver even exists
        if (receiverUser == null) return false;

        // 2. Invitations are live-only because lobby state may change while the receiver is offline.
        if (sender.startsWith("INVITE:")) {
            boolean pushed = pushFriendNotification(receiver, sender);
            if (pushed) {
                System.out.println("[" + now + "] LOG: INVITATION PUSHED FROM [" + sender + "] TO [" + receiver + "]");
            }
            return pushed;
        }

        // 3. Normal friend request validation
        User senderUser = globalUsers.get(sender);

        // Safety check if sender doesn't exist for some reason
        if (senderUser == null) return false;

        // Already friends or already pending
        if (senderUser.getFriends().contains(receiver)) return false;
        if (receiverUser.getPendingFriendRequests().contains(sender)) return true;

        // 4. If receiver is online, push immediately.
        if (pushFriendNotification(receiver, sender)) {
            System.out.println("[" + now + "] LOG: FRIEND REQUEST PUSHED FROM [" + sender + "] TO [" + receiver + "]");
            return true;
        }

        // 5. Receiver is offline or has no observer yet, so store request until next login/lobby subscribe.
        receiverUser.getPendingFriendRequests().add(sender);
        DataStore.saveUsers(globalUsers);

        System.out.println("[" + now + "] LOG: FRIEND REQUEST FROM [" + sender + "] TO [" + receiver + "] SAVED AS PENDING");
        return true;
    }

    // FUNCTION TO RESPOND TO FRIEND REQUEST
    public Boolean respondToRequest(String sender, String receiver) throws RemoteException
    {
        String now = dtf.format(LocalDateTime.now());
        User senderUser = globalUsers.get(sender);
        User receiverUser = globalUsers.get(receiver);

        if (senderUser != null && receiverUser != null) {
            // PREVENTS DUPLICATES
            senderUser.getPendingFriendRequests().remove(receiver);
            receiverUser.getPendingFriendRequests().remove(sender);

            if (!senderUser.getFriends().contains(receiver)) senderUser.getFriends().add(receiver);
            if (!receiverUser.getFriends().contains(sender)) receiverUser.getFriends().add(sender);

            System.out.println("[" + now + "] LOG: " + sender + " AND " + receiver + " ARE NOW FRIENDS");

            // SAVE FRIEND LISTS ON THE SERVER FILE
            DataStore.saveUsers(globalUsers);

            // TRIGGER UI REFRESH FOR BOTH
            refreshUserUI(sender);
            refreshUserUI(receiver);

            return true;
        }
        return false;
    }


    public void deliverPendingFriendRequests(String username) throws RemoteException {
        String now = dtf.format(LocalDateTime.now());
        User user = globalUsers.get(username);

        if (user == null) return;
        if (user.getPendingFriendRequests().isEmpty()) return;

        ILobbyObserver observer = findObserver(username);

        if (observer == null) return;

        ArrayList<String> delivered = new ArrayList<>();

        for (String sender : new ArrayList<>(user.getPendingFriendRequests())) {
            try {
                observer.notifyFriendRequest(sender);
                delivered.add(sender);
                System.out.println("[" + now + "] LOG: PENDING FRIEND REQUEST FROM [" + sender + "] DELIVERED TO [" + username + "]");
            } catch (RemoteException ignored) {}
        }

        if (!delivered.isEmpty()) {
            user.getPendingFriendRequests().removeAll(delivered);
            DataStore.saveUsers(globalUsers);
        }

        refreshUserUI(username);
    }

    // HELPER FUNCTION TO FORCE A REFRESH
    public void refreshUserUI(String username) throws RemoteException {
        String now = dtf.format(LocalDateTime.now());

        for (Map<String, ILobbyObserver> room : sharedObservers.values()) {
            if (room.containsKey(username)) {
                try {
                    // Pushing to the Client's Observer
                    room.get(username).onFriendListUpdated();
                    System.out.println("[" + now + "] LOG: FRIEND LIST REFRESH PUSHED TO " + username);
                } catch (RemoteException e) {
                    // Dead connection
                }
            }
        }
    }

    // GET THE CURRENT FRIEND LIST
    public ArrayList<User> getFriendList(String username) throws RemoteException
    {
        User currentUser = globalUsers.get(username);
        ArrayList<User> liveFriends = new ArrayList<>();

        if (currentUser != null)
        {
            for (String friendName : currentUser.getFriends())
            {
                User liveData = globalUsers.get(friendName);
                if (liveData != null)
                {
                    liveFriends.add(liveData);
                }
                else
                {
                    // OFFLINE DUMMY FOR UI DISPLAY
                    User offlineFriend = new User(friendName, "");
                    offlineFriend.setStatus(0);
                    liveFriends.add(offlineFriend);
                }
            }
        }
        return liveFriends;
    }

    // REMOVE FRIEND FUNCTION
    public Boolean removeFriend(String user, String friend) throws RemoteException
    {
        User u = globalUsers.get(user);
        User f = globalUsers.get(friend);

        if (u != null) u.getFriends().remove(friend);
        if (f != null) f.getFriends().remove(user);

        // SAVE FRIEND LISTS ON THE SERVER FILE
        DataStore.saveUsers(globalUsers);

        // RMI PUSH: Tell both clients to refresh their friend list UI immediately
        refreshUserUI(user);
        refreshUserUI(friend);

        return true;
    }
}
