package Simulator;

import Simulator.Target.Element;
import Simulator.Target.Isotope;
import Simulator.Target.Layer;
import java.util.LinkedList;

public class SimulatorOutput {

    public LinkedList<Spectrum> spectra;

    public  long simulationTime;
    public SimulatorOutput(LinkedList<Spectrum> spectra, long simulationTime){
        this.spectra = spectra;
        this.simulationTime = simulationTime;
    }

    public static SimulatorOutput generate(SimulatorInput simulatorInput, SimulationData simulationData){

        LinkedList<IsotopeFitData> isotopeList = simulationData.getIsotopeList();
        double[] simulatedSpectrum = simulationData.getSimulatedSpectrum();
        int numberOfChannels = simulationData.getNumberOfChannels();

        boolean showIsotopes = simulatorInput.outputOptions.showIsotopeContributions ;
        boolean showLayers   = simulatorInput.outputOptions.showLayerContributions   ;
        boolean showElements = simulatorInput.outputOptions.showElementContributions ;

        int numberOfLayers = simulatorInput.target.getLayerList().size();
        int numberOfIsotopes = simulatorInput.target.getNumberOfIsotopes();
        int numberOfElementsThroughAllLayers = simulatorInput.target.getNumberOfElementsThroughAllLayers();

        int numberOfElements = 0;
        int tempZ = 0;
        for (IsotopeFitData isotopeFitData : isotopeList) {
            if (isotopeFitData.Z != tempZ) {
                numberOfElements++;
                tempZ = isotopeFitData.Z;
            }
        }

        LinkedList<Spectrum> plotSeries = new LinkedList<>();

        Spectrum ps_sim = new Spectrum("Simulated", simulatedSpectrum);
        plotSeries.add(ps_sim);

        int plotIndex, isotopeMass;
        Element element;
        double[][] data;

        if (showElements) {
            if (showLayers && !showIsotopes)
            {
                plotIndex = 0;
                data = new double[numberOfElementsThroughAllLayers][numberOfChannels];
                int layerIndex = 0;
                for (Layer layer : simulatorInput.target.getLayerList()) {
                    for (Element temp_element : layer.getElementList()) {

                        for (IsotopeFitData isotopeFitData : isotopeList) {
                            if (isotopeFitData.Z == temp_element.getAtomicNumber()) {
                                for (int i=0; i<numberOfChannels; i++)
                                {
                                    data[plotIndex][i] += isotopeFitData.spectra[layerIndex][i];
                                }
                            }
                        }

                        String seriesName = temp_element.getName() + "_Layer_" + (layerIndex+1);
                        Spectrum ps = new Spectrum(seriesName, data[plotIndex]);
                        plotSeries.add(ps);

                        plotIndex++;
                    }
                    layerIndex++;
                }
            } else {

                tempZ = isotopeList.get(0).Z;
                plotIndex = 0;
                data = new double[numberOfElements][numberOfChannels];
                element = new Element();

                for (IsotopeFitData isotopeFitData : isotopeList) {

                    if (isotopeFitData.Z != tempZ) {
                        element.setAtomicNumber(tempZ);

                        String seriesName = element.getName();
                        Spectrum ps = new Spectrum(seriesName, data[plotIndex]);
                        plotSeries.add(ps);

                        plotIndex++;
                        tempZ = isotopeFitData.Z;
                    }

                    for (int k=0; k<numberOfLayers; k++) {
                        for(int j=0; j<numberOfChannels; j++) {
                            data[plotIndex][j] += isotopeFitData.spectra[k][j];
                        }
                    }

                }

                element.setAtomicNumber(tempZ);

                String seriesName = element.getName();
                Spectrum ps = new Spectrum(seriesName, data[plotIndex]);
                plotSeries.add(ps);
            }
        }

        if (showIsotopes) {
            if (showLayers) {
                plotIndex = 0;
                data = new double[numberOfIsotopes][numberOfChannels];
                int layerIndex = 0;
                for (Layer layer : simulatorInput.target.getLayerList()) {
                    for (Element temp_element : layer.getElementList()) {
                        for (Isotope temp_isotope : temp_element.getIsotopeList()) {
                            for (IsotopeFitData isotopeFitData : isotopeList) {
                                if (temp_element.getAtomicNumber() == isotopeFitData.Z && temp_isotope.getMass() == isotopeFitData.M) {
                                    if (numberOfChannels >= 0)
                                        System.arraycopy(isotopeFitData.spectra[layerIndex], 0, data[plotIndex], 0, numberOfChannels);
                                }
                            }
                            isotopeMass = (int)Math.ceil(temp_isotope.getMass());

                            String seriesName = isotopeMass + temp_element.getName() + "_Layer_" + (layerIndex+1);
                            Spectrum ps = new Spectrum(seriesName, data[plotIndex]);
                            plotSeries.add(ps);
                            plotIndex++;
                        }
                    }
                    layerIndex++;
                }
            } else {
                data = new double[isotopeList.size()][numberOfChannels];
                plotIndex = 0;
                element = new Element();
                for (IsotopeFitData isotopeFitData : isotopeList) {
                    for (int k=0; k<numberOfLayers; k++) {
                        for (int j=0; j<numberOfChannels; j++) {
                            data[plotIndex][j] += isotopeFitData.spectra[k][j];
                        }
                    }
                    element.setAtomicNumber(isotopeFitData.Z);
                    isotopeMass = (int)Math.ceil(isotopeFitData.M);

                    String seriesName = isotopeMass + element.getName();
                    Spectrum ps = new Spectrum(seriesName, data[plotIndex]);
                    plotSeries.add(ps);
                    plotIndex++;
                }
            }
        }

        if (showLayers && !showElements && !showIsotopes) {
            data = new double[numberOfLayers][numberOfChannels];
            for (int i=0; i<numberOfLayers; i++) {
                for (IsotopeFitData isotopeFitData : isotopeList) {
                    for (int j=0; j<numberOfChannels; j++) {
                        data[i][j] += isotopeFitData.spectra[i][j];
                    }
                }

                String seriesName = "Layer " + (i+1);
                Spectrum ps = new Spectrum(seriesName, data[i]);
                plotSeries.add(ps);
            }
        }

        return new SimulatorOutput(plotSeries, simulationData.getSimulationTime());
    }

    public SimulatorOutput getDeepCopy(){

        LinkedList<Spectrum> new_spectra = new LinkedList<>();

        for (Spectrum spectrum : this.spectra){

            double[] data = new double[spectrum.data.length];
            System.arraycopy(spectrum.data, 0, data, 0, spectrum.data.length);
            Spectrum newSpectrum = new Spectrum(spectrum.name, data);
            new_spectra.add(newSpectrum);
        }

        SimulatorOutput simulatorOutput = new SimulatorOutput(spectra,simulationTime);

        return simulatorOutput;
    }
}
