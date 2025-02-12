package Simulator.CalculationSetup;

import DE_Optimizer.CorrectionFactorEntry;
import Simulator.Stopping.*;

public class CalculationSetup {

    private static final StoppingCalculationMode DEFAULT_STOPPING_MODE            = StoppingCalculationMode.ZB    ;
    private static final CompoundCalculationMode DEFAULT_COMPOUND_CORRECTION_MODE = CompoundCalculationMode.BRAGG ;
    private static final StragglingMode          DEFAULT_STRAGGLING_MODE          = StragglingMode.CHU            ;
    private static final ScreeningMode           DEFAULT_SCREENING_MODE           = ScreeningMode.ANDERSON        ;
    private static final ChargeFractionMode      DEFAULT_CHARGE_FRACTION_MODE     = ChargeFractionMode.LINEAR     ;
    private static final boolean                 DEFAULT_USE_LOOK_UP_TABLE        = true                          ;
    private static final boolean                 DEFAULT_SIMULATE_ISOTOPES        = true                          ;
    private static final int                     DEFAULT_NUMBER_OF_CHANNELS       = 1024                          ;

    public StoppingCalculationMode stoppingPowerCalculationMode ;
    public String stoppingData                                  ;
    public String[] crossSectionData                            ;
    public CorrectionFactorEntry[] correctionFactors          ;
    public CompoundCalculationMode compoundCalculationMode      ;
    public ScreeningMode           screeningMode                ;
    public StragglingMode          stragglingMode               ;
    public ChargeFractionMode      chargeFractionMode           ;
    public int                     numberOfChannels             ;
    public boolean useLookUpTable, simulateIsotopes             ;

    public int ch_min, ch_max                                   ;

    public CalculationSetup() {

        this.stoppingPowerCalculationMode = DEFAULT_STOPPING_MODE            ;
        this.stoppingData                 = null                             ;
        this.correctionFactors            = new CorrectionFactorEntry[0]   ;
        this.compoundCalculationMode      = DEFAULT_COMPOUND_CORRECTION_MODE ;
        this.screeningMode                = DEFAULT_SCREENING_MODE           ;
        this.stragglingMode               = DEFAULT_STRAGGLING_MODE          ;
        this.chargeFractionMode           = DEFAULT_CHARGE_FRACTION_MODE     ;
        this.useLookUpTable               = DEFAULT_USE_LOOK_UP_TABLE        ;
        this.simulateIsotopes             = DEFAULT_SIMULATE_ISOTOPES        ;
        this.numberOfChannels             = DEFAULT_NUMBER_OF_CHANNELS       ;
        this.crossSectionData             = new String[0]                    ;
        this.ch_min = 0;
        this.ch_max = 1023;
    }

    public CalculationSetup getDeepCopy(){

        CalculationSetup calculationSetup = new CalculationSetup();

        calculationSetup.stoppingPowerCalculationMode = this.stoppingPowerCalculationMode ;
        calculationSetup.stoppingData                 = this.stoppingData                 ;
        calculationSetup.compoundCalculationMode      = this.compoundCalculationMode      ;
        calculationSetup.screeningMode                = this.screeningMode                ;
        calculationSetup.stragglingMode               = this.stragglingMode               ;
        calculationSetup.chargeFractionMode           = this.chargeFractionMode           ;
        calculationSetup.useLookUpTable               = this.useLookUpTable               ;
        calculationSetup.simulateIsotopes             = this.simulateIsotopes             ;
        calculationSetup.numberOfChannels             = this.numberOfChannels             ;

        if (correctionFactors != null){

            int size = correctionFactors.length;

            if (size > 0){

                calculationSetup.correctionFactors = new CorrectionFactorEntry[size];

                for (int i=0; i<size; i++){
                    calculationSetup.correctionFactors[i] = new CorrectionFactorEntry();
                    calculationSetup.correctionFactors[i].Z = this.correctionFactors[i].Z;
                    calculationSetup.correctionFactors[i].correctionFactor = this.correctionFactors[i].correctionFactor;
                    calculationSetup.correctionFactors[i].cF_min = this.correctionFactors[i].cF_min;
                    calculationSetup.correctionFactors[i].cF_max = this.correctionFactors[i].cF_max;
                }
            } else calculationSetup.correctionFactors = new CorrectionFactorEntry[0];
        } else calculationSetup.correctionFactors = null;

        if (this.crossSectionData != null)
        {
            calculationSetup.crossSectionData = new String[this.crossSectionData.length];
            System.arraycopy(this.crossSectionData, 0, calculationSetup.crossSectionData, 0, this.crossSectionData.length);
        } else {
            calculationSetup.crossSectionData = null;
        }

        return calculationSetup;
    }
}

