
import java.util.Scanner;

public class Ruthelde_Server implements Runnable {

    static ServerEngine serverEngine;

    static int port;

    public static void main(String[] args) {

        System.out.println("Ruthelde - Server V4.01");

        if (args.length == 0) port = 9090; else port = Integer.parseInt(args[0]);

        Ruthelde_Server ruthelde_server = new Ruthelde_Server();
        Thread t = new Thread(ruthelde_server);
        t.start();

        while(true)
        {
            try {Thread.sleep(50);} catch (Exception ex){}
        }
    }

    @Override
    public void run()
    {
        serverEngine = new ServerEngine();
        serverEngine.start(port);

        //Wait forever
        Scanner scanner = new Scanner(System.in);
        scanner.nextLine();
    }

}
