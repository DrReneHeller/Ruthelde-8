package DE_Optimizer;

import Simulator.CalculationSetup.CalculationSetup;
import Simulator.Detector.DetectorSetup;
import Simulator.ExperimentalSetup;
import Simulator.Spectrum;
import Simulator.Target.Target;

public class OptimizerInput {

    public Target target;
    public double[] experimentalSpectrum;
    public ExperimentalSetup experimentalSetup;
    public DetectorSetup detectorSetup;
    public CalculationSetup calculationSetup;
    public DEParameter deParameter;
}
