
import DE_Optimizer.IntermediateOptimizerOutput;
import DE_Optimizer.MultipleSpectra.MS_OptimizerOutput;
import DE_Optimizer.OptimizerInput;
import DE_Optimizer.OptimizerOutput;
import Simulator.SimulatorInput;
import Simulator.SimulatorOutput;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Observable;

public class ClientEngine extends Observable {

    final static boolean DEBUG = false;
    private final static int PORT = 9090;
    private final SocketClientWorker scw;
    private SimulatorOutput simulatorOutput;
    private IntermediateOptimizerOutput io;
    private OptimizerOutput optimizerOutput;
    private MS_OptimizerOutput ms_optimizerOutput;
    private String[] stoppingFileList, crossSectionFileList;

    public ClientEngine(){

        scw = new SocketClientWorker();
    }

    public SimulatorOutput getSimulatorOutput(){return simulatorOutput;}
    public OptimizerOutput getOptimizerOutput(){return optimizerOutput;}
    public IntermediateOptimizerOutput getIOP(){return io;}
    public String[] getStoppingFileList(){return stoppingFileList;}
    public String[] getCrossSectionFileList(){return crossSectionFileList;}

    public void connect(String ip, int port){

        debugMsg("Connecting to server.");
        //scw.connect(ip, PORT);
        scw.connect(ip, port);
        if (scw.isConnected()) scw.addPropertyChangeListener(evt -> {

            String name = evt.getPropertyName();
            if (name.equals("progress")) {
                handleReply();
            }
        });
        scw.execute();
    }

    public void disconnect(){

        debugMsg("Disconnecting from server.");
        scw.disconnect();
        scw.execute();
    }

    public void requestSimulation(SimulatorInput simulatorInput){

        debugMsg("Requesting simulation.");
        String requestStr = "SIMULATE_";

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String inputStr = gson.toJson(simulatorInput);
        requestStr += inputStr;
        scw.send(requestStr);
    }

    public void requestOptimization(OptimizerInput optimizerInput){

        debugMsg("Requesting optimization.");
        String requestStr = "OPTIMIZE_";

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String inputStr = gson.toJson(optimizerInput);
        requestStr += inputStr;
        scw.send(requestStr);
    }

    public void stopOptimization(){

        debugMsg("Requesting optimization to stop.");
        String requestStr = "STOP-OPTIMIZATION_";
        scw.send(requestStr);
    }

    public void requestStoppingFileList(){

        debugMsg("Requesting stopping file list.");
        String requestStr = "GET-STOPPING-FILE-LIST_";
        scw.send(requestStr);
    }

    public void requestCrossSectionFileList(){

        debugMsg("Requesting cross section file list.");
        String requestStr = "GET-CROSS-SECTION-FILE-LIST_";
        scw.send(requestStr);
    }

    private String handleReply(){

        String type = null;

        String msg = scw.getData();

        while (msg != null) {

            int offset = msg.indexOf("_");

            if (offset != -1) {

                String identifier = msg.substring(0, offset);
                offset++;
                int length = msg.length();
                String coreMsg = msg.substring(offset, length);
                Gson gson = new Gson();

                switch (identifier) {

                    case "SIM-RESULT":
                        debugMsg("Received simulation result.");
                        try {
                            simulatorOutput = gson.fromJson(coreMsg, SimulatorOutput.class);
                            type = "SIM-RESULT";
                        } catch (Exception ex) {
                            errorMsg("Error parsing simulation result.");
                        }
                        break;

                    case "DE-INFO":
                        io = gson.fromJson(coreMsg, IntermediateOptimizerOutput.class);
                        debugMsg("Received DE info (" + io.generationCounter + ").");
                        type = "DE-INFO";
                        break;

                    case "DE-RESULT":
                        debugMsg("Received DE result.");
                        optimizerOutput = gson.fromJson(coreMsg, OptimizerOutput.class);
                        type = "DE-RESULT";
                        break;

                    case "STOPPING-FILE-LIST":
                       debugMsg("Received stopping file list.");
                        stoppingFileList = coreMsg.split("___");
                        type = "STOPPING-FILE-LIST";
                        break;

                    case "CROSS-SECTION-FILE-LIST":
                        debugMsg("Received cross section file list.");
                        crossSectionFileList = coreMsg.split("___");
                        type = "CROSS-SECTION-FILE-LIST";
                        break;

                    default:
                        break;
                }

                if (type != null) {
                    setChanged();
                    notifyObservers(type);
                }
            }

            msg = scw.getData();
        }

        return type;
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
