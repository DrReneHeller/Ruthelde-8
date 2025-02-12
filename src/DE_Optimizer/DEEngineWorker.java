package DE_Optimizer;

import javax.swing.*;
import java.io.PrintWriter;

public class DEEngineWorker extends SwingWorker<Void,Integer> {
    private DEEngine deEngine;
    private boolean running, finished;

    public DEEngineWorker(OptimizerInput input, PrintWriter out){

        this.deEngine = new DEEngine(input, out);
        this.running = true;
        this.finished = false;
    }

    public DEEngine getDeEngine(){
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

        setProgress(90);

        deEngine.reset();
        finished = true;
        return null;
    }
}
