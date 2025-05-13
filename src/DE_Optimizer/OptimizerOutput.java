package DE_Optimizer;

import Simulator.Target.Target;

public class OptimizerOutput {

    public Target target;
    public double charge, resolution, factor, offset, E0;
    public double[] correctionFactors;
    public double fitness;
    public double optimizationTime;

    public OptimizerOutput(){}
}
