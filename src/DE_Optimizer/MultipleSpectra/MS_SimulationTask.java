package DE_Optimizer.MultipleSpectra;

import DE_Optimizer.Individual;

public class MS_SimulationTask implements Runnable {

    private MS_Individual individual;

    public MS_SimulationTask(MS_Individual individual){
        this.individual = individual;
    }

    public void run() {
        try {
            individual.simulate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
