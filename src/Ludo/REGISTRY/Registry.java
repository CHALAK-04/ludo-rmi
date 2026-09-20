package Ludo.REGISTRY;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.Scanner;

// THIS SCRIPT RUN THE REGISTRY ON WHICH WILL BE A PORT DEDICATED FOR LUDO GAME
public class Registry
{
    public static void main(String[] args)
    {
        // PORT DEDICATED FOR LUDO GAME
        int port = 2000;


        try
        {
            // STARTING THE RMI REGISTRY PROCESS
            LocateRegistry.createRegistry(port);



            System.out.println("----------------------------------");
            System.out.println("RMI REGISTRY STARTED ON PORT: "+port);
            System.out.println("PRESS ANY KEY TO STOP THE REGISTRY");
            System.out.println("----------------------------------");

            // KEEPING THE REGISTRY ON TILL A KEY IS PRESSED
            Scanner key = new Scanner(System.in);
            key.nextLine();
            System.out.println("-----------------------");
            System.out.println("REGISTRY IS NOW OFFLINE");
            System.out.println("-----------------------");
        }

        catch (RemoteException e)
        {
            System.err.println("REGISTRY ERROR: "+e.getMessage());
        }

    }
}
