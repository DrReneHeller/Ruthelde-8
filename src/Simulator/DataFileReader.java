package Simulator;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.LinkedList;

public class DataFileReader {

    public static double[] readASCIIFileOneColumn(File file) {

        double result[] = new double[0];
        String currentLine;
        BufferedReader inputBuffer;
        LinkedList<Double> spectrum = new LinkedList<>();

        try {
            if (file.exists() && file.isFile() && file.canRead()) {
                inputBuffer = new BufferedReader(new FileReader(file));

                while ((currentLine = inputBuffer.readLine()) != null) {
                    spectrum.add(Double.parseDouble(currentLine));
                }

                result = new double[spectrum.size()];
                for (int i= 0; i < spectrum.size(); i++) result[i] = spectrum.get(i);
                inputBuffer.close();
            }
        } catch (Exception ex) {}

        return result;
    }

    public static double[] readASCIIFileTwoColumn(File file) {

        double result[] = new double[0];
        String currentLine;
        BufferedReader inputBuffer;
        LinkedList<Double> spectrum = new LinkedList<>();

        try {
            if (file.exists() && file.isFile() && file.canRead()) {
                inputBuffer = new BufferedReader(new FileReader(file));

                while ((currentLine = inputBuffer.readLine()) != null) {
                    spectrum.add(Double.parseDouble(currentLine.split("\t")[1]));
                }

                result = new double[spectrum.size()];
                for (int i= 0; i < spectrum.size(); i++) result[i] = spectrum.get(i);
                inputBuffer.close();
            }
        } catch (Exception ex) {ex.printStackTrace();}

        return result;
    }

    public static double[] readIBCDataFile(File file) {

        double result[] = new double[0];
        String currentLine;
        BufferedReader inputBuffer;
        LinkedList<Integer> spectrum = new LinkedList<>();
        boolean foundStartOfData = false;

        try {
            if (file.exists() && file.isFile() && file.canRead()) {
                inputBuffer = new BufferedReader(new FileReader(file));

                while ((currentLine = inputBuffer.readLine()) != null) {
                    if (!foundStartOfData) {
                        if (currentLine.contains("Data:")) foundStartOfData = true;
                    }
                    else {
                        spectrum.add(Integer.parseInt(currentLine.split("\t")[1]));
                    }
                }

                result = new double[spectrum.size()];
                for (int i= 0; i < spectrum.size(); i++) result[i] = spectrum.get(i);
                inputBuffer.close();
            }
        } catch (Exception ex) {}

        return result;
    }

    public static double[] readIMECDataFile(File file) {

        double result[] = new double[0];
        String currentLine;
        BufferedReader inputBuffer;
        LinkedList<Integer> spectrum = new LinkedList<Integer>();
        boolean foundStartOfData = false;

        try {
            if (file.exists() && file.isFile() && file.canRead()) {
                inputBuffer = new BufferedReader(new FileReader(file));

                while ((currentLine = inputBuffer.readLine()) != null) {
                    if (!foundStartOfData) {
                        if (currentLine.contains(" % End comments")) {
                            foundStartOfData = true;
                        }
                    }
                    else {
                        spectrum.add(Integer.parseInt(currentLine.split(", ")[1]));
                    }
                }

                result = new double[spectrum.size()];
                for (int i=0; i < spectrum.size(); i++) {
                    result[i] = spectrum.get(i);
                }
                inputBuffer.close();
            }
        } catch (Exception ex) {System.out.println(ex.getMessage());}

        return result;
    }

    public static double[] read3MVAllDataFile(File file, int spectrumIndex) {

        double result[] = new double[0];
        String currentLine;
        BufferedReader inputBuffer;
        LinkedList<Double> spectrum = new LinkedList<Double>();
        boolean foundStartOfData = false;

        try {
            if (file.exists() && file.isFile() && file.canRead()) {
                inputBuffer = new BufferedReader(new FileReader(file));

                while ((currentLine = inputBuffer.readLine()) != null) {
                    if (!foundStartOfData) {
                        if (currentLine.contains("<data>")) foundStartOfData = true;
                    }
                    else {
                        String strVal = currentLine.split("\t")[spectrumIndex].replace(",",".");
                        double dblVal = Double.parseDouble(strVal);
                        spectrum.add(dblVal);
                    }
                }

                result = new double[spectrum.size()];
                for (int i= 0; i < spectrum.size(); i++) result[i] = spectrum.get(i);
                inputBuffer.close();
            }
        } catch (Exception ex) {System.out.println("Error parsing spectrum: " + ex.getMessage());}

        return result;
    }

    public static double[] readExpSpectrumFromSimulationFile(File file){

        double result[] = new double[0];

        try {

            if (file != null) {

                Gson gson = new Gson();

                FileReader fr = new FileReader(file);
                DataFile df = gson.fromJson(fr, DataFile.class);

               result = df.experimentalSpectrum;
            }

        } catch (Exception ex){ System.out.println("Error loading simulation file: "+ ex.getMessage()); }

        return result;
    }
}
