package Simulator.Stopping;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class StoppingParaFile_All {

    public String creation_time, reference, doi;
    public StoppingParaEntry_Ext[][] parameters;

    public static StoppingParaFile_All load(String fileName){

        StoppingParaFile_All result = null;

        if(fileName != null && fileName != "") {

            File file = new File(fileName);

            if (file.exists()) {

                Gson gson = new Gson();

                try {
                    FileReader fr = new FileReader(file);
                    result = gson.fromJson(fr, StoppingParaFile_All.class);
                } catch (Exception ex) {
                    System.out.println("Error loading stopping data form file '" + fileName + "'");
                    ex.printStackTrace();
                }
            } else {
                System.out.println("StoppingParaFile: Error reading stopping para File '" + fileName +"'");
            }
        }

        return result;
    }

    public static void convert(String fileName){

        StoppingParaFile_All stoppingParaFileAll = load(fileName);

        for (int z1=1; z1<93; z1++){

            StoppingParaFile stoppingParaFile = new StoppingParaFile();

            stoppingParaFile.creation_time = stoppingParaFileAll.creation_time;
            stoppingParaFile.reference = stoppingParaFileAll.reference;
            stoppingParaFile.doi = stoppingParaFileAll.doi;
            stoppingParaFile.z1 = z1;

            StoppingParaEntry[] stoppingParaEntries = new StoppingParaEntry[93];

            for (int z2=1; z2<93; z2++){

                stoppingParaEntries[z2] = new StoppingParaEntry();
                stoppingParaEntries[z2].z2 = z2;
                stoppingParaEntries[z2].params = stoppingParaFileAll.parameters[z1][z2].params;
                stoppingParaEntries[z2].error_data = new StoppingParaEntry.ErrorData();
                stoppingParaEntries[z2].error_data.chi2r2 = stoppingParaFileAll.parameters[z1][z2].error_data.chi2r2;
                stoppingParaEntries[z2].error_data.sigma_per_decade = stoppingParaFileAll.parameters[z1][z2].error_data.sigma_per_decade;
            }

            stoppingParaFile.data = stoppingParaEntries;

            try {
                String name = "StoppingData/SCS2024_01_GAS/SCS2024_01_GAS_Z1=" + z1 + ".json";
                File file = new File(name);
                FileWriter fw = new FileWriter(file);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(stoppingParaFile, fw);
                fw.flush();
                fw.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

}
