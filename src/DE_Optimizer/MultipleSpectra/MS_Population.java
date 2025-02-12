package DE_Optimizer.MultipleSpectra;

import DE_Optimizer.Individual;
import DE_Optimizer.OptimizerInput;

import java.util.LinkedList;

public class MS_Population {

    private final LinkedList<MS_Individual> individualList;

    public MS_Population(MS_OptimizerInput input){

        individualList = new LinkedList<>();

        int size = input.deParameter.populationSize;

        for (int i = 0; i < size; i++){
            if (i < 3*size/4) {
                individualList.add(new MS_Individual(input, 0.01d));
            } else{
                individualList.add(new MS_Individual(input, 1.0d));
            }
        }

        for (MS_Individual individual : individualList){
            individual.simulate();
        }
    }

    public LinkedList<MS_Individual> getIndividualList(){
        return individualList;
    }

    public int getBestFitnessIndex(){

        int result = 0;
        int index  = 0;

        for (MS_Individual individual : individualList){

            if (individual.getFitness() > individualList.get(result).getFitness()){
                result = index;
            }
            index++;
        }

        return result;
    }

    public double getAverageFitness(){

        double result = 0;
        for (MS_Individual individual : individualList){ result += individual.getFitness(); }
        result /= individualList.size();
        return result;
    }
}

