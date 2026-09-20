package Ludo.SERVER;

import Ludo.COMMON.User;

import java.io.Serializable;
import java.util.ArrayList;

// SERVER-SIDE FILE RECORD.
// We do not send this object to clients. It exists only so the server can save passwords,
// friends, wins and losses, because User.password is transient for network safety.
class StoredUser implements Serializable
{
    private String username;
    private String password;
    private int wins;
    private int losses;
    private ArrayList<String> friends;
    private ArrayList<String> pendingFriendRequests;

    public StoredUser(User user)
    {
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.wins = user.getWins();
        this.losses = user.getLosses();
        this.friends = new ArrayList<>(user.getFriends());
        this.pendingFriendRequests = new ArrayList<>(user.getPendingFriendRequests());
    }

    public User toUser()
    {
        User user = new User(username, password);
        user.setWins(wins);
        user.setLosses(losses);
        user.getFriends().addAll(friends);
        if (pendingFriendRequests != null) {
            user.getPendingFriendRequests().addAll(pendingFriendRequests);
        }

        // On server restart, nobody is connected yet.
        user.setStatus(0);
        user.setCurrentLobbyID(-1);
        user.setSpectatableGame(false);

        return user;
    }
}
