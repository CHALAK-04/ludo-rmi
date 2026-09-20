package Ludo.COMMON;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

// AUTHENTICATION SERVICE INTERFACE RESPONSIBLE FOR SIGNUP/LOGIN/LOGOUT
public interface IAuthService extends Remote
{
    // SIGN-UP AND AUTO-LOGIN AFTER REGISTRATION AND RETURN THE USER OBJECT
    User register(String username, String password) throws RemoteException;

    // STANDARD LOGIN AND RETURN THE USER OBJECT
    User login(String username, String password) throws RemoteException;

    // LOGOUT AND RETURN TRUE IF LOGOUT WENT SUCCESSFULLY
    boolean logout(String username) throws RemoteException;

    // RETURNS TOP 3 PLAYERS SORTED BY WIN RATE
    ArrayList<User> getLeaderboard() throws RemoteException;
}
