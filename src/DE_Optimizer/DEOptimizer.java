package DE_Optimizer;

import DE_Optimizer.MultipleSpectra.*;

import java.io.ObjectOutputStream;
import java.io.PrintWriter;

public class DEOptimizer {

    public static OptimizerOutput optimize(OptimizerInput input, PrintWriter out){

        DEEngineWorker deEngineWorker = new DEEngineWorker(input, out);
        deEngineWorker.execute();

        while(!deEngineWorker.isFinished()) {

            //TODO: Maybe check for cancel message?
            try {Thread.sleep(10);} catch (Exception e){}
        }

        Individual individual = deEngineWorker.getDeEngine().getBest();

        OptimizerOutput output = new OptimizerOutput();

        output.target     = individual.getTarget().getDeepCopy() ;
        output.charge     = individual.getCharge()               ;
        output.factor     = individual.getCalibrationFactor()    ;
        output.offset     = individual.getCalibrationOffset()    ;
        output.resolution = individual.getResolution()           ;
        output.fitness    = individual.getFitness()              ;
        output.optimizationTime = deEngineWorker.getDeEngine().getTotalTime()/1000.0d;

        return output;
    }

    public static MS_OptimizerOutput optimizeMS(MS_OptimizerInput input, PrintWriter out){

        MS_DEEngineWorker deEngineWorker = new MS_DEEngineWorker(input, out);
        deEngineWorker.execute();

        while(!deEngineWorker.isFinished()) {

            //TODO: Maybe check for cancel message?
            try {Thread.sleep(10);} catch (Exception e){}
        }

        MS_Individual individual = deEngineWorker.getDeEngine().getBest();

        MS_OptimizerOutput output = new MS_OptimizerOutput();

        int size = input.measurements.length;
        output.spectraParameters = new MS_MeasurementOutput[size];
        for (int i=0; i<size; i++) {output.spectraParameters[i] = new MS_MeasurementOutput();}
        int index = 0;

        for (MS_MeasurementOutput mo : output.spectraParameters) {

            mo.charge     = individual.getCharge()[index]           ;
            mo.factor     = individual.getCalibrationFactor(index)  ;
            mo.offset     = individual.getCalibrationOffset(index)  ;
            mo.resolution = individual.getResolution()[index]       ;

            index++;
        }

        output.targetModel = individual.getTarget().getDeepCopy() ;
        output.fitness     = individual.getFitness()              ;
        output.optimizationTime = deEngineWorker.getDeEngine().getTotalTime()/1000.0d;

        return output;
    }
}
