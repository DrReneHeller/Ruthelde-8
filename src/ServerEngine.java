
import DE_Optimizer.*;
import DE_Optimizer.MultipleSpectra.MS_OptimizerInput;
import DE_Optimizer.MultipleSpectra.MS_OptimizerOutput;
import Simulator.IBASpectrumSimulator;
import Simulator.SimulatorInput;
import Simulator.SimulatorOutput;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.PrintWriter;
import java.util.LinkedList;

public class ServerEngine {

    final static boolean DEBUG = false;

    private final static int PORT = 9090;

    public final SocketServerWorker ssw;

    private DEEngineWorker deEngineWorker;

    public ServerEngine(){

        ssw = new SocketServerWorker();
    }

    public void start(int port){

        debugMsg("Start listening.");

        //ssw.startListening(PORT);
        ssw.startListening(port);
        ssw.addPropertyChangeListener(evt -> {
            String name = evt.getPropertyName();
            if (name.equals("progress")) {
                processRequest();
            }
        });
        ssw.execute();
    }

    public void processRequest() {

        String msg = ssw.getData();

        while (msg != null) {

            int offset = msg.indexOf("_");

            if (offset != -1) {

                String identifier = msg.substring(0, offset);
                offset++;
                int length = msg.length();
                String coreMsg = msg.substring(offset, length);
                Gson gson = new Gson();
                String replyMsg = "";
                PrintWriter pw = ssw.getPrintWriter();
                String path;
                File folder = null;
                File[] listOfFiles = null;
                LinkedList<String> files = null;
                StringBuilder sb;

                switch (identifier) {

                    case "SIMULATE":
                        debugMsg("Received simulation request.");
                        SimulatorInput simulatorInput = gson.fromJson(coreMsg, SimulatorInput.class);
                        //TODO: Put this into a different threat

                        path = makeAbsolutePath(simulatorInput.calculationSetup.stoppingData, "StoppingData");
                        simulatorInput.calculationSetup.stoppingData = path;

                        if (simulatorInput.calculationSetup.crossSectionData != null){
                            int numEntries = simulatorInput.calculationSetup.crossSectionData.length;
                            for (int i=0; i<numEntries; i++){
                                path = makeAbsolutePath(simulatorInput.calculationSetup.crossSectionData[i], "CrossSectionData");
                                simulatorInput.calculationSetup.crossSectionData[i] = path;
                            }
                        }

                        SimulatorOutput simulatorOutput = IBASpectrumSimulator.simulate(simulatorInput);
                        gson = new GsonBuilder().setPrettyPrinting().create();
                        try {
                            replyMsg = "SIM-RESULT_" + gson.toJson(simulatorOutput);
                            ssw.send(replyMsg);
                        }
                        catch (Exception ex) {
                            debugMsg("Error in generation of replyMsg.");
                        }
                        break;

                    case "OPTIMIZE":
                        debugMsg("Received optimization request.");
                        OptimizerInput input = gson.fromJson(coreMsg, OptimizerInput.class);

                        path = makeAbsolutePath(input.calculationSetup.stoppingData, "StoppingData");
                        input.calculationSetup.stoppingData = path;

                        if (input.calculationSetup.crossSectionData != null){
                            int numEntries = input.calculationSetup.crossSectionData.length;
                            for (int i=0; i<numEntries; i++){
                                path = makeAbsolutePath(input.calculationSetup.crossSectionData[i], "CrossSectionData");
                                input.calculationSetup.crossSectionData[i] = path;
                            }
                        }

                        deEngineWorker = new DEEngineWorker(input, pw);

                        deEngineWorker.addPropertyChangeListener(evt -> {

                            String name = evt.getPropertyName();
                            if (name.equals("progress")) {

                                debugMsg("Sending DE results.");

                                Individual individual = deEngineWorker.getDeEngine().getBest();

                                OptimizerOutput output = new OptimizerOutput();

                                output.target     = individual.getTarget().getDeepCopy() ;
                                output.charge     = individual.getCharge()               ;
                                output.factor     = individual.getCalibrationFactor()    ;
                                output.offset     = individual.getCalibrationOffset()    ;
                                output.resolution = individual.getResolution()           ;
                                if (individual.getCorrectionFactors() != null){
                                    int numCF = individual.getCorrectionFactors().length;
                                    output.correctionFactors = new double[numCF];
                                    for (int i=0; i<numCF; i++){
                                        output.correctionFactors[i] = individual.getCorrectionFactors()[i].correctionFactor;
                                    }
                                }
                                output.fitness    = individual.getFitness()              ;
                                output.optimizationTime = deEngineWorker.getDeEngine().getTotalTime()/1000.0d;

                                Gson gson2 = new GsonBuilder().setPrettyPrinting().create();
                                String result = "DE-RESULT_" + gson2.toJson(output);
                                ssw.send(result);
                                try {Thread.sleep(20);} catch (Exception ex) {}
                            }
                        });

                        deEngineWorker.execute();

                        break;

                    case "STOP-OPTIMIZATION":

                        deEngineWorker.stop();
                        while (!deEngineWorker.isFinished()) try {
                            Thread.sleep(100);
                        } catch (Exception e) {
                        }
                        deEngineWorker.stop();

                        break;

                    case "OPTIMIZE-MS":
                        debugMsg("Received ms optimization request.");
                        MS_OptimizerInput ms_input = gson.fromJson(coreMsg, MS_OptimizerInput.class);

                        path = makeAbsolutePath(ms_input.calculationSetup.stoppingData, "StoppingData");
                        ms_input.calculationSetup.stoppingData = path;

                        if (ms_input.calculationSetup.crossSectionData != null){
                            int numEntries = ms_input.calculationSetup.crossSectionData.length;
                            for (int i=0; i<numEntries; i++){
                                path = makeAbsolutePath(ms_input.calculationSetup.crossSectionData[i], "CrossSectionData");
                                ms_input.calculationSetup.crossSectionData[i] = path;
                            }
                        }

                        //TODO: Put this into a different threat
                        //MS_OptimizerOutput ms_output = DEOptimizer.optimizeMS(ms_input, pw);
                        gson = new GsonBuilder().setPrettyPrinting().create();
                        //replyMsg = "OPT-MS-RESULT_" + gson.toJson(ms_output);
                        ssw.send(replyMsg);
                        break;

                    case "GET-STOPPING-FILE-LIST":

                        debugMsg("Received stopping file list request.");

                        folder = new File("StoppingData");
                        listOfFiles = folder.listFiles();

                        files = new LinkedList<>();

                        if(listOfFiles != null) {

                            for (int i = 0; i < listOfFiles.length; i++) {
                                if (listOfFiles[i].isFile()) {
                                    String fileName = listOfFiles[i].getName();
                                    files.add(fileName);
                                }
                            }
                        }

                        sb = new StringBuilder();

                        for (String name : files) {
                            sb.append(name);
                            sb.append("___");
                        }

                        replyMsg = "STOPPING-FILE-LIST_" + sb.substring(0,sb.toString().length()-3);
                        ssw.send(replyMsg);
                        break;


                    case "GET-CROSS-SECTION-FILE-LIST":

                        debugMsg("Received cross section file list request.");

                        folder = new File("CrossSectionData");
                        listOfFiles = folder.listFiles();

                        files = new LinkedList<>();

                        if(listOfFiles != null) {

                            for (int i = 0; i < listOfFiles.length; i++) {
                                if (listOfFiles[i].isFile()) {
                                    String fileName = listOfFiles[i].getName();
                                    files.add(fileName);
                                }
                            }
                        }

                        sb = new StringBuilder();

                        for (String name : files) {
                            sb.append(name);
                            sb.append("___");
                        }

                        replyMsg = "CROSS-SECTION-FILE-LIST_" + sb.substring(0,sb.toString().length()-3);
                        ssw.send(replyMsg);
                        break;

                    default:
                        break;
                }
            }

            msg = ssw.getData();
        }
    }

    private String makeAbsolutePath(String relativePath, String folderName){

        String path = null;

        if (relativePath != null && !relativePath.equals("")) {

            File base = new File(folderName);
            path = base.getAbsolutePath() + "/" + relativePath;
        }

        return path;
    }

    private void debugMsg(String msg){

        if (DEBUG){
            System.out.println(this.getClass().getName() + ": " + msg);
        }
    }

}
