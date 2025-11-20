package Uncertainty;

import java.util.LinkedList;

public class StatisticsCalculator {

    public String name;
    public double setValue, avr, std_set, std_avr;
    public LinkedList<Double> values;

    public StatisticsCalculator(){
        name = "";
        setValue = 0.0d;
        avr = 0.0d;
        std_set = 0.0d;
        std_avr = 0.0d;
        values = new LinkedList<>();
    }

    public void add(double value){
        values.add(value);
    }

    public String getStatistics(){

        int size = values.size();
        double sum = 0.0d;
        for (double value : values) sum += value;
        avr = sum / (double)size;

        sum = 0.0d;
        for (double value : values) sum += (setValue - value) * (setValue - value);
        std_set = Math.sqrt(sum / (double) size);

        sum = 0.0d;
        for (double value : values) sum += (avr - value) * (avr - value);
        std_avr = Math.sqrt(sum / (double) size);

        StringBuilder sb = new StringBuilder();

        sb.append(name);
        sb.append("\t");

        //sb.append("Set Value: ");
        sb.append(String.format("%.4f", setValue));
        sb.append("\t");

        //sb.append("Average Value: ");
        sb.append(String.format("%.4f", avr));
        sb.append("\t");

        //sb.append("Std. (from set value): ");
        sb.append(String.format("%.4f", std_set));
        sb.append("\t");
        double percentage = std_set / setValue * 100.0d;
        //sb.append(" [");
        sb.append(String.format("%.2f", percentage));
        //sb.append("%]");
        sb.append("\t");

        //sb.append("Std. (from avr. value): ");
        sb.append(String.format("%.4f", std_avr));
        sb.append("\t");
        percentage = std_avr / setValue * 100.0d;
        //sb.append(" [");
        sb.append(String.format("%.2f", percentage));
        //sb.append("%]");

        return sb.toString();
    }

}
