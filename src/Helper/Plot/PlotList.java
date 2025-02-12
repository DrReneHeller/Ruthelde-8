package Helper.Plot;

import java.util.LinkedList;

public class PlotList {

    public LinkedList<PlotSeries> plotSeries;
    public String dateTime;

    public PlotList(LinkedList<PlotSeries> plotSeries, String dateTime){
        this.plotSeries = plotSeries;
        this.dateTime = dateTime;
    }

}
