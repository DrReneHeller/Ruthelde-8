package Simulator;

import java.util.LinkedList;

public class SimulationData {

    private LinkedList<IsotopeFitData> isotopeList;
    private double[] energy, simulatedSpectrum;
    private long simulationTime;
    private int numberOfChannels;

    public SimulationData(){

    }

    public void setIsotopeFitData(LinkedList<IsotopeFitData> isotopeList){
        this.isotopeList = isotopeList;
    }

    public LinkedList<IsotopeFitData> getIsotopeList() {
        return isotopeList;
    }

    public void setSimulatedSpectrum(double[] simulatedSpectrum){
        this.simulatedSpectrum = simulatedSpectrum;
    }

    public double[] getSimulatedSpectrum() {
        return simulatedSpectrum;
    }

    public void setEnergy (double[] energy){
        this.energy = energy;
    }

    public double[] getEnergy() {
        return energy;
    }

    public void setSimulationTime(long simulationTime){
        this.simulationTime = simulationTime;
    }

    public long getSimulationTime() {
        return simulationTime;
    }

    public void setNumberOfChannels(int numberOfChannels){
        this.numberOfChannels = numberOfChannels;
    }

    public int getNumberOfChannels() {
        return numberOfChannels;
    }

    public SimulationData getDeepCopy(){

        SimulationData simulationData = new SimulationData();

        LinkedList<IsotopeFitData> _isotopeList = new LinkedList<>();
        for (IsotopeFitData isotopeFitData : isotopeList){ _isotopeList.add(isotopeFitData.getDeepCopy()); }
        simulationData.setIsotopeFitData(_isotopeList);

        simulationData.setSimulationTime(simulationTime);
        simulationData.setNumberOfChannels(numberOfChannels);

        double[] _energy = new double[numberOfChannels];
        if (numberOfChannels >= 0) System.arraycopy(energy, 0, _energy, 0, numberOfChannels);
        simulationData.setEnergy(_energy);

        double[] _simulatedSpectrum = new double[numberOfChannels];
        if (numberOfChannels >= 0) System.arraycopy(simulatedSpectrum, 0, _simulatedSpectrum, 0, numberOfChannels);
        simulationData.setSimulatedSpectrum(_simulatedSpectrum);

        return simulationData;
    }
}
