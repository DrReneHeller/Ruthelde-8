
import DE_Optimizer.IntermediateOptimizerOutput;
import DE_Optimizer.MultipleSpectra.MS_OptimizerInput;
import DE_Optimizer.OptimizerInput;
import DE_Optimizer.OptimizerOutput;
import Simulator.SimulatorInput;
import Simulator.SimulatorOutput;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Observable;
import java.util.Observer;

public class Ruthelde_Client implements Runnable, Observer {

    final static boolean DEBUG = true;
    static ClientEngine clientEngine;
    static boolean error, connected, finished;
    static String input, output, requestType, server;;
    static int port;

    public static void main(String[] args) {

        System.out.println("Ruthelde - Client V4.01");

        Ruthelde_Client ruthelde_client = new Ruthelde_Client();
        Thread t = new Thread(ruthelde_client);
        t.start();

        error = false;
        finished = false;
        server = "localhost";

        if (args.length == 0){

            //requestType = "SIMULATE";
            //input       = "sim_input.json";
            //output      = "sim_output.json";

            requestType = "OPTIMIZE";
            input       = "opt_input.json";
            output      = "opt_output.json";
            port        = 9090;

            //requestType = "OPTIMIZE_MS";
            //input       = "ms_opt_input.json";
            //output      = "ms_opt_output.json";

        } else if (args.length == 5) {

            requestType = args[0];
            input  = args[1];
            output = args[2];
            server = args[3];
            port   = Integer.parseInt(args[4]);

        } else {

            System.out.println("  Wrong number of arguments.");
            System.out.println("  Usage:");
            System.out.println("  requestType input.json output.json serverIP serverPort");
            System.out.println("  requestType can be SIMULATE, OPTIMIZE or OPTIMIZE_MS");

            requestType = null;
            input = null;
            output = null;
            error = true;
        }

        while(!finished)
        {
            try {Thread.sleep(50);} catch (Exception ex){}
        }
    }

    @Override
    public void run()
    {
        try {Thread.sleep(1000);} catch (Exception ex){}

        String inputStr = null;

        //read input file
        if(!error) {

            File file = new File(input);

            debugMsg("Loading input file.");

            if (file.exists()){

                try {
                    inputStr = new String(Files.readAllBytes(Paths.get(input)));
                } catch (Exception ex){
                    errorMsg("Could not read input file.");
                    error = true;
                }

            } else {
                errorMsg("Input file not found.");
                error = true;
            }
        }

        //Connect to server
        if (!error) {

            debugMsg("Connecting to server.");
            connected = false;

            try {
                clientEngine = new ClientEngine();
                clientEngine.addObserver(this);
                clientEngine.connect(server, port);
                connected = true;
            } catch (Exception ex) {
                errorMsg("Could not connect to server.");
                error = true;
            }
        }

        //Send request to server
        if (!error) {

            debugMsg("Sending input to server.");

            try {

                Gson gson =  new GsonBuilder().setPrettyPrinting().create();

                switch (requestType){

                    case "SIMULATE":
                        SimulatorInput simulatorInput = gson.fromJson(inputStr, SimulatorInput.class);
                        clientEngine.requestSimulation(simulatorInput);
                        break;
                    case "OPTIMIZE":
                        OptimizerInput optimizerInput = gson.fromJson(inputStr, OptimizerInput.class);
                        clientEngine.requestOptimization(optimizerInput);
                        break;
                    case "OPTIMIZE_MS":
                        MS_OptimizerInput ms_optimizerInput = gson.fromJson(inputStr, MS_OptimizerInput.class);
                        //TODO: Implement MS_Optimization
                        break;
                }

            } catch (Exception ex) {
                errorMsg("Could not send input to server.");
                error = true;
            }
        }


        //Wait until all pending requests have finished
        while(!finished && !error)
        {
            try {Thread.sleep(50);} catch (Exception ex){}
        }

        clientEngine.disconnect();
    }

    @Override
    public void update(Observable o, Object arg) {

        Gson gson =  new GsonBuilder().setPrettyPrinting().create();

        if (arg.equals("SIM-RESULT")){

            SimulatorOutput simulatorOutput = clientEngine.getSimulatorOutput();
            if (simulatorOutput != null) {
                debugMsg("Received simulation result.");
                writeToFile(output, gson.toJson(simulatorOutput));
            }
            finished = true;
        }

        if (arg.equals("DE-INFO")){

            IntermediateOptimizerOutput iop = clientEngine.getIOP();

            if (iop != null){
                debugMsg("Received intermediate DE result: " + iop.generationCounter);
            }
        }

        if (arg.equals("DE-RESULT")){

            OptimizerOutput optimizerOutput = clientEngine.getOptimizerOutput();
            if (optimizerOutput != null) {
                debugMsg("Received optimisation result.");
                writeToFile(output, gson.toJson(optimizerOutput));
            }
            finished = true;
        }

        if (arg.equals("FILE-LIST")){

            String[] fileNames = clientEngine.getStoppingFileList();
            if (fileNames != null) {
                debugMsg("Received file list.");
                int length = fileNames.length;
                fileNames[length - 1] = fileNames[length - 1].substring(0, fileNames[length - 1].length() - 2);
            }
        }
    }

    private void writeToFile(String fileName, String content){

        if (!error) {

            debugMsg("Writing output file.");

            File file = new File(fileName);

            try {
                FileWriter fw = new FileWriter(file);
                fw.write(content);
                fw.flush();
                fw.close();
            } catch (Exception ex) {
                errorMsg("Could not write file.");
            }
        }
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
