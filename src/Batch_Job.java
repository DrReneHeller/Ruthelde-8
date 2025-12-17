import java.io.File;

public class Batch_Job {

    public boolean active;
    public File[] files;
    public String batchFolder;
    public int index;

    public Batch_Job(File[] files, String batchFolder){

        this.files = files;
        this.batchFolder = batchFolder;
        index = 0;
        active = true;
    }
}
