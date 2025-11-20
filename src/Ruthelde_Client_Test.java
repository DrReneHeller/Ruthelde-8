import DE_Optimizer.IntermediateOptimizerOutput;
import DE_Optimizer.MultipleSpectra.MS_OptimizerInput;
import DE_Optimizer.OptimizerInput;
import DE_Optimizer.OptimizerOutput;
import Simulator.OutputOptions;
import Simulator.SimulatorInput;
import Simulator.SimulatorOutput;
import Simulator.Spectrum;
import Simulator.Target.Element;
import Simulator.Target.Layer;
import Uncertainty.StatisticsCalculator;
import Uncertainty.UncertaintyInput;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

public class Ruthelde_Client_Test implements Runnable, Observer {

    final static boolean DEBUG = false;
    static ClientEngine clientEngine;
    static boolean error, connected, finished;
    static String input, output, server;;
    static int port;
    SimulatorInput simulatorInput;
    OptimizerInput optimizerInput;
    int counter = 0;
    UncertaintyInput uncertaintyInput;
    double progress;
    LinkedList<StatisticsCalculator> statistics;
    final static int ITERATIONS = 100;
    boolean firstRun = true;

    public static void main(String[] args) {

        System.out.println("Ruthelde - Client for Uncertainty Determination");

        Ruthelde_Client_Test ruthelde_client = new Ruthelde_Client_Test();
        Thread t = new Thread(ruthelde_client);
        t.start();

        error = false;
        finished = false;
        server = "localhost";

        input       = "opt_input.json";
        output      = "uncertainty_output.txt";
        port        = 9090;

        while(!finished)
        {
            try {Thread.sleep(50);} catch (Exception ex){}
        }
    }

    @Override
    public void run() {

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

        //Send first request to server
        if (!error) {

            Gson gson =  new GsonBuilder().setPrettyPrinting().create();
            optimizerInput = gson.fromJson(inputStr, OptimizerInput.class);
            progress = 0.1d;

            uncertaintyInput = new UncertaintyInput();

            //TODO: Integrate into input file
            uncertaintyInput.E0_min    = 1520.0d ;
            uncertaintyInput.E0_max    = 1520.1d ;
            uncertaintyInput.alpha_min =  70.00d ;
            uncertaintyInput.alpha_max =  70.10d ;
            uncertaintyInput.theta_min = 100.00d ;
            uncertaintyInput.theta_max = 100.10d ;

            simulatorInput = new SimulatorInput();
            simulatorInput.experimentalSetup = optimizerInput.experimentalSetup.getDeepCopy() ;
            simulatorInput.calculationSetup  = optimizerInput.calculationSetup.getDeepCopy()  ;
            simulatorInput.detectorSetup     = optimizerInput.detectorSetup.getDeepCopy()     ;
            simulatorInput.target            = optimizerInput.target.getDeepCopy()            ;
            simulatorInput.outputOptions     = new OutputOptions()                            ;

            statistics = new LinkedList<>();

            StatisticsCalculator stat = new StatisticsCalculator();
            stat.name = "E0";
            stat.setValue = optimizerInput.experimentalSetup.getE0();
            statistics.add(stat);

            stat = new StatisticsCalculator();
            stat.name = "alpha";
            stat.setValue = optimizerInput.experimentalSetup.getAlpha();
            statistics.add(stat);

            stat = new StatisticsCalculator();
            stat.name = "theta";
            stat.setValue = optimizerInput.experimentalSetup.getTheta();
            statistics.add(stat);

            stat = new StatisticsCalculator();
            stat.name = "fitness";
            stat.setValue = 100.0d;
            statistics.add(stat);

            stat = new StatisticsCalculator();
            stat.name = "charge";
            stat.setValue = optimizerInput.experimentalSetup.getCharge();
            statistics.add(stat);

            stat = new StatisticsCalculator();
            stat.name = "detector_factor";
            stat.setValue = optimizerInput.detectorSetup.getCalibration().getFactor();
            statistics.add(stat);

            stat = new StatisticsCalculator();
            stat.name = "detector_offset";
            stat.setValue = optimizerInput.detectorSetup.getCalibration().getOffset();
            statistics.add(stat);

            stat = new StatisticsCalculator();
            stat.name = "detector_resolution";
            stat.setValue = optimizerInput.detectorSetup.getResolution();
            statistics.add(stat);

            int layerIndex = 1;
            for (Layer layer : optimizerInput.target.getLayerList()){

                stat = new StatisticsCalculator();
                stat.name = "AD layer " + layerIndex;
                stat.setValue = layer.getArealDensity();
                statistics.add(stat);

                for (Element element: layer.getElementList()){
                    stat = new StatisticsCalculator();
                    stat.name = element.getName() + " in layer " + layerIndex;
                    stat.setValue = element.getRatio();
                    statistics.add(stat);
                }

                layerIndex++;
            }

            StringBuilder sb = new StringBuilder();

            final DateTimeFormatter ZDT_FORMATTER = DateTimeFormatter.ofPattern("yyyy_MM_dd HH:mm:ss");
            sb.append(ZDT_FORMATTER.format(ZonedDateTime.now()));
            sb.append("\n");
            sb.append("\n");

            sb.append("E0 = ");
            sb.append(String.format("%.1f", optimizerInput.experimentalSetup.getE0()));
            sb.append(" [min = ");
            sb.append(String.format("%.1f", uncertaintyInput.E0_min));
            sb.append(" ... max = ");
            sb.append(String.format("%.1f", uncertaintyInput.E0_max));
            sb.append("]");
            sb.append("\n");

            sb.append("alpha = ");
            sb.append(String.format("%.1f", optimizerInput.experimentalSetup.getAlpha()));
            sb.append(" [min = ");
            sb.append(String.format("%.1f", uncertaintyInput.alpha_min));
            sb.append(" ... max = ");
            sb.append(String.format("%.1f", uncertaintyInput.alpha_max));
            sb.append("]");
            sb.append("\n");

            sb.append("theta = ");
            sb.append(String.format("%.1f", optimizerInput.experimentalSetup.getTheta()));
            sb.append(" [min = ");
            sb.append(String.format("%.1f", uncertaintyInput.theta_min));
            sb.append(" ... max = ");
            sb.append(String.format("%.1f", uncertaintyInput.theta_max));
            sb.append("]");
            sb.append("\n");

            sb.append("detector_factor = ");
            sb.append(String.format("%.1f", optimizerInput.detectorSetup.getCalibration().getFactor()));
            sb.append("\n");

            sb.append("detector_offset = ");
            sb.append(String.format("%.1f", optimizerInput.detectorSetup.getCalibration().getOffset()));
            sb.append("\n");

            sb.append("detector_resolution = ");
            sb.append(String.format("%.1f", optimizerInput.detectorSetup.getResolution()));
            sb.append("\n");

            sb.append("charge = ");
            sb.append(String.format("%.1f", optimizerInput.experimentalSetup.getCharge()));
            sb.append("\n");

            sb.append("Sim. steps = ");
            sb.append((int)optimizerInput.deParameter.endGeneration);
            sb.append("\n");

            sb.append("Sample size = ");
            sb.append(ITERATIONS);
            sb.append("\n");
            sb.append("\n");

            String temp = sb.toString().replace(",",".");
            sb = new StringBuilder();
            sb.append(temp);

            sb.append("Target: ");
            sb.append("\n");
            sb.append("\n");
            optimizerInput.target.getInfo(sb);

            writeToFile(output,sb.toString(), false);

            shuffleInputParameters();
            clientEngine.requestSimulation(simulatorInput);
        }

        //Wait until all pending requests have finished
        while(!finished && !error) {
            try {Thread.sleep(50);} catch (Exception ex){}
        }

        clientEngine.disconnect();
    }

    @Override
    public void update(Observable o, Object arg) {

        if (arg.equals("SIM-RESULT")){

            SimulatorOutput simulatorOutput = clientEngine.getSimulatorOutput();

            if (simulatorOutput != null) {
                debugMsg("Received simulation result.");
                System.out.println("-");

                //Generate experimental spectrum
                Spectrum simulatedSpectrum = simulatorOutput.spectra.getFirst();

                //if (firstRun) {
                    optimizerInput.experimentalSpectrum = makeExpSpectrum(simulatedSpectrum);
                //    firstRun = false;
                //}

                //Request optimization with original parameters
                clientEngine.requestOptimization(optimizerInput);
            }
        }

        if (arg.equals("DE-INFO")){

            IntermediateOptimizerOutput iop = clientEngine.getIOP();

            if (iop != null){
                if ((float) iop.generationCounter / (float) optimizerInput.deParameter.endGeneration > progress) {
                    System.out.print("-");
                    progress += 0.1d;
                }
            }
        }

        if (arg.equals("DE-RESULT")){

            System.out.print(" ");

            OptimizerOutput optimizerOutput = clientEngine.getOptimizerOutput();
            progress = 0.1d;

            if (optimizerOutput != null) {

                StringBuilder sb = new StringBuilder();
                int index = 0;

                sb.append(counter);
                sb.append("\t");

                sb.append(String.format("%.1f", simulatorInput.experimentalSetup.getE0()));
                sb.append("\t");
                statistics.get(index).add(simulatorInput.experimentalSetup.getE0());
                index++;

                sb.append(String.format("%.1f", simulatorInput.experimentalSetup.getAlpha()));
                sb.append("\t");
                statistics.get(index).add(simulatorInput.experimentalSetup.getAlpha());
                index++;

                sb.append(String.format("%.1f", simulatorInput.experimentalSetup.getTheta()));
                sb.append("\t");
                statistics.get(index).add(simulatorInput.experimentalSetup.getTheta());
                index++;

                sb.append(String.format("%.1f", optimizerOutput.fitness));
                sb.append("\t");
                statistics.get(index).add(optimizerOutput.fitness);
                index++;

                sb.append(String.format("%.2f", optimizerOutput.charge));
                sb.append("\t");
                statistics.get(index).add(optimizerOutput.charge);
                index++;

                sb.append(String.format("%.4f", optimizerOutput.factor));
                sb.append("\t");
                statistics.get(index).add(optimizerOutput.factor);
                index++;

                sb.append(String.format("%.1f", optimizerOutput.offset));
                sb.append("\t");
                statistics.get(index).add(optimizerOutput.offset);
                index++;

                sb.append(String.format("%.1f", optimizerOutput.resolution));
                statistics.get(index).add(optimizerOutput.resolution);
                index++;

                for (Layer layer: optimizerOutput.target.getLayerList()){

                    sb.append("\t");
                    sb.append(String.format("%.3f", layer.getArealDensity()));
                    statistics.get(index).add(layer.getArealDensity());
                    index++;

                    for (Element element : layer.getElementList()){
                        sb.append("\t");
                        sb.append(String.format("%.3f", element.getRatio()));
                        statistics.get(index).add(element.getRatio());
                        index++;
                    }
                }

                System.out.print(sb);
                System.out.print(" -");

                sb.append("\n");
                writeToFile(output,sb.toString(), true);

                counter++;
                if (counter == ITERATIONS) {

                    sb = new StringBuilder();
                    sb.append("\n");
                    System.out.println();

                    sb.append("Statistics");
                    sb.append("\n");
                    sb.append("\n");
                    sb.append("\tName -- Set Value -- Mean Value -- Std_Dev_Set_Abs -- Std_Dev_Set_Rel -- Std_Dev_Mean_Abs -- Std_Dev_Mean_Rel");
                    sb.append("\n");
                    sb.append("\n");

                    for (StatisticsCalculator sc : statistics) {
                        sb.append("\t");
                        sb.append(sc.getStatistics());
                        sb.append("\n");
                    }

                    //String statStr = sb.toString().replace(",",".");
                    String statStr = sb.toString();
                    System.out.println(statStr);

                    writeToFile(output,statStr, true);
                    finished = true;
                }

                if (!finished) {
                    shuffleInputParameters();
                    System.out.print("-");
                    clientEngine.requestSimulation(simulatorInput);
                }
            }
        }
    }

    private void writeToFile(String fileName, String content, Boolean append){

        if (!error) {

            debugMsg("Writing output file.");

            File file = new File(fileName);

            try {
                FileWriter fw = new FileWriter(file, append);
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

    private double[] makeExpSpectrum(Spectrum simulatedSpectrum) {

        int length = simulatedSpectrum.data.length;
        double[] expSpectrum = new double[length];

        //integrate simulated spectrum
        double integral = 0.0d;
        for (int i=0; i<length; i++){  integral += simulatedSpectrum.data[i]; }

        //normalize simulated spectrum integral to 1
        for (int i=0; i<length; i++){  expSpectrum[i] = simulatedSpectrum.data[i] / integral; }

        //generate width-array
        double[] w = new double[length];
        double sum = 0.0d;

        for (int i=0; i<length; i++){

            sum += expSpectrum[i];
            w[i] = sum;
        }

        //Fill artificial spectrum with events
        double[] artificialSpectrum = new double[length];

        for (long i=0; i<integral; i++){

            double r = Math.random();
            int index = 0;
            while (r > w[index]) index++;
            artificialSpectrum[index]++;
        }

        return artificialSpectrum;
    }

    private void shuffleInputParameters(){

        //Shuffle new input parameters
        double E0_min = uncertaintyInput.E0_min;
        double E0_max = uncertaintyInput.E0_max;
        double E0 = E0_min + Math.random() * (E0_max - E0_min);

        double alpha_min = uncertaintyInput.alpha_min;
        double alpha_max = uncertaintyInput.alpha_max;
        double alpha = alpha_min + Math.random() * (alpha_max - alpha_min);

        double theta_min = uncertaintyInput.theta_min;
        double theta_max = uncertaintyInput.theta_max;
        double theta = theta_min + Math.random() * (theta_max - theta_min);

        //Generate simulation input with shuffled input parameters
        simulatorInput.experimentalSetup.setE0(E0);
        simulatorInput.experimentalSetup.setAlpha(alpha);
        simulatorInput.experimentalSetup.setTheta(theta);
    }
}
