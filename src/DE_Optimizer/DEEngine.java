package DE_Optimizer;

import Helper.Helper;
import Simulator.Target.Element;
import Simulator.Target.Layer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DEEngine {

    private OptimizerInput input;

    PrintWriter out;
    private Population population;
    private double bestFitness, averageFitness, averageTime;
    private long lastMillis, totalTime;
    private int generationCounter, fittestIndex, processors;
    private double[] originalSpectrum;
    private boolean stop;

    public DEEngine(OptimizerInput input, PrintWriter out){

        this.input = input;
        this.out = out;
        stop = false;
    }

    public void initialize(){

        processors = Runtime.getRuntime().availableProcessors();
        //System.out.println("Available processors: " + processors);


        int length = input.experimentalSpectrum.length;
        originalSpectrum = new double[length];
        System.arraycopy(input.experimentalSpectrum,0, originalSpectrum,0,length);

        if (input.deParameter.numBins > 1) reBin(input.deParameter.numBins);

        population = new Population(input);

        generationCounter = 0;
        totalTime = 0;
        lastMillis = System.currentTimeMillis();
        stop = false;
    }

    public void reset(){

        int numBins = input.deParameter.numBins;
        double[] experimentalSpectrum;
        int length = originalSpectrum.length;
        experimentalSpectrum = new double[length];
        System.arraycopy(originalSpectrum,0, experimentalSpectrum,0,length);

        input.detectorSetup.getCalibration().scaleFactorDown(numBins);

        for (Individual individual : population.getIndividualList()){
            individual.setCalibrationFactor(individual.getCalibrationFactor() / input.deParameter.numBins);
        }

        input.deParameter.startCH *= numBins;
        input.deParameter.endCH   *= numBins;

        input.experimentalSpectrum = experimentalSpectrum;

        stop = false;
    }

    public boolean evolve(){

        long currentMillis = System.currentTimeMillis();
        totalTime += currentMillis - lastMillis;
        lastMillis = currentMillis;
        boolean plotRefresh = false;

        double simTime = 0.0f           ;
        int    index   = 0              ;
        double F       = input.deParameter.F  ;
        double CR      = input.deParameter.CR ;

        LinkedList<Individual> children = new LinkedList<>();

        for (Individual parent : population.getIndividualList()) {

            int size = population.getIndividualList().size();

            int r1 = index;
            while (r1 == index) r1 = (int) ((Math.random() * (double) size));
            int r2 = r1;
            while (r2 == index || r2 == r1) r2 = (int) ((Math.random() * (double) size));
            int r3 = r2;
            while (r3 == index || r3 == r2 || r3 == r1) r3 = (int) ((Math.random() * (double) size));

            LinkedList<Gene> genes_r1 = population.getIndividualList().get(r1).getGenes();
            LinkedList<Gene> genes_r2 = population.getIndividualList().get(r2).getGenes();
            LinkedList<Gene> genes_r3 = population.getIndividualList().get(r3).getGenes();

            LinkedList<Gene> parentGenes = parent.getGenes();
            LinkedList<Gene> childGenes = new LinkedList<>();

            int geneIndex = 0;

            for (Gene gene : parentGenes) {

                double gene_r1 = genes_r1.get(geneIndex).val;
                double gene_r2 = genes_r2.get(geneIndex).val;
                double gene_r3 = genes_r3.get(geneIndex).val;

                double mutVal = gene_r1 + F * (gene_r2 - gene_r3);

                if (mutVal >= gene.max || mutVal <= gene.min) mutVal = gene.min + Math.random() * (gene.max - gene.min);

                if (Math.random() < CR) {
                    childGenes.add(new Gene(gene.min, gene.max, mutVal));
                } else {
                    childGenes.add(new Gene(gene.min, gene.max, gene.val));
                }
                geneIndex++;
            }

            Individual child = parent.getDeepCopy();

            child.setGenes(childGenes);

            //Normalize child
            int layerIndex = 0;
            Random rand = new Random();
            for (Layer childLayer : child.getTarget().getLayerList()) {

                Layer parentLayer = parent.getTarget().getLayerList().get(layerIndex);
                int elementIndex = 0;

                int numElements = parentLayer.getElementList().size();

                double[] s = new double[numElements];
                double[] c = new double[numElements];

                //Fill ratios
                for (Element childElement : childLayer.getElementList()) {

                    Element parentElement = parentLayer.getElementList().get(elementIndex);
                    s[elementIndex] = parentElement.getRatio();
                    c[elementIndex] = childElement.getRatio();
                    elementIndex++;
                }

                //Shuffle order
                int[] order = new int[numElements - 1];

                for (int i = 0; i < numElements - 1; i++) {
                    order[i] = i;
                }

                for (int i = order.length - 1; i > 0; i--) {

                    int k = rand.nextInt(i + 1);
                    int a = order[k];
                    order[k] = order[i];
                    order[i] = a;
                }

                //Do normalization
                for (int i : order) {

                    double diff = c[i] - s[i];

                    while (Math.abs(diff) > 0) {

                        int j = i;

                        while (i == j) {
                            j = rand.nextInt(numElements);
                        }

                        if ((s[j] - diff) < 0) {
                            diff = -s[j];
                            s[j] = 0;
                            c[j] = 0;

                        } else {
                            s[j] = s[j] - diff;
                            diff = 0;
                            s[i] = c[i];
                        }
                    }
                }

                //Copy data to child
                elementIndex = 0;
                for (Element element : childLayer.getElementList()) {
                    element.setRatio(s[elementIndex]);
                    elementIndex++;
                }

                layerIndex++;
            }

            //Add child to list for later simulation
            children.add(child);

            index++;
        }


        // Do all simulation work
        ExecutorService es = Executors.newFixedThreadPool(processors);
        List<Callable<Object>> simList = new ArrayList<>();
        for (Individual child : children) { simList.add(Executors.callable(new SimulationTask(child))); }
        try { es.invokeAll(simList, 20, TimeUnit.SECONDS); } catch (InterruptedException e) { e.printStackTrace(); }
        es.shutdownNow();
        es = null;
        simList = null;

        //Replace parents if necessary
        index = 0;
        for (Individual child : children) {

            simTime += child.getSimulatorOutput().simulationTime;
            double childFitness = child.getFitness();
            double parentFitness = population.getIndividualList().get(index).getFitness();

            if (childFitness >= parentFitness) {

                population.getIndividualList().get(index).replace(child);

                if (childFitness > bestFitness) {

                    bestFitness  = childFitness;
                    fittestIndex = index;
                    plotRefresh = true;
                }
            }

            index++;
        }

        averageTime = simTime / population.getIndividualList().size();
        averageFitness = population.getAverageFitness();

        //Check that we still have enough diversity if not replace part of the population by random individuals
        if (averageFitness/bestFitness > input.deParameter.THR){

            final double replaceFraction = 0.05d;

            int numRep = (int)Math.ceil((Math.random() * replaceFraction * (population.getIndividualList().size())));
            for (int i=0; i< numRep; i++) {
                int ii = (int) (Math.random() * (population.getIndividualList().size()));
                if (ii != fittestIndex) {
                    population.getIndividualList().set(ii, new Individual(input, 1.0d));
                }
            }
        }

        //Intermediate output via stream
        if (out != null) {

            Individual best = population.getIndividualList().get(fittestIndex);
            //TODO: Find out why normalization fails here
            //for (Layer layer : best.getTarget().getLayerList()) layer.normalizeElements();
            String deStatusText = getInfo(getClippingReport(best));

            IntermediateOptimizerOutput io = new IntermediateOptimizerOutput();
            io.generationCounter = generationCounter+1;
            io.avrFitness = averageFitness;
            io.bestFitness = bestFitness;
            io.deStatusText = deStatusText;
            io.detectorCalibration = best.getDetectorCalibration();
            io.charge = best.getCharge();
            io.resolution = best.getResolution();

            if (plotRefresh) {

                if (best.getCorrectionFactors() != null){

                    int numCF = best.getCorrectionFactors().length;

                    if (best.getCorrectionFactors().length > 0){
                        io.correctionFactors = new double[numCF];
                        for (int i=0; i<numCF; i++){
                            io.correctionFactors[i] = best.getCorrectionFactors()[i].correctionFactor;
                        }
                    } else {
                        io.correctionFactors = new double[0];
                    }
                } else io.correctionFactors = null;

                io.target = best.getTarget();
                io.refreshPlot = true;
            } else {
                io.target = null;
                io.refreshPlot = false;
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String outputStr = gson.toJson(io);

            try {
                //System.out.println("Gen. " + generationCounter);
                out.println("DE-INFO_" + outputStr);
                out.println("End_Of_Transmission");
                out.flush();
                try {Thread.sleep(20);} catch (Exception ex) {}
            } catch (Exception ex) {

                System.out.println("DE_Engine: Error transmitting intermediate results.");
            }
        }

        //check if isotope simulation needs to be turned on
        final float ms = 1000.0f;
        if (input.deParameter.isotopeTime   > 0 && totalTime  / ms > input.deParameter.isotopeTime) {
            input.calculationSetup.simulateIsotopes = true;
        }

        //check if goal reached
        if (input.deParameter.endTime       > 0 && totalTime  / ms   > input.deParameter.endTime      ) stop=true;
        if (input.deParameter.endFitness    > 0 && bestFitness       > input.deParameter.endFitness   ) stop=true;
        if (input.deParameter.endGeneration > 0 && generationCounter > input.deParameter.endGeneration-2) stop=true;

        generationCounter++;

        return stop;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public int getGenerationCount() {return generationCounter;}

    private String getInfo(String clippingReport){

        StringBuilder sb = new StringBuilder();
        final float ms = 1000.0f;

        sb.append("Generation \t = ").append(generationCounter+1).append("\n\r");
        sb.append("Avr. sim. time \t = ").append(Helper.dblToDecStr(averageTime, 2)).append(" ms\n\r");
        sb.append("Total time \t = ").append(Helper.dblToDecStr(totalTime / ms, 2)).append(" s\n\r\n\r");

        sb.append("DE-Para \t = ");
        sb.append("[N=").append(population.getIndividualList().size()).append(", ");
        sb.append("F=").append(Helper.dblToDecStr(input.deParameter.F, 2)).append(", ");
        sb.append("CR=").append(Helper.dblToDecStr(input.deParameter.CR, 2)).append(", ");
        sb.append("THR=").append(Helper.dblToDecStr(input.deParameter.THR, 2)).append("]\n\r");

        sb.append("Best fitness \t = ").append(Helper.dblToDecStr(bestFitness, 2)).append(" (No. ");
        sb.append(fittestIndex).append(")\n\r");
        sb.append("Avr. fitness \t = ").append(Helper.dblToDecStr(averageFitness, 2)).append(" (");
        sb.append(Helper.dblToDecStr(averageFitness / bestFitness * 100.0d, 1)).append("%)").append("\n\r\n\r");

        population.getIndividualList().get(fittestIndex).getInfo(sb, input.deParameter.numBins);

        sb.append("\n\r");
        sb.append(clippingReport);
        return sb.toString();
    }

    private String getClippingReport(Individual best){

        final double thr = 0.1d; // 10% above *min* or 10% below *max* waring will appear

        StringBuilder sb = new StringBuilder();
        int clipCount = 0;

        sb.append("Clipping warning for parameters: \n\r");

        double min   = input.experimentalSetup.getMinCharge();
        double max   = input.experimentalSetup.getMaxCharge();
        double value = best.getCharge();

        if (value >= (max - (thr*(max-min))) || value <= (min + (thr*(max-min)))) {
            sb.append("  Charge\n\r");
            clipCount++;
        }

        min   = input.detectorSetup.getMinRes();
        max   = input.detectorSetup.getMaxRes();
        value = best.getResolution();

        if (value >= (max - (thr*(max-min))) || value <= (min + (thr*(max-min)))) {
            sb.append("  Detector resolution\n\r");
            clipCount++;
        }

        min   = input.detectorSetup.getCalibration().getFactorMin();
        max   = input.detectorSetup.getCalibration().getFactorMax();
        value = best.getCalibrationFactor() * input.deParameter.numBins;

        if (value >= (max - (thr*(max-min))) || value <= (min + (thr*(max-min)))) {
            sb.append("  Calibration factor\n\r");
            clipCount++;
        }

        min   = input.detectorSetup.getCalibration().getOffsetMin();
        max   = input.detectorSetup.getCalibration().getOffsetMax();
        value = best.getCalibrationOffset();

        if (value >= (max - (thr*(max-min))) || value <= (min + (thr*(max-min)))) {
            sb.append("  Calibration offset\n\r");
            clipCount++;
        }

        int layerIndex = 1;

        for (Layer layer : best.getTarget().getLayerList()){

            min = layer.getMinAD();
            max = layer.getMaxAD();
            value  = layer.getArealDensity();

            if (value >= (max - (thr*(max-min))) || value <= (min + (thr*(max-min)))) {
                sb.append("  Layer " + layerIndex + " areal density \n\r");
                clipCount++;
            }

            if (layer.getElementList().size()>1) {

                for (Element element : layer.getElementList()) {

                    min = element.getMin_ratio();
                    max = element.getMax_ratio();
                    value = element.getRatio();

                    if (value >= (max - (thr*(max-min))) || value <= (min + (thr*(max-min)))) {
                        sb.append("  Layer " + layerIndex + " " + element.getName() + " ratio \n\r");
                        clipCount++;
                    }
                }
            }

            layerIndex++;
        }

        String result = "No parameters clipping.";

        if (clipCount > 0) {
            result = sb.toString();
        }

        return result;
    }

    public Individual getBest(){

        fittestIndex   = population.getBestFitnessIndex();
        return population.getIndividualList().get(fittestIndex).getDeepCopy();
    }

    private void reBin(int numBins){

        int length = input.experimentalSpectrum.length;
        originalSpectrum = new double[length];
        System.arraycopy(input.experimentalSpectrum,0, originalSpectrum,0,length);

        int newNumCh = length / numBins;

        input.calculationSetup.numberOfChannels = newNumCh;

        double[] newSpectrum = new double[newNumCh];

        for (int i=0; i<newNumCh; i++){

            double value = 0.0f;
            for (int j=0; j<numBins; j++){ value += input.experimentalSpectrum[numBins * i +j]; }
            newSpectrum[i] = value;
        }

        input.detectorSetup.getCalibration().scaleFactorUp(numBins);

        input.deParameter.startCH /= numBins;
        input.deParameter.endCH   /= numBins;

        input.experimentalSpectrum = newSpectrum;
    }

}
