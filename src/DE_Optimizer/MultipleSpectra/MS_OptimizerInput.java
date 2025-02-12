package DE_Optimizer.MultipleSpectra;

import DE_Optimizer.DEParameter;
import DE_Optimizer.Measurement;
import Simulator.CalculationSetup.CalculationSetup;
import Simulator.Target.Target;

public class MS_OptimizerInput {

    public Target target;
    public Measurement[] measurements;
    public CalculationSetup calculationSetup;
    public DEParameter deParameter;
}
