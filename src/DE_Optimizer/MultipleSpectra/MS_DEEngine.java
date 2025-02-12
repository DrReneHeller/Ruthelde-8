package DE_Optimizer.MultipleSpectra;

import DE_Optimizer.*;
import Helper.Helper;
import Simulator.SimulatorOutput;
import Simulator.Target.Element;
import Simulator.Target.Layer;

import javax.swing.*;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MS_DEEngine {

    private MS_OptimizerInput input;

    PrintWriter out;
    private MS_Population population;
    private double bestFitness, averageFitness, averageTime;
    private long lastMillis, totalTime;
    private int generationCounter, fittestIndex, processors;
    private boolean stop;

    public MS_DEEngine(MS_OptimizerInput input, PrintWriter out){

        this.input = input;
        this.out = out;
        stop = false;
    }

    public void initialize(){

        processors = Runtime.getRuntime().availableProcessors();
        //System.out.println("Available processors: " + processors);
        inform("    Available cores: " + processors);
        inform("\n\r    ");

        reBin();

        population = new MS_Population(input);
        generationCounter = 0;
        totalTime = 0;
        lastMillis = System.currentTimeMillis();
        stop = false;
    }

    public void reset(){

        int index = 0;

        for (Measurement measurement: input.measurements){

            //int numBins = measurement.deNumBins;
            //measurement.detectorSetup.getCalibration().scaleFactorDown(numBins);

            for (MS_Individual individual : population.getIndividualList()){

                double factor = individual.getCalibrationFactor(index) / measurement.deNumBins;
                individual.setCalibrationFactor(factor, index);
            }

            index++;
        }

        stop = false;
    }

    public boolean evolve(){

        //inform("Starting calculation of new generation [" + generationCounter + "]\n\r");
        if (generationCounter < 10  ) inform("0");
        if (generationCounter < 100 ) inform("0");
        if (generationCounter < 1000) inform("0");
        inform(generationCounter + "");

        long currentMillis = System.currentTimeMillis();
        totalTime += currentMillis - lastMillis;
        lastMillis = currentMillis;

        //inform("  Implementing transitions ... \n\r");

        double simTime = 0.0f           ;
        int    index   = 0              ;
        double F       = input.deParameter.F  ;
        double CR      = input.deParameter.CR ;

        LinkedList<MS_Individual> children = new LinkedList<>();

        for (MS_Individual parent : population.getIndividualList()) {

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

            MS_Individual child = parent.getDeepCopy();

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
        for (MS_Individual child : children) { simList.add(Executors.callable(new MS_SimulationTask(child))); }
        try { es.invokeAll(simList, 20, TimeUnit.SECONDS); } catch (InterruptedException e) { e.printStackTrace(); }
        es.shutdownNow();
        es = null;
        simList = null;

        //Replace parents if necessary
        index = 0;
        for (MS_Individual child : children) {

            for (SimulatorOutput simulatorOutput : child.getSimulatorOutput()){
                simTime += simulatorOutput.simulationTime;
            }

            double childFitness = child.getFitness();
            double parentFitness = population.getIndividualList().get(index).getFitness();

            if (childFitness >= parentFitness) {

                population.getIndividualList().get(index).replace(child);

                if (childFitness > bestFitness) {
                    bestFitness  = childFitness;
                    fittestIndex = index;
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
                    population.getIndividualList().set(ii, new MS_Individual(input, 1.0d));
                }
            }
        }

        //inform("Done.\n\r");
        inform(".");

        //inform("  Updating outputs ... \n\r");

        //TODO: Put out through output stream if desired
        //fitnessPlotter.addDataEntry(bestFitness, averageFitness);

        //double bestCharge = population.getIndividualList().get(fittestIndex).getCharge();
        //double bestRes    = population.getIndividualList().get(fittestIndex).getResolution();
        //double bestA      = population.getIndividualList().get(fittestIndex).getCalibrationFactor();
        //bestA /= deParameter.numBins;
        //double bestB      = population.getIndividualList().get(fittestIndex).getCalibrationOffset();
        //Target bestTarget = population.getIndividualList().get(fittestIndex).getTarget().getDeepCopy();

        //for (Layer layer: bestTarget.getLayerList()) layer.normalizeElements();
        //FitParameterSet fitParameterSet = new FitParameterSet(generationCounter, bestFitness, bestCharge, bestRes, bestA, bestB, bestTarget);
        //String clippingReport = fitParameterSet.getClippingReport(spectrumSimulator.getExperimentalSetup(), spectrumSimulator.getDetectorSetup(), deParameter.numBins);

        //parameterPlotter.add(fitParameterSet);
        //if (generationCounter > 0) {
        //    parameterPlotWindow.setPlotSeries(parameterPlotter.makePlots());
        //    parameterPlotWindow.refresh();
        //}

        //if (plotRefresh) {

        //    SimulationData simData = population.getIndividualList().get(fittestIndex).getSimulatorOutput();
        //    Target target = population.getIndividualList().get(fittestIndex).getTarget();
        //    spectraPlotWindow.setPlotSeries(simulationResultPlotter.makePlots(simData, target));
        //    spectraPlotWindow.refresh();
        //}

        //getInfo(infoBox, clippingReport);

        //inform("Done.\n\r");
        inform(".");

        //inform("  Checking for stop condition(s) ... ");

        //check if isotope simulation needs to be turned on
        final float ms = 1000.0f;
        if (input.deParameter.isotopeTime   > 0 && totalTime  / ms > input.deParameter.isotopeTime) {
            input.calculationSetup.simulateIsotopes = true;
        }

        //check if goal reached
        if (input.deParameter.endTime       > 0 && totalTime  / ms   > input.deParameter.endTime      ) stop=true;
        if (input.deParameter.endFitness    > 0 && bestFitness       > input.deParameter.endFitness   ) stop=true;
        if (input.deParameter.endGeneration > 0 && generationCounter > input.deParameter.endGeneration-2) stop=true;

        //inform("Done.\n\r");
        inform(".");

        generationCounter++;

        //inform("Calculation of generation done. \n");
        if (generationCounter % 10 == 0) inform("\n\r    ");

        return stop;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public int getGenerationCount() {return generationCounter;}

    private void getInfo(JTextArea taOutput, String clippingReport){

        StringBuilder sb = new StringBuilder();
        final float ms = 1000.0f;

        sb.append("Generation \t = ").append(generationCounter).append("\n\r");
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

        taOutput.setText(sb.toString());
    }

    public MS_Individual getBest(){

        fittestIndex   = population.getBestFitnessIndex();
        return  population.getIndividualList().get(fittestIndex).getDeepCopy();
    }

    private void reBin(){

        for (Measurement measurement : input.measurements) {

            int length = measurement.spectrum.length;
            int numBins = measurement.deNumBins;
            int newNumCh = length / numBins;

            double[] newSpectrum = new double[newNumCh];

            for (int i = 0; i < newNumCh; i++) {

                double value = 0.0f;
                for (int j = 0; j < numBins; j++) {
                    value += measurement.spectrum[numBins * i + j];
                }
                newSpectrum[i] = value;
            }

            measurement.detectorSetup.getCalibration().scaleFactorUp(numBins);
            measurement.deStartCh /= numBins;
            measurement.deEndCh /= numBins;

            measurement.spectrum = newSpectrum;
        }
    }

    private void inform(String information){
        try {
            System.out.print(information);
            if (out != null) {
                if (information.contains("\n\r")) out.println("DE-INFO_LINE_BREAK");
                out.println("DE-INFO_" + information);
            }
        } catch (Exception ex){}
    }
}
