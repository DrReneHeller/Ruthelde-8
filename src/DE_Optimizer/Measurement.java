package DE_Optimizer;

import Simulator.Detector.DetectorSetup;
import Simulator.ExperimentalSetup;
import Simulator.Spectrum;

public class Measurement {

    public double[] spectrum;
    public ExperimentalSetup experimentalSetup;
    public DetectorSetup detectorSetup;
    public double deWeight;
    public int deNumBins, deStartCh, deEndCh;
}
