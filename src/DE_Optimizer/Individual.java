package DE_Optimizer;

import Helper.Helper;
import Simulator.*;
import Simulator.Detector.DetectorCalibration;
import Simulator.Target.Element;
import Simulator.Target.Layer;
import Simulator.Target.Target;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class Individual {

    private Target target;
    private final DetectorCalibration detectorCalibration;
    private double charge, resolution, E0;
    private double fitness;
    private SimulatorOutput simulatorOutput;
    private CorrectionFactorEntry[] correctionFactors;

    private OptimizerInput input;

    public Individual(OptimizerInput input, double strength){

        this.input = input;

        Random rand = new Random();

        this.target = input.target.getDeepCopy();
        this.target.randomize(strength);

        this.detectorCalibration = input.detectorSetup.getCalibration().getDeepCopy();
        this.detectorCalibration.randomize(strength);

        double charge_min = input.experimentalSetup.getMinCharge();
        double charge_max = input.experimentalSetup.getMaxCharge();
        double q = input.experimentalSetup.getCharge() * (1.0d - strength/2.0d + rand.nextDouble()*strength);
        if (q > charge_max) q = charge_max;
        if (q < charge_min) q = charge_min;
        this.charge = q;
        this.E0 = input.experimentalSetup.getE0();

        double res_min = input.detectorSetup.getMinRes();
        double res_max = input.detectorSetup.getMaxRes();
        double res = input.detectorSetup.getResolution() * (1.0d - strength/2.0d + rand.nextDouble()*strength);
        if (res > res_max) res = res_max;
        if (res < res_min) res = res_min;
        this.resolution = res;

        double minE0 = input.experimentalSetup.getMinE0();
        double maxE0 = input.experimentalSetup.getMaxE0();
        if (minE0 != 0 && maxE0 != 0){
            if (minE0 < E0 && maxE0 > E0){

                double E0_ = input.experimentalSetup.getE0() * (1.0d - strength/2.0d + rand.nextDouble()*strength);
                if(E0_ > maxE0) E0_ = maxE0;
                if(E0_ < minE0) E0_ = minE0;
                this.E0 = E0_;
            }
        }

        if (input.calculationSetup.correctionFactors != null){

            int size = input.calculationSetup.correctionFactors.length;

            if (size > 0){

                this.correctionFactors = new CorrectionFactorEntry[size];

                for (int i=0; i<size; i++){
                    this.correctionFactors[i] = new CorrectionFactorEntry();
                    this.correctionFactors[i].Z = input.calculationSetup.correctionFactors[i].Z;
                    double cf_min = input.calculationSetup.correctionFactors[i].cF_min;
                    double cf_max = input.calculationSetup.correctionFactors[i].cF_max;
                    double cf_val = input.calculationSetup.correctionFactors[i].correctionFactor * (1.0d - strength/2.0d + rand.nextDouble()*strength);
                    if (cf_val > cf_max) cf_val = cf_max;
                    if (cf_val < cf_min) cf_val = cf_min;
                    this.correctionFactors[i].correctionFactor = cf_val;
                    this.correctionFactors[i].cF_min = cf_min;
                    this.correctionFactors[i].cF_max = cf_max;
                }
            } else this.correctionFactors = new CorrectionFactorEntry[0];
        } else this.correctionFactors = null;
    }

    public SimulatorOutput simulate(){

        SimulatorInput simulatorInput = new SimulatorInput();

        simulatorInput.target = target.getDeepCopy();

        simulatorInput.experimentalSetup = input.experimentalSetup.getDeepCopy();
        simulatorInput.experimentalSetup.setCharge(charge);
        simulatorInput.experimentalSetup.setE0(E0);

        simulatorInput.detectorSetup = input.detectorSetup.getDeepCopy();
        simulatorInput.detectorSetup.setCalibration(detectorCalibration);
        simulatorInput.detectorSetup.setResolution(resolution);

        simulatorInput.calculationSetup = input.calculationSetup;

        simulatorInput.outputOptions = new OutputOptions();

        simulatorInput.correctionFactors = correctionFactors;

        simulatorOutput = IBASpectrumSimulator.simulate(simulatorInput);

        int startChannel              = input.deParameter.startCH;
        int endChannel                = input.deParameter.endCH;
        double[] simulatedSpectrum    = simulatorOutput.spectra.get(0).data;
        double[] experimentalSpectrum = input.experimentalSpectrum;

        fitness = FitnessCalculator.calcFitness(simulatorInput.detectorSetup,  startChannel, endChannel, simulatedSpectrum, experimentalSpectrum);

        return simulatorOutput;
    }

    public void setSimulatorOutput(SimulatorOutput simulatorOutput){
        this.simulatorOutput = simulatorOutput;
    }

    public SimulatorOutput getSimulatorOutput(){return simulatorOutput;}

    public double getFitness(){
        return fitness;
    }

    public void setFitness(double fitness){
        this.fitness = fitness;
    }

    public void setTarget(Target target){
        this.target = target;
    }

    public void setCharge(double charge){
        this.charge = charge;
    }

    public double getCharge() {
        return charge;
    }

    public void setE0(double E0){
        this.E0 = E0;
    }

    public double getE0(){
        return E0;
    }

    public void setResolution(double resolution){this.resolution = resolution;}

    public double getResolution(){return resolution;}

    public DetectorCalibration getDetectorCalibration(){

        return this.detectorCalibration;
    }

    public void setCalibrationFactor(double calibrationFactor){
        detectorCalibration.setFactor(calibrationFactor);
    }

    public double getCalibrationFactor(){
        return detectorCalibration.getFactor();
    }

    public void setCalibrationOffset(double calibrationOffset){
        detectorCalibration.setOffset(calibrationOffset);
    }

    public double getCalibrationOffset(){
        return detectorCalibration.getOffset();
    }

    public void setDetectorCalibration(DetectorCalibration detectorCalibration){
        this.detectorCalibration.setFactor(detectorCalibration.getFactor());
        this.detectorCalibration.setOffset(detectorCalibration.getOffset());
    }

    public Target getTarget(){
        return target;
    }

    public CorrectionFactorEntry[] getCorrectionFactors() {
        return correctionFactors;
    }

    public void setCorrectionFactors(CorrectionFactorEntry[] correctionFactors) {
        this.correctionFactors = correctionFactors;
    }

    public LinkedList<Gene> getGenes(){

        LinkedList<Gene> genes = new LinkedList<>();
        double min, max, val;

        //Charge
        min = input.experimentalSetup.getMinCharge();
        max = input.experimentalSetup.getMaxCharge();
        val = charge;
        genes.add(new Gene(min,max,val));

        //Calibration Factor
        min = input.detectorSetup.getCalibration().getFactorMin();
        max = input.detectorSetup.getCalibration().getFactorMax();
        val = detectorCalibration.getFactor();
        genes.add(new Gene(min,max,val));

        //Calibration Offset
        min = input.detectorSetup.getCalibration().getOffsetMin();
        max = input.detectorSetup.getCalibration().getOffsetMax();
        val = detectorCalibration.getOffset();
        genes.add(new Gene(min,max,val));

        //Detector Resolution
        min = input.detectorSetup.getMinRes();
        max = input.detectorSetup.getMaxRes();
        val = resolution;
        genes.add(new Gene(min,max,val));

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

        //CorrectionFactors
        if (correctionFactors != null){
            for (CorrectionFactorEntry cfe : correctionFactors){
                min = cfe.cF_min;
                max = cfe.cF_max;
                val = cfe.correctionFactor;
                genes.add(new Gene(min,max,val));
            }
        }

        //Beam energy
        double minE0 = input.experimentalSetup.getMinE0();
        double maxE0 = input.experimentalSetup.getMaxE0();
        if (minE0 != 0 && maxE0 != 0){
            double E0_ = input.experimentalSetup.getE0();
            if (minE0 < E0_ && maxE0 > E0_){
                genes.add(new Gene(minE0, maxE0, E0));
            }
        }

        return genes;
    }

    public void setGenes(List<Gene> genes){

        int geneIndex = 0;

        //Charge
        charge = genes.get(geneIndex).val;
        geneIndex++;

        //Calibration Factor
        detectorCalibration.setFactor(genes.get(geneIndex).val);
        geneIndex++;

        //Calibration Offset
        detectorCalibration.setOffset(genes.get(geneIndex).val);
        geneIndex++;

        //Detector Resolution
        resolution = genes.get(geneIndex).val;
        geneIndex++;

        //Target Model
        for (Layer layer : target.getLayerList()){

            layer.setArealDensity(genes.get(geneIndex).val);
            geneIndex++;

            for (Element element : layer.getElementList()){

                element.setRatio(genes.get(geneIndex).val);
                geneIndex++;
            }
        }

        //Correction Factors
        if (correctionFactors != null){
            for (CorrectionFactorEntry cfe : correctionFactors){
                cfe.correctionFactor = genes.get(geneIndex).val;
                geneIndex++;
            }
        }

        //Beam energy
        double minE0 = input.experimentalSetup.getMinE0();
        double maxE0 = input.experimentalSetup.getMaxE0();
        if (minE0 != 0 && maxE0 != 0){
            E0 = genes.get(geneIndex).val;
        }
    }

    public void replace(Individual individual){

        setGenes(individual.getGenes());
        setFitness(individual.getFitness());
        setSimulatorOutput(individual.getSimulatorOutput().getDeepCopy());
    }

    public Individual getDeepCopy(){

        Individual result = new Individual(input, 1.0d);

        result.setTarget(target.getDeepCopy());
        result.setCharge(charge);
        result.setE0(E0);
        result.setResolution(resolution);
        result.setDetectorCalibration(detectorCalibration);
        result.setFitness(fitness);

        if (correctionFactors != null){

            int numCF = correctionFactors.length;
            CorrectionFactorEntry[] _correctionFactors = new CorrectionFactorEntry[numCF];

            if (numCF > 0){

                for (int i=0; i<numCF; i++){
                    _correctionFactors[i] = new CorrectionFactorEntry();
                    _correctionFactors[i].cF_min = this.correctionFactors[i].cF_min;
                    _correctionFactors[i].cF_max = this.correctionFactors[i].cF_max;
                    _correctionFactors[i].Z = this.correctionFactors[i].Z;
                    _correctionFactors[i].correctionFactor = this.correctionFactors[i].correctionFactor;
                }

                //System.arraycopy(this.correctionFactors, 0, _correctionFactors, 0, numCF);
            }

            result.setCorrectionFactors(_correctionFactors);
        } else {
            result.setCorrectionFactors(null);
        }


        return result;
    }

    public void getInfo(StringBuilder sb, double binningFactor){

        sb.append("Charge \t = ").append(Helper.dblToDecStr(charge, 4)).append("\r\n");
        sb.append("Resolution \t = ").append(Helper.dblToDecStr(resolution, 4)).append("\r\n");
        sb.append("Cal.-Factor \t = ").append(Helper.dblToDecStr(detectorCalibration.getFactor()/binningFactor, 4)).append("\r\n");
        sb.append("Cal.-Offset \t = ").append(Helper.dblToDecStr(detectorCalibration.getOffset(), 4)).append("\r\n\n\r");

        if (correctionFactors != null){
            if (correctionFactors.length > 0) {
                sb.append("Corr.-Factor(s) \t : ");
                for (CorrectionFactorEntry cfe : correctionFactors) {
                    sb.append("  [");
                    sb.append(cfe.Z);
                    sb.append("]-");
                    sb.append(Helper.dblToDecStr(cfe.correctionFactor, 4));
                }
            }
        }

        if (input.experimentalSetup.getMinE0() != 0 && input.experimentalSetup.getMaxE0() != 0){
            sb.append("E0 \t = ");
            sb.append(Helper.dblToDecStr(E0, 2));
        }

        sb.append("\r\n\n\r");

        Target temp = target.getDeepCopy();
        temp.getInfo(sb);
    }

}

