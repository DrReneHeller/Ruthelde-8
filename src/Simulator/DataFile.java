package Simulator;

import DE_Optimizer.DEParameter;
import GUI.WindowPositions;
import Simulator.CalculationSetup.CalculationSetup;
import Simulator.Detector.DetectorSetup;
import Simulator.Target.Target;
import java.io.Serializable;

public class DataFile implements Serializable {

    public Target target;
    public ExperimentalSetup experimentalSetup;
    public CalculationSetup calculationSetup;
    public DetectorSetup detectorSetup;
    public DEParameter deParameter;
    public double[] experimentalSpectrum;
    public OutputOptions outputOptions;
    public String spectrumName;
    public WindowPositions windowPositions;

    public DataFile(Target target, ExperimentalSetup experimentalSetup, CalculationSetup calculationSetup,
                    DetectorSetup detectorSetup, DEParameter deParameter, OutputOptions outputOptions,
                    double[] experimentalSpectrum, String spectrumName, WindowPositions windowPositions){
        this.target = target;
        this.experimentalSetup = experimentalSetup;
        this.calculationSetup = calculationSetup;
        this.detectorSetup = detectorSetup;
        this.deParameter = deParameter;
        this.outputOptions = outputOptions;
        this.experimentalSpectrum = experimentalSpectrum;
        this.spectrumName = spectrumName;
        this.windowPositions = windowPositions;
    }
}
