package IBAMisc.DepthPlotter;

import Helper.Plot.MyPlotGenerator;
import Helper.Plot.PlotSeries;
import Helper.Plot.PlotWindow;
import IBAMisc.IBAMiscInput;
import Simulator.Stopping.StoppingCalculator;
import Simulator.Stopping.StoppingParaFile;
import Simulator.Target.Layer;
import javax.swing.*;
import java.awt.*;

public class DepthPlotWindow extends JFrame{

    private PlotWindow plotWindow;
    private IBAMiscInput input;
    private JPanel rootPanel;
    private JComboBox cBoxSPUnitX, cBoxSPUnitY;
    private JButton showPlotButton;

    private static final double ECutOff = 10.0d;

    public DepthPlotWindow(){

        super("Depth Plotter");
        initComponents();
    }

    private void initComponents() {

        this.setDefaultCloseOperation(HIDE_ON_CLOSE);
        this.setContentPane(rootPanel);
        pack();
        this.setMinimumSize(getSize());
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        this.setLocation(dim.width / 2 - this.getWidth() / 2, dim.height / 2 - this.getHeight() / 2);

        plotWindow = new PlotWindow("Depth Plotter");

        cBoxSPUnitX.addActionListener(e -> refresh());
        cBoxSPUnitY.addActionListener(e -> refresh());
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

        input.unitX = cBoxSPUnitX.getSelectedIndex();
        input.unitY = cBoxSPUnitY.getSelectedIndex();

        MyPlotGenerator pg = getPlot(input);

        plotWindow.setPlotSeries(pg.plotSeries);
        plotWindow.getPlotProperties().xAxisName = pg.plotProperties.xAxisName;
        plotWindow.getPlotProperties().yAxisName = pg.plotProperties.yAxisName;
        plotWindow.refresh();
    }

    private MyPlotGenerator getPlot(IBAMiscInput input) {

        double E0 = input.projectile.getE();

        final int NUM_POINTS_PER_LAYER = 1024                         ;
        double    depth, thickness, E, S, layerArealDensity, stepSize ;
        int       layerIndex                                          ;
        String    xLabel, yLabel                                      ;

        MyPlotGenerator plotGenerator = new MyPlotGenerator();

        int numberOfLayers = input.target.getLayerList().size();

        double x[][] = new double[numberOfLayers][NUM_POINTS_PER_LAYER+1];
        double y[][] = new double[numberOfLayers][NUM_POINTS_PER_LAYER+1];

        switch (input.unitX) {
            case 0:
                xLabel = "depth (nm)";
                break;
            case 1:
                xLabel = "Areal density (10^15 at/cm)";
                break;
            default:
                xLabel = "";
                break;
        }

        switch (input.unitY) {
            case 0:
                yLabel = "E (keV)";
                break;
            case 1:
                yLabel = "dE/dx (keV/nm)";
                break;
            case 2:
                yLabel = "dE/dx (eV/10^15 at/cm2)";
                break;
            default:
                yLabel = "";
                break;
        }

        plotGenerator.plotProperties.xAxisName = xLabel;
        plotGenerator.plotProperties.yAxisName = yLabel;

        depth      = 0.0d              ;
        thickness  = 0.0d              ;
        E          = input.projectile.getE() ;
        layerIndex = 0                 ;

        StoppingParaFile stoppingParaFile = null;
        if (input.stoppingData != null && !input.stoppingData.equals("")){
            stoppingParaFile = StoppingParaFile.load(input.stoppingData);
        }
        StoppingCalculator stoppingCalculator = new StoppingCalculator(stoppingParaFile, input.correctionFactors);

        for (Layer layer: input.target.getLayerList()) {
            layerArealDensity = layer.getArealDensity();
            stepSize = layerArealDensity / NUM_POINTS_PER_LAYER;

            for (int i=0; i<=NUM_POINTS_PER_LAYER; i++) {

                S = stoppingCalculator.getStoppingPower(input.projectile, layer, input.stoppingPowerCalculationMode,
                        input.compoundCalculationMode, 2);

                switch (input.unitX) {
                    case 0:
                        x[layerIndex][i] = thickness;
                        break;
                    case 1:
                        x[layerIndex][i] = depth;
                        break;
                }

                switch (input.unitY) {
                    case 0:
                        y[layerIndex][i] = E;
                        break;
                    case 1:
                        y[layerIndex][i] = S / (layer.getThicknessConversionFactor() * 1000.0d);
                        break;
                    case 2:
                        y[layerIndex][i] = S;
                        break;
                }

                if (E > ECutOff) {
                    input.projectile.setE(E);
                    depth += stepSize;
                    thickness += stepSize * layer.getThicknessConversionFactor();
                    E -= S * stepSize / 1000.0d;
                    if (E<0) E=ECutOff;
                }
            }
            layerIndex++;
        }

        x[0][0] = x[0][1];
        y[0][0] = y[0][1];

        plotGenerator.plotSeries.clear();

        for (int i=0; i<numberOfLayers; i++) {
            String seriesName = "Layer_" + i;
            PlotSeries ps = new PlotSeries(seriesName, x[i], y[i]);
            ps.setStroke(4);
            float h = (float) i / (float) numberOfLayers + 0.33f;
            //ps.setColor(new Color(Color.HSBtoRGB(h, 1, 1)));
            Color color = new Color(Color.HSBtoRGB(h, 1, 1));
            ps.setColor(color.getRed(), color.getGreen(), color.getBlue());
            plotGenerator.plotSeries.add(ps);
        }

        input.projectile.setE(E0);
        return plotGenerator;
    }

}

