package DE_Optimizer;

import Simulator.Detector.DetectorCalibration;
import Simulator.Detector.DetectorSetup;
import Simulator.ExperimentalSetup;
import Simulator.Target.Target;

public class IntermediateOptimizerOutput {

    public int generationCounter;
    public String deStatusText;
    public double bestFitness, avrFitness;
    public boolean refreshPlot;
    public Target target;
    public double resolution, charge, E0;
    public DetectorCalibration detectorCalibration;
    public double[] correctionFactors;

    public IntermediateOptimizerOutput(){

    }

    public void print(){
        System.out.println("" + generationCounter + " " + bestFitness + " " + avrFitness + " " + refreshPlot + " " + resolution + " " + charge + " " + E0 + " " + detectorCalibration.getFactor() + "" + detectorCalibration.getOffset());
    }
}
