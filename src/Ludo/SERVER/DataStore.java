package Ludo.SERVER;

import Ludo.COMMON.User;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// SIMPLE SERVER-SIDE FILE STORAGE.
// This follows the same course style: normal Java objects, Serializable,
// ObjectInputStream/ObjectOutputStream, and synchronized methods for shared data.
public class DataStore
{
    private static final String DATA_FOLDER = "server_data";
    private static final String USERS_FILE = DATA_FOLDER + File.separator + "users.dat";

    @SuppressWarnings("unchecked")
    public static synchronized void loadUsers(ConcurrentHashMap<String, User> users)
    {
        File file = new File(USERS_FILE);

        if (!file.exists())
        {
            System.out.println("LOG: No saved users file found. Starting with empty user list.");
            return;
        }

        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(file)))
        {
            Map<String, StoredUser> savedUsers = (Map<String, StoredUser>) in.readObject();

            users.clear();

            for (Map.Entry<String, StoredUser> entry : savedUsers.entrySet())
            {
                users.put(entry.getKey(), entry.getValue().toUser());
            }

            System.out.println("LOG: Loaded " + users.size() + " user(s) from file.");
        }
        catch (Exception e)
        {
            System.err.println("ERROR: Could not load users file: " + e.getMessage());
        }
    }

    public static synchronized void saveUsers(ConcurrentHashMap<String, User> users)
    {
        File folder = new File(DATA_FOLDER);

        if (!folder.exists())
        {
            folder.mkdirs();
        }

        Map<String, StoredUser> data = new HashMap<>();

        for (User user : users.values())
        {
            data.put(user.getUsername(), new StoredUser(user));
        }

        File file = new File(USERS_FILE);
        File tempFile = new File(DATA_FOLDER + File.separator + "users.tmp");

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(tempFile)))
        {
            out.writeObject(data);
        }
        catch (IOException e)
        {
            System.err.println("ERROR: Could not save users file: " + e.getMessage());
            return;
        }

        if (file.exists() && !file.delete())
        {
            System.err.println("ERROR: Could not replace old users file.");
            return;
        }

        if (!tempFile.renameTo(file))
        {
            System.err.println("ERROR: Could not rename temporary users file.");
        }
    }
}
