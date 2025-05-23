package DE_Optimizer;

import java.util.LinkedList;

public class Population {

    private final LinkedList<Individual> individualList;

    public Population(OptimizerInput input){

        individualList = new LinkedList<>();

        int size = input.deParameter.populationSize;

        for (int i = 0; i < size; i++){
            if (i < 3*size/4) {
                individualList.add(new Individual(input, 0.01d));
            } else{
                individualList.add(new Individual(input, 1.0d));
            }
        }

        for (Individual individual : individualList){
            individual.simulate();
        }
    }

    public LinkedList<Individual> getIndividualList(){
        return individualList;
    }

    public int getBestFitnessIndex(){

        int result = 0;
        int index  = 0;

        for (Individual individual : individualList){

            if (individual.getFitness() > individualList.get(result).getFitness()){
                result = index;
            }
            index++;
        }

        return result;
    }

    public double getAverageFitness(){

        double result = 0;
        for (Individual individual : individualList){ result += individual.getFitness(); }
        result /= individualList.size();
        return result;
    }
}

