
import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class SocketClientWorker extends SwingWorker<Object, Object> {

    final static boolean DEBUG = false;
    private Socket socket;
    private final DataBuffer buffer;
    private int dummyCnt = 0;
    private boolean connected, error;

    public SocketClientWorker(){

        connected = false            ;
        error     = false            ;
        buffer    = new DataBuffer() ;
    }

    public void connect(String ip, int port){

        debugMsg("Connecting to server.");

        error = false;

        try {
            socket = new Socket(ip, port);
            connected = true;
        } catch (Exception ex) {
            errorMsg("Could not connect to server.");
            error = true;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {

        connected = false;
    }

    public String getData(){
        return buffer.getData();
    }

    private String receive(){

        String result = null;

        if (!error) {

            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line = in.readLine();
                StringBuilder sb = new StringBuilder();

                while (!line.equals("End_Of_Transmission")){

                    sb.append(line + "\n\r");
                    line = in.readLine();
                }

                result = sb.toString();

            } catch (Exception ex) {

                errorMsg("Could not receive answer from server.");
                ex.printStackTrace();
                error = true;
            }
        }

        return result;
    }

    public void send(String request){

        debugMsg("Sending request to server");

        if (!error) {

            try {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                out.println(request);
                out.println("End_Of_Transmission");

            } catch (Exception ex) {
                errorMsg("Could not send request to server.");
                error = true;
            }
        }
    }

    protected Object doInBackground() throws Exception {

        while(connected)
        {
            String msg = receive();
            buffer.add(msg);
            dummyCnt++;
            if (dummyCnt == 100) dummyCnt = 0;
            setProgress(dummyCnt);
            Thread.sleep(10);
        }

        debugMsg("Closing connection to server.");

        try {
            socket.close();
        } catch (Exception ex) {
            errorMsg("Could not close socket.");
            error = true;
        }

        return null;
    }

    private void debugMsg(String msg){

        if (DEBUG){
            System.out.println(this.getClass().getName() + ": " + msg);
        }
    }

    private void errorMsg(String msg){

        System.out.println(this.getClass().getName() + " - An error occurred: " + msg);
    }
}
