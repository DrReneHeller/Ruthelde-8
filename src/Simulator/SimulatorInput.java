package Simulator;

import DE_Optimizer.CorrectionFactorEntry;
import Simulator.CalculationSetup.CalculationSetup;
import Simulator.Detector.DetectorSetup;
import Simulator.Target.Target;

public class SimulatorInput {

    public Target target;
    public ExperimentalSetup experimentalSetup;
    public DetectorSetup detectorSetup;
    public CalculationSetup calculationSetup;
    public OutputOptions outputOptions;
    public CorrectionFactorEntry[] correctionFactors;

    public SimulatorInput(){

    }
}
