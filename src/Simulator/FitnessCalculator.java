package Simulator;

import Simulator.Detector.DetectorSetup;
import mr.go.sgfilter.SGFilter;

public abstract class FitnessCalculator {
    public static double calcFitness(DetectorSetup ds, int startChannel, int endChannel, double[] simSpectrum, double[] expSpectrum){

        double temp = ds.getResolution() / ds.getCalibration().getFactor();
        temp = Math.floor(Math.floor(temp/2.0f)) * 2.0f +1.0f;
        int filterLength = (int) temp;
        if (filterLength < 4) filterLength = 4;

        SGFilter sgFilter = new SGFilter(filterLength/2, filterLength/2);
        double[] coeff = SGFilter.computeSGCoefficients(filterLength/2, filterLength/2, 3);
        double[] smoothedSpectrum = sgFilter.smooth(expSpectrum, coeff);

        double LFF = 0.0f;
        for (int i=startChannel; i<endChannel; i++){
            LFF += Math.pow(smoothedSpectrum[i] - expSpectrum[i],2);
        }

        double sigma2 = 0.0f;

        for (int i=startChannel; i<endChannel; i++){
            sigma2 += Math.pow(expSpectrum[i]-simSpectrum[i],2);
        }

        //sigma2 = LFF / sigma2 * 100.0f;
        sigma2 = Math.log(LFF) / Math.log(sigma2) * 100.0f;

        return sigma2;
    }
}
