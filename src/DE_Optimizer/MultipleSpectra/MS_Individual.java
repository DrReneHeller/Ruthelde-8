package DE_Optimizer.MultipleSpectra;

import DE_Optimizer.Gene;
import DE_Optimizer.Measurement;
import Helper.Helper;
import Simulator.Detector.DetectorCalibration;
import Simulator.IBASpectrumSimulator;
import Simulator.SimulatorInput;
import Simulator.SimulatorOutput;
import Simulator.Target.Element;
import Simulator.Target.Layer;
import Simulator.Target.Target;
import mr.go.sgfilter.SGFilter;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class MS_Individual {

    private Target target;
    private final DetectorCalibration[] detectorCalibration;
    private double[] charge, resolution;
    private double fitness;
    private SimulatorOutput[] simulatorOutput;

    private MS_OptimizerInput input;

    public MS_Individual(MS_OptimizerInput input, double strength){

        this.input = input;

        Random rand = new Random();

        this.target = input.target.getDeepCopy();
        this.target.randomize(strength);

        int size = input.measurements.length;
        this.detectorCalibration = new DetectorCalibration[size];
        this.charge = new double[size];
        this.resolution = new double[size];

        for(int i = 0; i < size; i++){

            this.detectorCalibration[i] = input.measurements[i].detectorSetup.getCalibration().getDeepCopy();
            this.detectorCalibration[i].randomize(strength);

            double charge_min = input.measurements[i].experimentalSetup.getMinCharge();
            double charge_max = input.measurements[i].experimentalSetup.getMaxCharge();
            double q = input.measurements[i].experimentalSetup.getCharge() * (1.0d - strength/2.0d + rand.nextDouble()*strength);
            if (q > charge_max) q = charge_max;
            if (q < charge_min) q = charge_min;
            this.charge[i] = q;

            double res_min = input.measurements[i].detectorSetup.getMinRes();
            double res_max = input.measurements[i].detectorSetup.getMaxRes();
            double res = input.measurements[i].detectorSetup.getResolution() * (1.0d - strength/2.0d + rand.nextDouble()*strength);
            if (res > res_max) res = res_max;
            if (res < res_min) res = res_min;
            this.resolution[i] = res;
        }
    }

    public SimulatorOutput[] simulate(){

        simulatorOutput = new SimulatorOutput[input.measurements.length];
        fitness = 0.0d;
        int index = 0;

        for(Measurement measurement : input.measurements){

            SimulatorInput simulatorInput = new SimulatorInput();

            simulatorInput.target = target.getDeepCopy();

            simulatorInput.experimentalSetup = measurement.experimentalSetup.getDeepCopy();
            simulatorInput.experimentalSetup.setCharge(charge[index]);

            simulatorInput.detectorSetup = measurement.detectorSetup.getDeepCopy();
            simulatorInput.detectorSetup.setCalibration(detectorCalibration[index]);
            simulatorInput.detectorSetup.setResolution(resolution[index]);

            simulatorInput.calculationSetup = input.calculationSetup.getDeepCopy();

            simulatorInput.calculationSetup.numberOfChannels = input.measurements[index].spectrum.length;

            simulatorOutput[index] = IBASpectrumSimulator.simulate(simulatorInput);

            int startCh = measurement.deStartCh ;
            int endCh   = measurement.deEndCh   ;

            fitness += measurement.deWeight * calcFitness(simulatorOutput[index],
                    measurement.spectrum, detectorCalibration[index].getFactor(),
                    resolution[index], startCh, endCh);

            index++;
        }

        return simulatorOutput;
    }

    private double calcFitness(SimulatorOutput simulatorOutput, double[] expSpectrum, double resolution, double factor,
                               int startChannel, int stopChannel){

        double temp = resolution / factor;
        temp = Math.floor(Math.floor(temp/2.0f)) * 2.0f +1.0f;
        int filterLength = (int) temp;
        if (filterLength < 4) filterLength = 4;

        SGFilter sgFilter = new SGFilter(filterLength/2, filterLength/2);
        double[] coeff = SGFilter.computeSGCoefficients(filterLength/2, filterLength/2, 3);
        double[] smoothedSpectrum = sgFilter.smooth(expSpectrum, coeff);

        double LFF = 0.0f;
        for (int i=startChannel; i<stopChannel; i++){
            LFF += Math.pow(smoothedSpectrum[i] - expSpectrum[i],2);
        }

        double sigma2 = 0.0f;

        for (int i=startChannel; i<stopChannel; i++){
            sigma2 += Math.pow(expSpectrum[i]-simulatorOutput.spectra.get(0).data[i],2);
        }

        //sigma2 = 100.0f / (Math.log(sigma2) - Math.log(LFF));
        //sigma2 = LFF / sigma2 * 100.0f;
        sigma2 = Math.log(LFF) / Math.log(sigma2) * 100.0f;

        return sigma2;
    }

    public void setSimulatorOutput(SimulatorOutput[] simulatorOutput){
        int size = simulatorOutput.length;
        this.simulatorOutput = new SimulatorOutput[size];
        System.arraycopy(simulatorOutput, 0, this.simulatorOutput, 0, size);
    }

    public SimulatorOutput[] getSimulatorOutput(){return simulatorOutput;}

    public double getFitness(){
        return fitness;
    }

    public void setFitness(double fitness){
        this.fitness = fitness;
    }

    public void setTarget(Target target){
        this.target = target;
    }

    public void setCharge(double[] charge){

        int size = charge.length;
        this.charge = new double[size];
        System.arraycopy(charge, 0, this.charge, 0, size);
    }

    public double[] getCharge() {
        return charge;
    }

    public void setResolution(double[] resolution){

        int size = resolution.length;
        this.resolution = new double[size];
        System.arraycopy(resolution, 0, this.resolution, 0, size);
    }

    public double[] getResolution(){return resolution;}

    public void setCalibrationFactor(double calibrationFactor, int index){
        detectorCalibration[index].setFactor(calibrationFactor);
    }

    public double getCalibrationFactor(int index){
        return detectorCalibration[index].getFactor();
    }

    public void setCalibrationOffset(double calibrationOffset, int index){
        detectorCalibration[index].setOffset(calibrationOffset);
    }

    public double getCalibrationOffset(int index){
        return detectorCalibration[index].getOffset();
    }

    public void setDetectorCalibration(DetectorCalibration[] detectorCalibration){

        int size = detectorCalibration.length;

        for (int i=0; i<size; i++){
            this.detectorCalibration[i].setFactor(detectorCalibration[i].getFactor());
            this.detectorCalibration[i].setOffset(detectorCalibration[i].getOffset());
        }
    }

    public Target getTarget(){
        return target;
    }

    public LinkedList<Gene> getGenes(){

        LinkedList<Gene> genes = new LinkedList<>();
        double min, max, val;

        int size = input.measurements.length;

        for (int i = 0; i < size; i++) {

            //Charge
            min = input.measurements[i].experimentalSetup.getMinCharge();
            max = input.measurements[i].experimentalSetup.getMaxCharge();
            val = charge[i];
            genes.add(new Gene(min, max, val));

            //Calibration Factor
            min = input.measurements[i].detectorSetup.getCalibration().getFactorMin();
            max = input.measurements[i].detectorSetup.getCalibration().getFactorMax();
            val = detectorCalibration[i].getFactor();
            genes.add(new Gene(min, max, val));

            //Calibration Offset
            min = input.measurements[i].detectorSetup.getCalibration().getOffsetMin();
            max = input.measurements[i].detectorSetup.getCalibration().getOffsetMax();
            val = detectorCalibration[i].getOffset();
            genes.add(new Gene(min, max, val));

            //Detector Resolution
            min = input.measurements[i].detectorSetup.getMinRes();
            max = input.measurements[i].detectorSetup.getMaxRes();
            val = resolution[i];
            genes.add(new Gene(min, max, val));
        }

        //Target Model
        for (Layer layer : target.getLayerList()){

            min = layer.getMinAD();
            max = layer.getMaxAD();
            val = layer.getArealDensity();
            genes.add(new Gene(min,max,val));

            for (Element element : layer.getElementList()){

                min = element.getMin_ratio();
                max = element.getMax_ratio();
                val = element.getRatio();
                genes.add(new Gene(min,max,val));
            }
        }

        return genes;
    }

    public void setGenes(List<Gene> genes){

        int geneIndex = 0;

        int size = input.measurements.length;

        for (int i = 0; i < size; i++) {

            //Charge
            charge[i] = genes.get(geneIndex).val;
            geneIndex++;

            //Calibration Factor
            detectorCalibration[i].setFactor(genes.get(geneIndex).val);
            geneIndex++;

            //Calibration Offset
            detectorCalibration[i].setOffset(genes.get(geneIndex).val);
            geneIndex++;

            //Detector Resolution
            resolution[i] = genes.get(geneIndex).val;
            geneIndex++;
        }

        //Target Model
        for (Layer layer : target.getLayerList()){

            layer.setArealDensity(genes.get(geneIndex).val);
            geneIndex++;

            for (Element element : layer.getElementList()){

                element.setRatio(genes.get(geneIndex).val);
                geneIndex++;
            }
        }
    }

    //TODO: In case of performance issues or memory lacks this could be done in setSimulatorOutput()
    public void replace(MS_Individual individual){

        setGenes(individual.getGenes());
        setFitness(individual.getFitness());
        int size = individual.input.measurements.length;
        SimulatorOutput[] simulatorOutput = new SimulatorOutput[size];
        for (int i=0; i<size; i++)
        {
            simulatorOutput[i] = individual.getSimulatorOutput()[i].getDeepCopy();
        }
        setSimulatorOutput(simulatorOutput);
    }

    public MS_Individual getDeepCopy(){

        MS_Individual result = new MS_Individual(input, 1.0d);

        result.setTarget(target.getDeepCopy());
        result.setCharge(charge);
        result.setResolution(resolution);
        result.setDetectorCalibration(detectorCalibration);
        result.setFitness(fitness);

        return result;
    }

    public void getInfo(StringBuilder sb, double binningFactor){

        int size = input.measurements.length;

        for (int i=0; i<size; i++) {

            sb.append("Spectrum " + (i+1)).append("\r\n");
            sb.append("Charge \t = ").append(Helper.dblToDecStr(charge[i], 4)).append("\r\n");
            sb.append("Resolution \t = ").append(Helper.dblToDecStr(resolution[i], 4)).append("\r\n");
            sb.append("Cal.-Factor \t = ").append(Helper.dblToDecStr(detectorCalibration[i].getFactor() / binningFactor, 4)).append("\r\n");
            sb.append("Cal.-Offset \t = ").append(Helper.dblToDecStr(detectorCalibration[i].getOffset(), 4)).append("\r\n\n\r");
            sb.append("\r\n");
        }

        Target temp = target.getDeepCopy();
        temp.getInfo(sb);
    }

}

