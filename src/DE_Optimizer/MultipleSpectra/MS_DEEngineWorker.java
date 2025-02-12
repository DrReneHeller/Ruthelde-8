package DE_Optimizer.MultipleSpectra;

import javax.swing.*;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;

public class MS_DEEngineWorker extends SwingWorker<Void,Integer> {
    private MS_DEEngine deEngine;
    private boolean running, finished;

    public MS_DEEngineWorker(MS_OptimizerInput input, PrintWriter out){

        this.deEngine = new MS_DEEngine(input, out);
        this.running = true;
        this.finished = false;
    }

    public MS_DEEngine getDeEngine(){
        return deEngine;
    }

    public void stop(){
        running = false;
    }

    public boolean isFinished() {
        return finished;
    }

    @Override
    protected Void doInBackground() throws Exception {

        deEngine.initialize();

        while(running){
            if (deEngine.evolve()) running = false;
            try {Thread.sleep(10);} catch (Exception e){}
        }

        deEngine.reset();
        finished = true;
        return null;
    }
}
