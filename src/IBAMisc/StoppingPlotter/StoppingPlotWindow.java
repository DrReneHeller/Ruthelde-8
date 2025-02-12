package IBAMisc.StoppingPlotter;

import Helper.Plot.*;
import IBAMisc.IBAMiscInput;
import Simulator.Stopping.StoppingCalculator;
import Simulator.Stopping.StoppingParaFile;
import Simulator.Target.Layer;
import javax.swing.*;
import java.awt.*;

public class StoppingPlotWindow extends JFrame{

    private PlotWindow plotWindow;
    private IBAMiscInput input;
    private JPanel rootPanel;
    private JTextField tfSPEMin, tfSPEMax;
    private JComboBox cBoxSPUnit;
    private JButton showPlotButton;

    public StoppingPlotWindow(){

        super("Stopping Plotter");
        initComponents();
    }

    private void initComponents() {

        this.setDefaultCloseOperation(HIDE_ON_CLOSE);
        this.setContentPane(rootPanel);
        pack();
        this.setMinimumSize(getSize());
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        this.setLocation(dim.width / 2 - this.getWidth() / 2, dim.height / 2 - this.getHeight() / 2);

        plotWindow = new PlotWindow("Stopping Plotter");

        tfSPEMin.addActionListener(e -> refresh());
        tfSPEMax.addActionListener(e -> refresh());
        cBoxSPUnit.addActionListener(e -> refresh());
        showPlotButton.addActionListener(e -> plotWindow.setVisible(true));
    }

    public void setInput(IBAMiscInput input){
        this.input = input;
        refresh();
    }

    public void setLastFolder(String lastFolder){
        this.plotWindow.setLastFolder(lastFolder);
    }

    public void refresh(){

        input.EMin  = Double.parseDouble(tfSPEMin.getText());
        input.EMax  = Double.parseDouble(tfSPEMax.getText());
        input.unitY = cBoxSPUnit.getSelectedIndex();

        MyPlotGenerator pg = getPlot(input);

        plotWindow.setPlotSeries(pg.plotSeries);
        plotWindow.getPlotProperties().xAxisName = pg.plotProperties.xAxisName;
        plotWindow.getPlotProperties().yAxisName = pg.plotProperties.yAxisName;
        plotWindow.refresh();
    }

    private MyPlotGenerator getPlot(IBAMiscInput input) {

        double E0Org = input.projectile.getE();

        final int NUM_POINTS = 100;
        double conversionFactor;
        double[] E0 = new double[NUM_POINTS];
        double[] Se = new double[NUM_POINTS];
        double[] Sn = new double[NUM_POINTS];
        double[] St = new double[NUM_POINTS];

        Layer layer = input.target.getLayerList().get(0);

        String xAxisName, yAxisName;

        if (input.unitY == 0) {
            xAxisName = "E (keV)";
            yAxisName = "S (eV/10^15 at/cm2)";
            conversionFactor = 1.0d;
        } else {
            xAxisName = "E (keV)";
            yAxisName = "S (keV/nm)";
            conversionFactor = layer.getThicknessConversionFactor() * 1000;
        }

        double logE = Math.log10(input.EMin);
        double increment = (Math.log10(input.EMax) - Math.log10(input.EMin)) / NUM_POINTS;

        StoppingParaFile stoppingParaFile = null;
        if (input.stoppingData != null && !input.stoppingData.equals("")){
            stoppingParaFile = StoppingParaFile.load(input.stoppingData);
        }
        StoppingCalculator stoppingCalculator = new StoppingCalculator(stoppingParaFile, input.correctionFactors);


        for (int i=0; i<NUM_POINTS; i++) {
            logE += increment;
            E0[i] = Math.pow(10, logE);
            input.projectile.setE(E0[i]);

            Se[i] = stoppingCalculator.getStoppingPower(input.projectile, layer,
                    input.stoppingPowerCalculationMode, input.compoundCalculationMode,0);
            Se[i] /= conversionFactor;
            Sn[i] = stoppingCalculator.getStoppingPower(input.projectile, layer,
                    input.stoppingPowerCalculationMode, input.compoundCalculationMode,1);
            Sn[i] /= conversionFactor;
            St[i] = Se[i] + Sn[i];
        }

        MyPlotGenerator plotGenerator = new MyPlotGenerator();

        plotGenerator.plotProperties.xAxisName = xAxisName;
        plotGenerator.plotProperties.yAxisName = yAxisName;

        plotGenerator.plotSeries.clear();

        PlotSeries ps1 = new PlotSeries("Se", E0, Se);
        ps1.seriesProperties.color_red = 0;
        ps1.seriesProperties.color_green = 0;
        ps1.seriesProperties.color_blue = 255;
        ps1.seriesProperties.dashed = true;
        ps1.setStroke(4);
        plotGenerator.plotSeries.add(ps1);

        PlotSeries ps2 = new PlotSeries("Sn", E0, Sn);
        ps1.seriesProperties.color_red = 0;
        ps1.seriesProperties.color_green = 255;
        ps1.seriesProperties.color_blue = 0;
        ps2.seriesProperties.dashed = true;
        ps2.setStroke(4);
        plotGenerator.plotSeries.add(ps2);

        PlotSeries ps3 = new PlotSeries("S_total", E0, St);
        ps1.seriesProperties.color_red = 255;
        ps1.seriesProperties.color_green = 0;
        ps1.seriesProperties.color_blue = 0;
        ps3.seriesProperties.dashed = false;
        ps3.setStroke(4);
        plotGenerator.plotSeries.add(ps3);

        input.projectile.setE(E0Org);

        return plotGenerator;
    }

}

