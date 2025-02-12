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

public class Ruthelde_Client_Test implements Runnable, Observer {

    final static boolean DEBUG = true;
    static ClientEngine clientEngine;
    static boolean error, connected, finished;
    static String input, output, requestType, server;;
    static int port;
    OptimizerInput optimizerInput;
    int counter = 0;

    public static void main(String[] args) {

        System.out.println("Ruthelde - Test Client for S-Optimisation");

        Ruthelde_Client_Test ruthelde_client = new Ruthelde_Client_Test();
        Thread t = new Thread(ruthelde_client);
        t.start();

        error = false;
        finished = false;
        server = "localhost";

        requestType = "OPTIMIZE";
        input       = "opt_input.json";
        output      = "opt_output.json";
        port        = 9090;

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
                optimizerInput = gson.fromJson(inputStr, OptimizerInput.class);
                clientEngine.requestOptimization(optimizerInput);

            } catch (Exception ex) {
                errorMsg("Could not send input to server.");
                ex.printStackTrace();
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


        if (arg.equals("DE-INFO")){

            IntermediateOptimizerOutput iop = clientEngine.getIOP();

            if (iop != null){
                //debugMsg("Received intermediate DE result: " + iop.generationCounter);
            }
        }

        if (arg.equals("DE-RESULT")){

            OptimizerOutput optimizerOutput = clientEngine.getOptimizerOutput();
            if (optimizerOutput != null) {
                //debugMsg("Received optimisation result.");
                //writeToFile(output, gson.toJson(optimizerOutput));

                System.out.print(counter + "\t");
                System.out.print(String.format("%.2f", optimizerOutput.fitness) + "\t");
                System.out.print(String.format("%.2f", optimizerOutput.charge) + "\t");
                System.out.print(String.format("%.3f", optimizerOutput.factor) + "\t");
                System.out.print(String.format("%.2f", optimizerOutput.offset) + "\t");
                System.out.print(String.format("%.3f", optimizerOutput.resolution) + "\t");
                System.out.print(String.format("%.3f", optimizerOutput.target.getLayerList().get(0).getArealDensity()) + "\t");

                System.out.print(String.format("%.3f", optimizerOutput.correctionFactors[0]) + "\t");
                System.out.print(String.format("%.3f", optimizerOutput.correctionFactors[1]) + "\t");

                System.out.println();

                counter++;

                //debugMsg("Sending next request.");
                clientEngine.requestOptimization(optimizerInput);
            }

            if (counter == 100) finished = true;
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
