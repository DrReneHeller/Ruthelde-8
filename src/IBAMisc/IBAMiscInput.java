package IBAMisc;

import DE_Optimizer.CorrectionFactorEntry;
import Simulator.CalculationSetup.ScreeningMode;
import Simulator.Stopping.CompoundCalculationMode;
import Simulator.Stopping.StoppingCalculationMode;
import Simulator.Target.Projectile;
import Simulator.Target.Target;

public class IBAMiscInput {

    public String stoppingData;
    public Target target, foil;
    public Projectile projectile;

    public double alpha, theta;
    public int unitX, unitY;
    // X: 0 = eV/10^15 at/cm2, 1 = keV/nm
    // Y: 0 = keV, 1 = keV/nm, 2 = eV/10^15 at/cm2

    public double EMin, EMax;
    public StoppingCalculationMode stoppingPowerCalculationMode;
    public CompoundCalculationMode compoundCalculationMode;
    public ScreeningMode screeningMode;
    public CorrectionFactorEntry[] correctionFactors;

    public IBAMiscInput(){

        stoppingData = null;
        target = new Target();
        foil = null;
        stoppingPowerCalculationMode = StoppingCalculationMode.ZB;
        compoundCalculationMode = CompoundCalculationMode.BRAGG;
        screeningMode = ScreeningMode.ANDERSON;
        projectile = new Projectile();
        correctionFactors = null;

        alpha = 0.0d;
        theta = 170.0d;

        unitX = 0;
        unitY = 0;
        EMin = 10;
        EMax = 10000;
    }
}
