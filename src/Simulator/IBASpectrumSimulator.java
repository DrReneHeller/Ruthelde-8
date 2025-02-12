package Simulator;

import Simulator.CalculationSetup.*;
import Simulator.Detector.DetectorCalibration;
import Simulator.Stopping.*;
import Simulator.Target.*;

import java.util.LinkedList;

public abstract class IBASpectrumSimulator {

    private final static double E_cutoff  = 50.0 ; //eV   //TODO: Implement into calculationSetup
    private final static int    NUM_STEPS = 50    ;       //TODO: Implement into calculationSetup

    //--------------------- Simulation -------------------------------------------------------------------------------//

    public static SimulatorOutput simulate(SimulatorInput input){

        SimulationData simulationData = getSimulationData(input);
        return SimulatorOutput.generate(input, simulationData);
    }

    public static SimulationData getSimulationData(SimulatorInput input){

        int numberOfLayers = input.target.getLayerList().size()      ;
        double[][] S       = new double[numberOfLayers][NUM_STEPS+2] ;
        double EMax        = input.experimentalSetup.getE0()         ;
        double EMin        = E_cutoff                                ;
        double dE          = (EMax - EMin) / NUM_STEPS               ;

        StoppingParaFile stoppingParaFile = null;

        if (input.calculationSetup.stoppingData != null && input.calculationSetup.stoppingData != ""){

            stoppingParaFile = StoppingParaFile.load(input.calculationSetup.stoppingData);
        }

        StoppingCalculator stoppingCalculator = new StoppingCalculator(stoppingParaFile, input.correctionFactors);
        preCalcStoppingValues(input, stoppingCalculator, S, EMin, dE);

        SimulationData simulationData = new SimulationData();
        simulationData.setNumberOfChannels(input.calculationSetup.numberOfChannels);
        LinkedList<IsotopeFitData> isotopeList;

        long millis = System.currentTimeMillis();

        //Build a list of all spectra (isotopes) we have to simulate
        if (input.calculationSetup.simulateIsotopes) {
            isotopeList = generateIsotopeList(input);
        } else {
            isotopeList = generateSimplifiedIsotopeList(input);
        }

        //Load in cross-section files if necessary
        if (input.calculationSetup.crossSectionData == null || input.calculationSetup.crossSectionData.length < 1) {
            KinematicsCalculator.clearCrossSectionData();
        }
        for (String path : input.calculationSetup.crossSectionData) KinematicsCalculator.addCrossSectionData(path);

        //Simulate all spectra
        int tempZ = 0;
        for (IsotopeFitData isotopeFitData : isotopeList) {

            if (isotopeFitData.Z != tempZ) {tempZ = isotopeFitData.Z;}

            try {
                simulateIsotopeSpectrum(input, stoppingCalculator, isotopeFitData, S, EMin, dE);
            } catch (Exception e){System.out.println("Simulation error: "); e.printStackTrace();}
        }

        //Generate sum spectrum (simulated spectrum)
        int numberOfChannels = input.calculationSetup.numberOfChannels;

        double[] simulatedSpectrum = new double[numberOfChannels];

        for (IsotopeFitData isotopeFitData : isotopeList) {
            for (int j=0; j<numberOfLayers; j++) {
                for (int k=0; k<numberOfChannels; k++) {
                    simulatedSpectrum[k] += isotopeFitData.spectra[j][k];
                }
            }
        }

        millis = System.currentTimeMillis() - millis;

        //Scale experimental spectrum according to detector calibration
        double[] energy = new double[numberOfChannels];
        DetectorCalibration detectorCalibration = input.detectorSetup.getCalibration();
        for (int j=0; j<numberOfChannels; j++) {
            energy[j] = detectorCalibration.getFactor()*j + detectorCalibration.getOffset();
        }

        simulationData.setEnergy(energy)                             ;
        simulationData.setIsotopeFitData(isotopeList)                ;
        simulationData.setSimulatedSpectrum(simulatedSpectrum)       ;
        simulationData.setSimulationTime(millis)                     ;

        return simulationData;
    }

    private static void simulateIsotopeSpectrum(SimulatorInput input,StoppingCalculator stoppingCalculator,
                                                IsotopeFitData isotopeFitData, double[][] S, double EMin,
                                                double dE) {

        int     layerIndex       = 0     ;
        boolean interfaceReached = false ;
        boolean stopSimulation   = false ;

        Layer layer;
        Projectile projectile;
        projectile = input.experimentalSetup.getProjectile();

        double E0    = input.experimentalSetup.getE0()     ;
        double theta = input.experimentalSetup.getTheta()  ;
        double alpha = input.experimentalSetup.getAlpha()  ;
        double beta  = input.experimentalSetup.getBeta()   ;
        double Q     = input.experimentalSetup.getCharge() ;

        double a     = input.detectorSetup.getCalibration().getFactor() ;
        double b     = input.detectorSetup.getCalibration().getOffset() ;
        double omega = input.detectorSetup.getSolidAngle()              ;

        int    Z2 = isotopeFitData.Z                          ;
        double M2 = isotopeFitData.M                          ;
        double c  = isotopeFitData.concentrations[layerIndex] ;

        double dx = 5.0; //Step width for ion penetration. If a layer is thinner it is recognized and handled separately.

        //Set initial parameter when the ion reaches the target's surface
        double E = E0;
        double str2_F = Math.pow(input.experimentalSetup.getDeltaE0() / 2.355, 2);

        //Calculate ion energy after scattering at the surface
        projectile.setE(E);
        double K = KinematicsCalculator.getBSKFactorA(projectile, M2, theta);
        double E_det = K * E;

        //Calculate energy and corresponding channel of the back scattered ion at the detector
        E_det          = E_det - calculateEnergyLossThroughFoil() ;
        double channel = Math.floor((E_det - b) / a)              ;
        double E_det_b = (a * channel + b)                        ;

        //Set start conditions for brick calculations
        layer                            = input.target.getLayerList().get(layerIndex)         ;
        double thicknessConversionFactor = layer.getThicknessConversionFactor() * 1000.0       ;
        double sumLayerThicknesses       = layer.getThickness()                                ;
        double S_brick_out               = 0.0                                                 ;
        double depth                     = 0.0                                                 ;
        double brickThicknesses[]        = new double[input.calculationSetup.numberOfChannels] ;
        int    brickLayerIndexes[]       = new    int[input.calculationSetup.numberOfChannels] ;
        int    brickIndex                = 0                                                   ;
        double targetThickness           = input.target.getTotalThickness()                    ;

        //Stepwise construct bricks and their contribution to the spectrum
        while (E_det > E_cutoff && depth < targetThickness && !stopSimulation && brickIndex < input.calculationSetup.numberOfChannels) {

            //Set start value for the brick
            double n = 0.0;
            double E_ion_front = E;

            //Calculate the brick's stopping cross-section for incoming path
            projectile.setE(E_ion_front);
            double S_brick_in = calculateStopping(stoppingCalculator, input, projectile, layer, layerIndex, EMin, dE, S);
            S_brick_in = S_brick_in / thicknessConversionFactor;

            double E_det_prev = E_det;

            //Find the brick's end
            while (E_det > E_det_b) {

                // Move one step (dx) deeper into the current brick
                n++;
                E = E_ion_front - n * S_brick_in * dx / Math.cos(Math.toRadians(alpha));
                projectile.setE(K*E);
                S_brick_out = calculateStopping(stoppingCalculator, input, projectile, layer, layerIndex, EMin, dE, S);
                S_brick_out = S_brick_out / thicknessConversionFactor;

                //Calculate energy of the back scattered ion at the front of the current brick
                E_det_prev = E_det; //For calculation of the exact brick thickness after stop condition is reached
                E_det = K * E - S_brick_out * n * dx / Math.cos(Math.toRadians(beta));

                //Calculate energy of the back scattered ion at the surface
                for (int j = brickIndex-1; j>=0; j--) {
                    projectile.setE(E_det);
                    Layer topLayer = input.target.getLayerList().get(brickLayerIndexes[j]);
                    double thicknessConversionFactor1 = topLayer.getThicknessConversionFactor() * 1000.0;
                    double Sj = calculateStopping(stoppingCalculator, input, projectile, topLayer, brickLayerIndexes[j], EMin, dE, S);
                    Sj = Sj / thicknessConversionFactor1;
                    E_det -= brickThicknesses[j] * Sj / Math.cos(Math.toRadians(beta));
                }

                //Calculate energy of the back scattered ion at the detector
                E_det = E_det - calculateEnergyLossThroughFoil();
            }

            //Calculate brick thickness
            double cf = (E_det -  E_det_b) / (E_det - E_det_prev);
            n -= cf;
            double brickThickness = n * dx;

            //Check if we have passed the interface to next layer
            double oldDepth = depth;
            depth += brickThickness;

            if (depth > sumLayerThicknesses && layerIndex < input.target.getLayerList().size()-1) {
                interfaceReached = true;
                brickThickness = sumLayerThicknesses - oldDepth;
                depth = sumLayerThicknesses;
            }

            brickThicknesses[brickIndex]  = brickThickness ;
            brickLayerIndexes[brickIndex] = layerIndex     ;

            //Calculate ion energy at the brick's back side (= next brick's front side)
            E = E_ion_front - S_brick_in * brickThickness / Math.cos(Math.toRadians(alpha));

            //Calculate brick's contribution to the spectrum
            double AD = brickThickness / thicknessConversionFactor * 1000.0;
            projectile.setE(E);
            ScreeningMode screeningMode = input.calculationSetup.screeningMode;
            double sigma = KinematicsCalculator.getBSCrossSection(projectile, Z2, M2, theta, screeningMode, 0);
            double Y_brick = 6.24E-3 * Q * AD * sigma * omega * c / Math.cos(Math.toRadians(alpha));

            if (channel < input.calculationSetup.numberOfChannels-1 && channel >= 0) {

                isotopeFitData.spectra[layerIndex][(int) channel] = Y_brick;

                //Calculate current brick's straggling contribution
                str2_F = calculateStraggling(input, stoppingCalculator, E_ion_front, E, projectile, str2_F, layer,
                        layerIndex, Z2, brickThickness, EMin, dE, S, thicknessConversionFactor, K, brickThicknesses,
                        brickIndex, brickLayerIndexes, isotopeFitData, channel);

            }

            //Set next brick's detector energies
            if (interfaceReached) {

                //Calculate energy of the back scattered ion at the front of the current brick
                E_det = K * E - S_brick_out * brickThickness / Math.cos(Math.toRadians(beta));

                //Calculate energy of the back scattered ion at the surface
                for (int j = brickIndex-1; j>=0; j--) {
                    projectile.setE(E_det);
                    Layer topLayer = input.target.getLayerList().get(brickLayerIndexes[j]);
                    double thicknessConversionFactor2 = topLayer.getThicknessConversionFactor() * 1000.0;
                    double Sj = calculateStopping(stoppingCalculator, input, projectile, topLayer, brickLayerIndexes[j], EMin, dE, S);
                    Sj = Sj / thicknessConversionFactor2;
                    E_det -= brickThicknesses[j] * Sj / Math.cos(Math.toRadians(beta));
                }

                //Calculate energy of the back scattered ion at the detector
                E_det = E_det - calculateEnergyLossThroughFoil();

                layerIndex++;
                layer = input.target.getLayerList().get(layerIndex);
                thicknessConversionFactor = layer.getThicknessConversionFactor() * 1000.0;
                sumLayerThicknesses += layer.getThickness();
                c = isotopeFitData.concentrations[layerIndex];
                interfaceReached = false;

                //Look if this was the last layer containing the current isotope
                stopSimulation = true;
                for (int l=layerIndex; l<isotopeFitData.concentrations.length; l++) {
                    if (isotopeFitData.concentrations[l] != 0.0) {
                        stopSimulation = false;
                        break;
                    }
                }

            } else {

                E_det = E_det_b;
                E_det_b = E_det_b - a;

                channel--;
            }

            brickIndex++;
        }

        //Make convolution of simulated spectrum with straggling and detector resolution
        if (channel < input.calculationSetup.numberOfChannels-1) convolveSpectrum(input, isotopeFitData, a, b, channel);

        //Reset the projectile's initial energy
        projectile.setE(E0);
    }

    private static double calculateEnergyLossThroughFoil() {

        //TODO: Implement proper energy loss through foil
        return 0.0;
    }

    private static double calculateStopping(StoppingCalculator stoppingCalculator, SimulatorInput input,
                                            Projectile projectile, Layer layer, int layerIndex, double EMin,
                                            double dE, double S[][]) {
        double result;

        StoppingCalculationMode sm = input.calculationSetup.stoppingPowerCalculationMode;
        CompoundCalculationMode cm = input.calculationSetup.compoundCalculationMode;

        if (input.calculationSetup.useLookUpTable) {
            double energyIndex = (projectile.getE()-EMin)/dE ;
            int    lowerIndex  = (int)energyIndex            ;
            int    upperIndex  = lowerIndex + 1              ;
            double E_low       = EMin + lowerIndex * dE      ;
            double E_high      = EMin + upperIndex * dE      ;
            double E           = projectile.getE()           ;
            double S_low       = S[layerIndex][lowerIndex]   ;
            double S_high      = S[layerIndex][upperIndex]   ;

            double S_inter = (S_high - S_low) * (E - E_low) / (E_high - E_low) + S_low;
            result = S_inter;
        } else {
            result= stoppingCalculator.getStoppingPower(projectile, layer, sm, cm, 2);
        }

        return result;
    }

    private static double calculateStraggling(SimulatorInput input, StoppingCalculator stoppingCalculator,
                                              double E_ion_front, double E, Projectile projectile, double str2_F,
                                              Layer layer, int layerIndex, int Z2, double brickThickness, double EMin,
                                              double dE, double S[][], double thicknessConversionFactor, double K,
                                              double brickThicknesses[], int brickIndex, int brickLayerIndexes[],
                                              IsotopeFitData isotopeFitData, double channel) {

        if (input.calculationSetup.stragglingMode != StragglingMode.NONE) {

            double Ef = E_ion_front;
            double Eb = E;

            projectile.setE(Ef);
            double Si = calculateStopping(stoppingCalculator, input, projectile, layer, layerIndex, EMin, dE, S);
            projectile.setE(Eb);
            double Sf = calculateStopping(stoppingCalculator, input, projectile, layer, layerIndex, EMin, dE, S);
            double str2_Bohr = 0.26 * Math.pow(projectile.getZ(), 2) * Z2 * brickThickness / thicknessConversionFactor;
            double str2_B = Math.pow(Sf / Si, 2) * str2_F + str2_Bohr;
            str2_F = str2_B;

            double str2_B_prime = K * K * str2_B;
            double Eb_prime = K * Eb;
            projectile.setE(Eb_prime);
            Si  = calculateStopping(stoppingCalculator, input, projectile, layer, layerIndex, EMin, dE, S);
            Si /= thicknessConversionFactor;

            double Ef_prime = Eb_prime - Si * brickThickness;
            projectile.setE(Ef_prime);
            Sf  = calculateStopping(stoppingCalculator, input, projectile, layer, layerIndex, EMin, dE, S);
            Sf /= thicknessConversionFactor;
            double str2_F_prime = Math.pow(Sf / Si, 2) * str2_B_prime + str2_Bohr;

            for (int j = brickIndex - 1; j >= 0; j--) {
                Eb_prime = Ef_prime;
                projectile.setE(Eb_prime);
                Layer topLayer1 = input.target.getLayerList().get(brickLayerIndexes[j]);
                double thicknessConversionFactor2 = topLayer1.getThicknessConversionFactor() * 1000.0;
                Si  = calculateStopping(stoppingCalculator, input, projectile, topLayer1, brickLayerIndexes[j], EMin, dE, S);
                Si /= thicknessConversionFactor2;
                Ef_prime = Eb_prime - Si * brickThicknesses[j];
                projectile.setE(Ef_prime);
                Sf  = calculateStopping(stoppingCalculator, input, projectile, topLayer1, brickLayerIndexes[j], EMin, dE, S);
                Sf /= thicknessConversionFactor2;
                str2_Bohr  = 0.26 * Math.pow(projectile.getZ(), 2) * Z2 * brickThicknesses[j];
                str2_Bohr /= thicknessConversionFactor2;
                str2_F_prime = Math.pow(Sf / Si, 2) * str2_F_prime + str2_Bohr;
            }

            isotopeFitData.straggling[(int) channel] = str2_F_prime;
        } else {
            isotopeFitData.straggling[(int) channel] = 0.0;
        }

        return str2_F;
    }

    private static void convolveSpectrum(SimulatorInput input, IsotopeFitData isotopeFitData, double a, double b,
                                         double channel) {

        int numberOfChannels         = input.calculationSetup.numberOfChannels ;
        double detRes                = input.detectorSetup.getResolution()     ;
        double str2_det              = Math.pow(detRes / 2.355,2)              ;
        int    layerIndex            = 0                                       ;
        int    sx                    = isotopeFitData.spectra.length           ;
        double convolutedSpectra[][] = new double[sx][numberOfChannels]        ;

        //Fill the none simulated part of the simulate isotope spectrum with last straggling value
        for (int i=(int)channel; i>=0; i--) {
            isotopeFitData.straggling[i] = isotopeFitData.straggling[(int)channel+1];
        }

        //Do convolution
        for (Layer layer : input.target.getLayerList()) {

            for (int ch=0; ch<numberOfChannels; ch++) {

                double str2_total = str2_det + isotopeFitData.straggling[ch]    ;
                double fact       = 1.0 / Math.sqrt(2.0*Math.PI*str2_total)     ;
                double Ei         = a * ch + b                                  ;
                double Si         = 0.0                                         ;

                for (int j=0; j<numberOfChannels; j++) {

                    double Nj = isotopeFitData.spectra[layerIndex][j];
                    double Sjj = 0.0;

                    if (Nj > 0) {
                        double Ej = a * j + b;
                        double argument = -0.5 * (Ei - Ej) * (Ei - Ej) / (str2_total);
                        if (argument > -5.0) { Sjj = Nj * Math.exp(argument); }
                    }

                    Si += Sjj;
                }
                convolutedSpectra[layerIndex][ch] = fact*Si*a;
            }
            layerIndex++;
        }

        //Replace original simulated spectrum with the convoluted one
        layerIndex = 0;
        for (Layer layer : input.target.getLayerList()) {
            for (int ch=0; ch<numberOfChannels; ch++) {
                isotopeFitData.spectra[layerIndex][ch] = convolutedSpectra[layerIndex][ch];
            }
            layerIndex++;
        }
    }
    private static void preCalcStoppingValues(SimulatorInput input, StoppingCalculator stoppingCalculator,
                                              double S[][], double EMin, double dE) {

        Projectile projectile = input.experimentalSetup.getProjectile() ;
        int        layerIndex = 0                                       ;
        double     EMax       = projectile.getE()                       ;

        StoppingCalculationMode sm = input.calculationSetup.stoppingPowerCalculationMode ;
        CompoundCalculationMode cm = input.calculationSetup.compoundCalculationMode      ;

        for (Layer layer : input.target.getLayerList()) {
            for (int i=0; i<NUM_STEPS+2; i++) {
                double E = EMin + i*dE;
                projectile.setE(E);
                S[layerIndex][i] = stoppingCalculator.getStoppingPower(projectile, layer, sm, cm, 2);
            }
            layerIndex++;
        }

        projectile.setE(EMax);
    }

    private static LinkedList<IsotopeFitData> generateIsotopeList(SimulatorInput input) {

        LinkedList<IsotopeFitData> isotopeList = new LinkedList<>();
        boolean addIt;
        int layerIndex = 0;

        for (Layer layer : input.target.getLayerList()) {
            for (Element element : layer.getElementList()) {
                int Z = element.getAtomicNumber();
                for (Isotope isotope : element.getIsotopeList()) {
                    double M = isotope.getMass();
                    double c = layer.getIsotopeContribution(Z, M);
                    addIt = true;
                    for (IsotopeFitData isotopeFitData : isotopeList) {
                        if (isotopeFitData.Z == Z && isotopeFitData.M == M) {
                            addIt = false;
                            isotopeFitData.concentrations[layerIndex] = c;
                            break;
                        }
                    }
                    if (addIt) {
                        double concentrations[] = new double[input.target.getLayerList().size()];
                        concentrations[layerIndex] = c;
                        isotopeList.add(new IsotopeFitData(Z,M,concentrations, input.calculationSetup.numberOfChannels));
                    }
                }
            }
            layerIndex++;
        }
        return isotopeList;
    }

    private static LinkedList<IsotopeFitData> generateSimplifiedIsotopeList(SimulatorInput input) {

        LinkedList<IsotopeFitData> isotopeList = new LinkedList<>();
        boolean addIt;
        int layerIndex = 0;

        for (Layer layer : input.target.getLayerList()) {
            for (Element element : layer.getElementList()) {
                int Z = element.getAtomicNumber();

                double M = element.getAverageMass();
                double c = layer.getElementContribution(Z);
                addIt = true;
                for (IsotopeFitData isotopeFitData : isotopeList) {
                    if (isotopeFitData.Z == Z) {
                        addIt = false;
                        isotopeFitData.concentrations[layerIndex] = c;
                        break;
                    }
                }
                if (addIt) {
                    double concentrations[] = new double[input.target.getLayerList().size()];
                    concentrations[layerIndex] = c;
                    isotopeList.add(new IsotopeFitData(Z,M,concentrations, input.calculationSetup.numberOfChannels));
                }
            }
            layerIndex++;
        }
        return isotopeList;
    }

    //TODO: Remove later
    public static SimulatorOutput generateOutput(SimulationData simulationData, Target target){

        LinkedList<IsotopeFitData> isotopeList = simulationData.getIsotopeList();
        double[] simulatedSpectrum = simulationData.getSimulatedSpectrum();
        //double[] energy = simulationData.getEnergy();
        int numberOfChannels = simulationData.getNumberOfChannels();
        int numberOfLayers = target.getLayerList().size();

        int numberOfElements = 0;
        int tempZ = 0;
        for (IsotopeFitData isotopeFitData : isotopeList) {
            if (isotopeFitData.Z != tempZ) {
                numberOfElements++;
                tempZ = isotopeFitData.Z;
            }
        }

        LinkedList<Spectrum> spectra = new LinkedList<>();

        Spectrum spectrum_sim = new Spectrum("Simulated", simulatedSpectrum);
        spectra.add(spectrum_sim);

        int spectrumIndex;
        Element element;
        double[][] data;

        tempZ = isotopeList.get(0).Z;
        spectrumIndex = 0;
        data = new double[numberOfElements][numberOfChannels];
        element = new Element();

        for (IsotopeFitData isotopeFitData : isotopeList) {

            if (isotopeFitData.Z != tempZ) {
                element.setAtomicNumber(tempZ);

                String spectrumName = element.getName();
                Spectrum spectrum = new Spectrum(spectrumName, data[spectrumIndex]);
                spectra.add(spectrum);

                spectrumIndex++;
                tempZ = isotopeFitData.Z;
            }

            for (int k = 0; k < numberOfLayers; k++) {
                for (int j = 0; j < numberOfChannels; j++) {
                    data[spectrumIndex][j] += isotopeFitData.spectra[k][j];
                }
            }
        }

        element.setAtomicNumber(tempZ);

        String spectrumName = element.getName();
        Spectrum spectrum = new Spectrum(spectrumName, data[spectrumIndex]);
        spectra.add(spectrum);

        return new SimulatorOutput(spectra, simulationData.getSimulationTime());
    }

}
