package Simulator.Stopping;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;

public class StoppingParaFile {

    public String creation_time, reference, doi;
    public int z1;
    public StoppingParaEntry[] data;

    public StoppingParaFile(){

    }

    public StoppingParaFile(StoppingParaEntry[] data){

        this.data = data;
    }

    public static StoppingParaFile load(String fileName){

        //System.out.println("StoppingParaFile: " + fileName);

        StoppingParaFile result = null;

        if(fileName != null && fileName != "") {

            //System.out.println("Loading stopping file.");

            File file = new File(fileName);

            if (file.exists()) {

                Gson gson = new Gson();

                try {
                    FileReader fr = new FileReader(file);
                    result = gson.fromJson(fr, StoppingParaFile.class);
                    //System.out.println("Stopping data successfully imported from " + file.getName());
                } catch (Exception ex) {
                    System.out.println("Error loading stopping data form file '" + fileName + "'");
                    //ex.printStackTrace();
                }
            } else {
                System.out.println("StoppingParaFile: Error reading stopping para File '" + fileName +"'");
            }
        }

        return result;
    }

    public static LinkedList<String> getFileList(){

        LinkedList<String> result = new LinkedList<>();

        File folder = new File("stopping");
        File[] listOfFiles = folder.listFiles();

        if(listOfFiles != null) {

            int size = listOfFiles.length;

            for (int i = 0; i < size; i++) {

                if (listOfFiles[i].isFile()) {
                    //System.out.print(listOfFiles[i].getName());
                    result.add(listOfFiles[i].getName());
                }
            }
        }

        return result;
    }

    public StoppingParaFile getDeepCopy(){

        StoppingParaEntry[] new_data = new StoppingParaEntry[data.length];

        for (int i=0; i<data.length; i++){
            new_data[i] = data[i].getDeepCopy();
        }

        return new StoppingParaFile(new_data);
    }

}
