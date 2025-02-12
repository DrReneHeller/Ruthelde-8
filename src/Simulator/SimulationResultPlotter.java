package Simulator;

import Helper.Plot.PlotSeries;
import Simulator.Detector.DetectorCalibration;
import Simulator.Target.*;
import java.awt.*;
import java.util.LinkedList;

public abstract class SimulationResultPlotter {

    public static LinkedList<PlotSeries> makePlots(DetectorCalibration detectorCalibration, double[] experimentalSpectrum, LinkedList<Spectrum> simulatedSpectra){

        final float colOffset = 0.33f;

        int size = simulatedSpectra.get(0).data.length;
        double[] energy = new double[size];
        for (int i=0; i<size; i++) energy[i] = detectorCalibration.getFactor()*i + detectorCalibration.getOffset();

        LinkedList<PlotSeries> plotSeries = new LinkedList<>();


        PlotSeries ps_sim = new PlotSeries("Simulated"   , energy, simulatedSpectra.get(0).data);
        ps_sim.seriesProperties.color_red = 0;
        ps_sim.seriesProperties.color_green = 0;
        ps_sim.seriesProperties.color_blue = 255;
        ps_sim.seriesProperties.dashed = true;
        ps_sim.seriesProperties.stroke = 4;
        plotSeries.add(ps_sim);

        if (experimentalSpectrum != null) {

            PlotSeries ps_exp = new PlotSeries("Experimental", energy, experimentalSpectrum);
            ps_exp.seriesProperties.showLine = false;
            ps_exp.seriesProperties.showSymbols = true;
            ps_exp.seriesProperties.color_red = 255;
            ps_exp.seriesProperties.color_green = 0;
            ps_exp.seriesProperties.color_blue = 0;

            plotSeries.add(ps_exp);
        }

        int numSpectra = simulatedSpectra.size();
        int plotIndex = 0;

        for (int i = 1; i < numSpectra; i++) {

            String seriesName = simulatedSpectra.get(i).name;
            PlotSeries ps = new PlotSeries(seriesName, energy, simulatedSpectra.get(i).data);
            float h = (float) plotIndex / (float) numSpectra + colOffset;
            Color color = new Color(Color.HSBtoRGB(h, 1, 1));
            ps.setColor(color.getRed(), color.getGreen(), color.getBlue());
            ps.setStroke(2);
            plotSeries.add(ps);
            plotIndex++;
        }

        return plotSeries;
    }

    //TODO: Remove later (old version)
    public static LinkedList<PlotSeries> makePlots(SimulationData simulationData, Target target, double[] experimentalSpectrum, OutputOptions outputOptions){

        LinkedList<IsotopeFitData> isotopeList = simulationData.getIsotopeList();
        double[] simulatedSpectrum = simulationData.getSimulatedSpectrum();
        double[] energy = simulationData.getEnergy();
        int numberOfChannels = simulationData.getNumberOfChannels();

        boolean showIsotopes = outputOptions.showIsotopeContributions ;
        boolean showLayers   = outputOptions.showLayerContributions   ;
        boolean showElements = outputOptions.showElementContributions ;

        int numberOfLayers = target.getLayerList().size();
        int numberOfIsotopes = target.getNumberOfIsotopes();
        int numberOfElementsThroughAllLayers = target.getNumberOfElementsThroughAllLayers();
        final float colOffset = 0.33f;

        int numberOfElements = 0;
        int tempZ = 0;
        for (IsotopeFitData isotopeFitData : isotopeList) {
            if (isotopeFitData.Z != tempZ) {
                numberOfElements++;
                tempZ = isotopeFitData.Z;
            }
        }

        LinkedList<PlotSeries> plotSeries = new LinkedList<>();

        PlotSeries ps_sim = new PlotSeries("Simulated"   , energy, simulatedSpectrum   );
        ps_sim.seriesProperties.color_red = 0;
        ps_sim.seriesProperties.color_green = 0;
        ps_sim.seriesProperties.color_blue = 255;

        ps_sim.seriesProperties.dashed = true;
        ps_sim.seriesProperties.stroke = 4;
        plotSeries.add(ps_sim);

        PlotSeries ps_exp = new PlotSeries("Experimental", energy, experimentalSpectrum);
        ps_exp.seriesProperties.showLine = false;
        ps_exp.seriesProperties.showSymbols = true;
        ps_exp.seriesProperties.color_red = 255;
        ps_exp.seriesProperties.color_green = 0;
        ps_exp.seriesProperties.color_blue = 0;
        ps_sim.seriesProperties.color_red = 255;
        ps_sim.seriesProperties.color_green = 0;
        ps_sim.seriesProperties.color_blue = 0;
        plotSeries.add(ps_exp);

        int plotIndex, isotopeMass;
        Element element;
        double[][] data;

        if (showElements) {
            if (showLayers && !showIsotopes)
            {
                plotIndex = 0;
                data = new double[numberOfElementsThroughAllLayers][numberOfChannels];
                int layerIndex = 0;
                for (Layer layer : target.getLayerList()) {
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
                        PlotSeries ps = new PlotSeries(seriesName, energy, data[plotIndex]);
                        float h = (float) plotIndex / (float) numberOfLayers + colOffset;
                        //ps.setColor(new Color(Color.HSBtoRGB(h, 1, 1)));
                        Color color = new Color(Color.HSBtoRGB(h, 1, 1));
                        ps.setColor(color.getRed(), color.getGreen(), color.getBlue());
                        ps.setStroke(2);
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
                        PlotSeries ps = new PlotSeries(seriesName, energy, data[plotIndex]);
                        float h = (float) plotIndex / (float) isotopeList.size() + colOffset;
                        //ps.setColor(new Color(Color.HSBtoRGB(h, 1, 1)));
                        Color color = new Color(Color.HSBtoRGB(h, 1, 1));
                        ps.setColor(color.getRed(), color.getGreen(), color.getBlue());
                        ps.setStroke(2);
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
                PlotSeries ps = new PlotSeries(seriesName, energy, data[plotIndex]);
                float h = (float) plotIndex / (float) isotopeList.size() + colOffset;
                //ps.setColor(new Color(Color.HSBtoRGB(h, 1, 1)));
                Color color = new Color(Color.HSBtoRGB(h, 1, 1));
                ps.setColor(color.getRed(), color.getGreen(), color.getBlue());
                ps.setStroke(2);
                plotSeries.add(ps);
            }
        }

        if (showIsotopes) {
            if (showLayers) {
                plotIndex = 0;
                data = new double[numberOfIsotopes][numberOfChannels];
                int layerIndex = 0;
                for (Layer layer : target.getLayerList()) {
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
                            PlotSeries ps = new PlotSeries(seriesName, energy, data[plotIndex]);
                            float h = (float) plotIndex / (float) isotopeList.size() + colOffset;
                            //ps.setColor(new Color(Color.HSBtoRGB(h, 1, 1)));
                            Color color = new Color(Color.HSBtoRGB(h, 1, 1));
                            ps.setColor(color.getRed(), color.getGreen(), color.getBlue());
                            ps.setStroke(2);
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
                    PlotSeries ps = new PlotSeries(seriesName, energy, data[plotIndex]);
                    float h = (float) plotIndex / (float) isotopeList.size() + colOffset;
                    //ps.setColor(new Color(Color.HSBtoRGB(h, 1, 1)));
                    Color color = new Color(Color.HSBtoRGB(h, 1, 1));
                    ps.setColor(color.getRed(), color.getGreen(), color.getBlue());
                    ps.setStroke(2);
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
                PlotSeries ps = new PlotSeries(seriesName, energy, data[i]);
                float h = (float) i / (float) numberOfLayers + colOffset;
                //ps.setColor(new Color(Color.HSBtoRGB(h, 1, 1)));
                Color color = new Color(Color.HSBtoRGB(h, 1, 1));
                ps.setColor(color.getRed(), color.getGreen(), color.getBlue());
                ps.setStroke(2);
                plotSeries.add(ps);
            }
        }

        return plotSeries;
    }
}
