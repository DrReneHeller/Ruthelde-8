package Simulator;

import DE_Optimizer.CorrectionFactorEntry;
import Simulator.CalculationSetup.ScreeningMode;
import Simulator.Stopping.CompoundCalculationMode;
import Simulator.Stopping.StoppingCalculationMode;
import Simulator.Stopping.StoppingCalculator;
import Simulator.Stopping.StoppingParaFile;
import Simulator.Target.Layer;
import Simulator.Target.Projectile;
import Simulator.Target.Target;

import java.util.LinkedList;

public final class KinematicsCalculator {

    private static final StoppingCalculationMode DEFAULT_STOPPING_MODE = StoppingCalculationMode.ZB    ;
    private static final CompoundCalculationMode DEFAULT_COMPOUND_MODE = CompoundCalculationMode.BRAGG ;

    private static StoppingCalculationMode stoppingPowerCalculationMode ;
    private static CompoundCalculationMode compoundCalculationMode      ;

    public static LinkedList<CrossSectionData> crossSectionData = new LinkedList<>();

    public KinematicsCalculator() {
        setStoppingPowerCalculationMode(DEFAULT_STOPPING_MODE);
        setCompoundCalculationMode(DEFAULT_COMPOUND_MODE);
    }

    public static void addCrossSectionData(String fileName){

        CrossSectionData csde = new CrossSectionData();
        if (csde.loadFromFile(fileName)) crossSectionData.add(csde);
    }


    public static void clearCrossSectionData(){
        crossSectionData = new LinkedList<>();
    }

    public static void setStoppingPowerCalculationMode(StoppingCalculationMode _stoppingPowerCalculationMode) {
        if (_stoppingPowerCalculationMode != null) {
            stoppingPowerCalculationMode = _stoppingPowerCalculationMode;
        }
    }

    public static void setCompoundCalculationMode(CompoundCalculationMode _compoundCalculationMode) {
        if (_compoundCalculationMode != null) {
            compoundCalculationMode = _compoundCalculationMode;
        }
    }


    public static double getEnergyInDepth(Projectile projectile, Target target, double incidentAngle, double depth, StoppingParaFile stoppingParaFile, CorrectionFactorEntry[] correctionFactors) {

        StoppingCalculator stoppingCalculator = new StoppingCalculator(stoppingParaFile, correctionFactors);

        double E0               ;
        double remainingEnergy  ;
        double energyStep       ;
        double x, dx, dE, S     ;
        double pathLength       ;
        double projectedDepth   ;
        double layerThickness   ;

        E0                = projectile.getE() ;
        remainingEnergy   = E0                ;
        energyStep        = E0 / 1000.0d      ;
        pathLength        = 0.0d              ;
        projectedDepth    = depth / Math.abs(Math.cos(Math.toRadians(incidentAngle))) ;

        for (Layer layer: target.getLayerList()) {

            layerThickness = layer.getArealDensity();
            layerThickness = layerThickness / Math.abs(Math.cos(Math.toRadians(incidentAngle)));
            x = 0.0d;

            while (x<layerThickness && remainingEnergy>0.0d && pathLength<projectedDepth) {
                projectile.setE(remainingEnergy);
                S = stoppingCalculator.getStoppingPower(projectile, layer, stoppingPowerCalculationMode, compoundCalculationMode, 2) / 1000.0d;

                if (energyStep <= remainingEnergy) {
                    dx = energyStep / S;
                    dE = energyStep;
                } else {
                    dx = remainingEnergy / S;
                    dE = remainingEnergy;
                }

                if (x+dx <= layerThickness) {
                    x += dx;
                    remainingEnergy -= dE;
                } else {
                    remainingEnergy -= S*(layerThickness - x);
                    x  = layerThickness;
                    dx = layerThickness - x;
                }

                pathLength += dx * layer.getThicknessConversionFactor();
            }
        }

        projectile.setE(E0);
        return remainingEnergy;
    }

    public static double getEnergyAtSurface(Projectile projectile, Target target, double alpha, double theta, double depth, StoppingParaFile stoppingParaFile, CorrectionFactorEntry[] correctionFactors) {

        double E1_prime = 0.0d;
        double E1 = projectile.getE();
        double beta;

        if (E1 > 0.0d) {

            int layerIndex = 0;
            double sumThickness = 0.0d;

            //Get layer index in "depth"
            for (Layer layer: target.getLayerList()) {
                sumThickness += layer.getThickness();
                if (sumThickness < depth) {
                    layerIndex++;
                } else {
                    break;
                }
            }

            Target inverseTarget = new Target();

            //Make target and inverse target having same number of layers
            for (int i=layerIndex; i>=1; i--) {
                inverseTarget.addLayer();
            }

            //Build inverse target from original one
            for (int i=0; i<=layerIndex; i++) {
                inverseTarget.getLayerList().set(i, target.getLayerList().get(layerIndex - i));
            }

            //Adjust thickness of inverse target'S first layer
            double thickness = inverseTarget.getLayerList().get(0).getThickness();
            double newThickness = thickness - sumThickness + depth;
            inverseTarget.getLayerList().get(0).setThickness(newThickness);

            beta     = Math.abs(180.0d - alpha - theta);
            E1_prime = getEnergyInDepth(projectile, inverseTarget, beta, depth, stoppingParaFile, correctionFactors);
            inverseTarget.getLayerList().get(0).setThickness(thickness);
        }

        projectile.setE(E1);
        return E1_prime;
    }


    public static double getBSEnergyA(Projectile projectile, double M2, double theta) {

        return projectile.getE() * getBSKFactorA(projectile, M2, theta);
    }

    public static double getBSKFactorA(Projectile projectile, double M2, double theta) {

        double M1 = projectile.getM() ;
        double E1                     ;

        theta = Math.toRadians(theta) ;

        E1  = (M2/M1) * (M2/M1)                 ;
        E1 -= Math.sin(theta) * Math.sin(theta) ;
        E1  = Math.sqrt(E1)                     ;
        E1  = Math.cos(theta) + E1              ;
        E1 *= E1                                ;
        E1 *= M1 * M1                           ;
        E1 /= (M1+M2)*(M1+M2)                   ;

        return E1;
    }

    public static double getBSEnergyB(Projectile projectile, double M2, double theta) {

        double E0 = projectile.getE() ;
        double M1 = projectile.getM() ;
        double E1                     ;

        theta = Math.toRadians(theta) ;

        E1  = (M2/M1) * (M2/M1)                 ;
        E1 -= Math.sin(theta) * Math.sin(theta) ;
        E1  = Math.sqrt(E1)                     ;
        E1  = Math.cos(theta) - E1              ;
        E1 *= E1                                ;
        E1 *= E0 * M1 * M1                      ;
        E1 /= (M1+M2)*(M1+M2)                   ;

        return E1;
    }

    public static double getRecoilEnergy(Projectile projectile, double M2, double theta) {

        double E0 = projectile.getE() ;
        double M1 = projectile.getM() ;
        double E_RC                   ;

        theta = Math.toRadians(theta) ;

        E_RC  = E0 * 4.0d * M1*M2;
        E_RC /= (M1+M2)*(M1+M2);
        E_RC *= Math.cos(theta) * Math.cos(theta);

        return E_RC;
    }


    public static double getMaxScatteringAngle(double M1, double M2) {
        return Math.toDegrees(Math.asin(M2/M1));
    }

    public static double getBSCrossSection(Projectile projectile, int Z2, double M2, double theta,
                                           ScreeningMode screeningMode, int index) {

        //index determines which kinematic solution should be regarded
        //0 = positive solution / 1 = negative solution
        double result = 0.0;

        double M1, E0, ECM, F, V1, thetaLS, thetaCM;
        int Z1;

        F = 1.0d;

        Z1  = projectile.getZ();
        M1  = projectile.getM();
        E0  = projectile.getE();
        ECM = M2 / (M1+M2) * E0;

        //Check if there is an entry is the cross-section list. If not index will stay at -1
        int crossSectionListEntryIndex = -1;
        int tmp = 0;

        if (crossSectionData != null && !crossSectionData.isEmpty()) {

            try {

                for (CrossSectionData csd : crossSectionData) {
                    if (Z1 == csd.Z1 && (int) M1 == (int) csd.M1 && Z2 == csd.Z2 && (int) M2 == (int) csd.M2) {
                        crossSectionListEntryIndex = tmp;
                        break;
                    }
                    tmp++;
                }
            } catch (Exception ex){
                System.out.println("Error accessing cross section data.");
                crossSectionListEntryIndex = -1;
            }
        }

        //Calculate screening factor
        switch (screeningMode) {

            case LECUYER:

                F   = 1.0 - 0.04873 * Z1 * Math.pow(Z2,4.0/3.0) / ECM;

                break;

            case ANDERSON:

                thetaLS  = Math.toRadians(theta);
                thetaCM  = thetaLS + Math.asin(M1/M2 * Math.sin(thetaLS));

                V1       = 0.04872192 * Z1 * Z2 * Math.sqrt(Math.pow(Z1,2.0d/3.0d) + Math.pow(Z2,2.0d/3.0d)); // 0.04872192

                F        = V1 / (2.0d * ECM * Math.sin(thetaCM/2.0d));
                F        = Math.pow(1.0d + V1/ECM + F*F, 2);
                F        = Math.pow(1.0d + 0.5d * V1/ECM, 2) / F;

                break;

            case NONE:

            default:
                break;
        }

        boolean rr = true;

        if(crossSectionListEntryIndex != -1) {

            double E_start = crossSectionData.get(crossSectionListEntryIndex).E_start ;
            double E_end   = crossSectionData.get(crossSectionListEntryIndex).E_end   ;
            rr             = crossSectionData.get(crossSectionListEntryIndex).rr      ;

            //System.out.println("E_sart = " + E_start + ", E_end = " + E_end);
            //System.out.println("E_0 = " + E0);

            if (E0 > E_start && E0 < E_end) {

                index = 2;
                double energy[] = crossSectionData.get(crossSectionListEntryIndex).energies;
                double sigma[]  = crossSectionData.get(crossSectionListEntryIndex).crossSections;
                int j=0;

                while (E0 > energy[j]) j++;

                double m  = (sigma[j]- sigma[j-1]) / (energy[j] - energy[j-1]);
                double dE = E0 - energy[j-1];

                result = sigma[j-1] + dE * m;

                //System.out.println("E_j-1 = " + energy[j-1] + ", E_j = " + energy[j]);
                //System.out.println("s_j-1 = " + sigma[j-1] + ", s_j = " + sigma[j]);
                //System.out.println("s = " + result);
                //System.out.println("");
            }
        }

        if (index == 0) {
            result = F * getBSCrossSectionA(projectile, Z2, M2, theta);
        } else if (index == 1) {
            result = F * getBSCrossSectionB(projectile, Z2, M2, theta);
        } else if (index == 2) {
            if (rr){
                result *= getBSCrossSectionA(projectile, Z2, M2, theta);
            } else{
                // Nothing to do here.
            }
        }

        return result;
    }

    public static double getRecoilCrossSection(Projectile projectile, int Z2, double M2, double theta) {

        final double FACTOR_RC = 2.0731E7;

        double Z1 = projectile.getZ() ;
        double M1 = projectile.getM() ;
        double E  = projectile.getE() ;

        double sigmaRecoil;
        theta = Math.toRadians(theta);

        sigmaRecoil  = Z1*Z2*(M1+M2);
        sigmaRecoil *= sigmaRecoil;
        sigmaRecoil *= FACTOR_RC;
        sigmaRecoil /= (2*M2*E)*(2*M2*E)*Math.pow(Math.cos(theta),3);

        return sigmaRecoil;
    }

    private static double getBSCrossSectionA(Projectile projectile, int Z2, double M2, double theta) {

        final double FACTOR_BS = 5.1837436E6 ;

        double Z1 = projectile.getZ() ;
        double M1 = projectile.getM() ;
        double E  = projectile.getE() ;

        double sqrt, sigmaBSA;
        theta = Math.toRadians(theta);

        /*
            Z1 = 2;
            Z2 = 79;
            M1 = 4.0d;
            M2 = 197.0d;
        */

        sqrt      = Math.sqrt(M2*M2 - M1*M1*Math.sin(theta)*Math.sin(theta));
        sigmaBSA  = sqrt + M2*Math.cos(theta);
        sigmaBSA *= sigmaBSA;
        sigmaBSA /= M2*Math.pow(Math.sin(theta), 4)*sqrt;
        sigmaBSA *= FACTOR_BS * (Z1*Z1*Z2*Z2) / (E*E);

        //unit: mb/sr

        return sigmaBSA;
    }

    private static double getBSCrossSectionB(Projectile projectile, int Z2, double M2, double theta) {

        final double FACTOR_CM = 1.295935E6;

        double Z1 = projectile.getZ() ;
        double M1 = projectile.getM() ;
        double E0 = projectile.getE() ;

        double thetaLS, thetaCM, ECM, sigmaCM, sigmaLS;

        thetaLS  = Math.toRadians(theta);
        thetaCM  = thetaLS - Math.asin(M1/M2 * Math.sin(thetaLS)) + Math.PI;
        ECM      = M2 / (M1+M2) * E0;
        sigmaCM  = FACTOR_CM * Math.pow(Z1*Z2,2.0d) / (ECM * ECM * Math.pow(Math.sin(thetaCM/2.0d),4.0d));

        /*
        sigmaLS  = Math.sqrt(1.0d-Math.pow((M1/M2)*Math.sin(thetaLS),2.0d));
        sigmaLS  = (M1/M2) * Math.cos(thetaLS) / sigmaLS;
        sigmaLS  = 1.0d - sigmaLS;
        sigmaLS  = sigmaCM * (Math.sin(thetaCM) / Math.sin(thetaLS)) * sigmaLS;

        sigmaLS  = Math.pow(M1/M2 + Math.cos(thetaCM), 2);
        sigmaLS += Math.pow(Math.sin(thetaCM),2);
        sigmaLS  = Math.pow(sigmaLS, 1.5d);
        sigmaLS /= 1 + (M1/M2)*Math.cos(thetaCM);
        sigmaLS *= sigmaCM;
        */

        //TODO: Check calculation

        double y = M1/M2;
        sigmaLS = 1.0d + y*y + 2*y*Math.cos(thetaCM);
        sigmaLS = Math.pow(sigmaLS, 1.5);
        sigmaLS = sigmaLS / (1 + y*Math.cos(thetaCM));
        sigmaLS = Math.abs(sigmaLS * sigmaCM);

        System.out.println("sigma* = " +  sigmaLS);

        return sigmaLS;
    }
}

