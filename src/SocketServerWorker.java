
import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketServerWorker extends SwingWorker<Object, Object> {

    final static boolean DEBUG = false;

    private ServerSocket server;
    private Socket socket;
    public final DataBuffer buffer;
    private int dummyCnt = 0;
    private boolean connected, error, reconnect;

    public SocketServerWorker(){

        connected = false            ;
        reconnect = false            ;
        error     = false            ;
        buffer    = new DataBuffer() ;
    }

    public void startListening(int port){

        error = false;

        try {
            server = new ServerSocket(port);
            connected = true;
        } catch (Exception ex) {
            debugMsg("Error opening socket.");
            error = true;
        }
    }

    public void stopListening() {

        try {
            server.close();
            connected = false;
        } catch (Exception ex) {
            debugMsg("Error closing socket.");
            error = true;
        }
    }

    public String getData(){
        return buffer.getData();
    }

    public String receive(){

        String result = null;

        if (!error) {

            try {

                if (socket == null || reconnect) {
                    debugMsg("Waiting for client connection.");
                    socket = server.accept();
                    debugMsg("Client connected.");
                    reconnect = false;
                }

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                debugMsg("Waiting for data from client.");
                String line = in.readLine();

                debugMsg("Received (first) line): " + line);

                if (line != null) {

                    debugMsg("Received data, waiting for End_Of_Transmission.");
                    StringBuilder sb = new StringBuilder();

                    while (!line.equals("End_Of_Transmission")) {

                        sb.append(line + "\n\r");
                        line = in.readLine();
                        debugMsg("Received line: " + line);
                    }

                    debugMsg("Received End_Of_Transmission message.");
                    result = sb.toString();

                } else {

                    debugMsg("Received data = null");
                    reconnect = true;
                }

            } catch (Exception ex) {

                debugMsg("Error receiving data.");
                reconnect = true;
            }
        }

        return result;
    }

    public void send(String data){

        if (!error) {

            try {
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println(data);
                out.println("End_Of_Transmission");

            } catch (Exception ex) {
                debugMsg("Error sending data to client.");
                error = true;
            }
        }
    }

    public PrintWriter getPrintWriter() {

        PrintWriter result = null;

        if (connected && !error) {

            try{
                result = new PrintWriter(socket.getOutputStream(), true);
            } catch (Exception ex){
                debugMsg("Error getting output stream.");
                error = true;
            }
        }

        return result;
    }

    public boolean isConnected() {return connected;}

    protected Object doInBackground() throws Exception {

        while(connected)
        {
            String data = receive();

            if (data != null){
                buffer.add(data);
                dummyCnt++;
                if (dummyCnt == 100) dummyCnt = 0;
                setProgress(dummyCnt);
            }

            Thread.sleep(5);
        }

        return null;
    }

    private void debugMsg(String msg){

        if (DEBUG){
            System.out.println(this.getClass().getName() + ": " + msg);
        }
    }
}
