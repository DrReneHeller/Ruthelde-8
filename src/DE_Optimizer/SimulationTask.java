package DE_Optimizer;

public class SimulationTask implements Runnable {

    private Individual individual;

    public SimulationTask(Individual individual){
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
