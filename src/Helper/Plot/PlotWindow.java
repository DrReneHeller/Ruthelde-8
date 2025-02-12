package Helper.Plot;

import Helper.Spectrum_3MV;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;

public class PlotWindow extends JFrame {

    private JPanel rootPanel;
    private JPanel plotPanel;

    private MyPlotGenerator plotGenerator;
    private String lastFolder;

    private LinkedList<PlotSeries> plotSeries, loadedPlotSeries;

    public PlotWindow(String title) {

        super(title);
        plotGenerator = new MyPlotGenerator();
        lastFolder = null;
        plotSeries = new LinkedList<>();
        loadedPlotSeries = new LinkedList<>();
        initComponents();
    }

    public MyPlotGenerator getPlotGenerator() {
        return plotGenerator;
    }

    public void setPlotSeries(LinkedList<PlotSeries> plotSeries) {

        this.plotSeries = plotSeries;
    }

    public PlotProperties getPlotProperties() {
        return plotGenerator.plotProperties;
    }

    public LinkedList<PlotSeries> getPlotSeries() {
        return plotGenerator.plotSeries;
    }

    public void setLastFolder(String lastFolder) {
        this.lastFolder = lastFolder;
    }

    public void refresh() {

        plotGenerator.plotSeries = new LinkedList<>();
        plotGenerator.plotSeries.addAll(plotSeries);

        int numPlots = loadedPlotSeries.size();
        int index = 0;
        float colOffset = 0.1f;

        for (PlotSeries ps : loadedPlotSeries){

            float h = (float) index / (float) numPlots + colOffset;
            Color color = new Color(Color.HSBtoRGB(h, 1, 1));
            ps.setColor(color.getRed(), color.getGreen(), color.getBlue());
            //ps.seriesProperties.showSymbols = false;
            //ps.seriesProperties.showLine = true;
            ps.seriesProperties.dashed = false;
            ps.seriesProperties.stroke = 1;
            index++;
        }

        plotGenerator.plotSeries.addAll(loadedPlotSeries);
        plotGenerator.render(plotPanel);
    }

    public void saveImage(File file) {

        if (file == null) {

            final JFileChooser fc;
            if (lastFolder != null) fc = new JFileChooser(lastFolder);
            else fc = new JFileChooser();
            int returnVal = fc.showSaveDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                file = fc.getSelectedFile();
                lastFolder = file.getParent();
            }
        }

        if (file != null) {

            BufferedImage image = new BufferedImage(plotPanel.getWidth(), plotPanel.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics g = image.getGraphics();
            plotPanel.paint(g);

            try {
                ImageIO.write(image, "png", file);
            } catch (IOException ex) {
                System.out.println("Error when saving graphics: " + ex.getMessage());
            }
        }

    }

    public void exportAscii(File _file) {

        LinkedList<PlotSeries> pss = plotGenerator.plotSeries;

        int numPlots = pss.size();
        int length = pss.getFirst().data.size();

        Double[][] data = new Double[2 * numPlots + 1][length];
        StringBuilder sb = new StringBuilder();

        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateTime = formatter.format(date);

        sb.append("<Header> \n");

        sb.append("  " + dateTime + "\n");
        sb.append("  " + this.getTitle() + "\n\n");

        sb.append("  Column 1: \t Line Number \n");

        for (int i = 0; i < length; i++) data[0][i] = (double) (i + 1);

        int plotIndex = 1;

        for (PlotSeries ps : pss) {

            sb.append("  Column " + (2 * plotIndex + 0) + ": \t " + ps.seriesProperties.name + " (x-Values) \n");
            sb.append("  Column " + (2 * plotIndex + 1) + ": \t " + ps.seriesProperties.name + " (y-Values) \n");

            for (int i = 0; i < length; i++) {
                data[2 * plotIndex - 1][i] = ps.data.get(i).x;
            }
            for (int i = 0; i < length; i++) {
                data[2 * plotIndex][i] = ps.data.get(i).y;
            }

            plotIndex++;
        }

        sb.append("</Header> \n\n");

        for (int i = 0; i < length; i++) {

            sb.append(String.format("%.4e", (double) (i + 1)) + "\t\t");

            for (int j = 0; j < numPlots; j++) {
                sb.append(String.format("%.4e", data[2 * j + 1][i]) + "\t\t");
                sb.append(String.format("%.4e", data[2 * j + 2][i]) + "\t\t");
            }

            sb.append("\n");
        }

        if (_file == null) {

            try {
                final JFileChooser fc;
                if (lastFolder != null) fc = new JFileChooser(lastFolder);
                else fc = new JFileChooser();
                int returnVal = fc.showSaveDialog(this);

                if (returnVal == JFileChooser.APPROVE_OPTION) {

                    File file = fc.getSelectedFile();
                    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                    writer.write(sb.toString());
                    writer.close();
                    lastFolder = file.getParent();
                }

            } catch (Exception ex) {
                System.out.println("Error writing ASCII file: " + ex.getMessage());
            }
        } else {
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(_file));
                writer.write(sb.toString());
                writer.close();
                lastFolder = _file.getParent();
            } catch (Exception ex) {
                System.out.println("Error writing ASCII file: " + ex.getMessage());
            }

        }
    }

    public void exportJSON(File _file) {

        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateTime = formatter.format(date);
        PlotList plotList = new PlotList(plotGenerator.plotSeries, dateTime);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        if (_file == null) {

            try {
                final JFileChooser fc;
                if (lastFolder != null) fc = new JFileChooser(lastFolder);
                else fc = new JFileChooser();

                fc.addChoosableFileFilter(new CFileFilter("json", "Spectra-JSON File (*.json)"));
                fc.setAcceptAllFileFilterUsed(false);
                int returnVal = fc.showSaveDialog(this);

                if (returnVal == JFileChooser.APPROVE_OPTION) {

                    CFileFilter cf = (CFileFilter) fc.getFileFilter();
                    File file = fc.getSelectedFile();

                    String dir = file.getParentFile().toString();
                    String name = file.getName();
                    if (name.contains(".")) name = name.split("\\.")[0];
                    name = name + "." + cf.getFileExt();
                    file = new File(dir + "/" + name);
                    lastFolder = file.getParent();

                    FileWriter fw = new FileWriter(file);
                    gson.toJson(plotList, fw);

                    fw.flush();
                    fw.close();
                }

            } catch (Exception ex) {
                System.out.println("Error writing JSON file: " + ex.getMessage());
                ex.printStackTrace();
            }
        } else {
            try {
                FileWriter fw = new FileWriter(_file);
                gson.toJson(plotList, fw);
                fw.flush();
                fw.close();
                lastFolder = _file.getParent();
            } catch (Exception ex) {
                System.out.println("Error writing JSON file: " + ex.getMessage());
            }

        }
    }

    public void addJSON(File _file){

        File selectedFile = null;

        if (_file == null) {

            final JFileChooser fc;
            if (lastFolder != null) fc = new JFileChooser(lastFolder);
            else fc = new JFileChooser();
            int returnVal = fc.showOpenDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                selectedFile = fc.getSelectedFile();
                lastFolder = fc.getSelectedFile().getParent();
                setLastFolder(lastFolder);
            }
        } else selectedFile = _file;

        if (selectedFile != null) {

            Gson gson = new Gson();

            try {
                FileReader fr = new FileReader(selectedFile);
                PlotList plotList = gson.fromJson(fr, PlotList.class);

                int index = loadedPlotSeries.size() + 1;

                for (PlotSeries ps : plotList.plotSeries){
                    String name = ps.seriesProperties.name;
                    String fileName = selectedFile.getName();
                    if (fileName.contains(".")) fileName = fileName.split("\\.")[0];
                    String indexStr = "";
                    if (index < 10) indexStr += "0";
                    indexStr += index;
                    String newName = indexStr + "_" + fileName + "_" + name;
                    ps.seriesProperties.name = newName;
                    index++;
                }

                loadedPlotSeries.addAll(plotList.plotSeries);
                refresh();

            } catch (Exception ex) {
                System.out.println("Could not read JSON file.");
                ex.printStackTrace();
            }

        }

    }

    public void clearJSON(){

        loadedPlotSeries = new LinkedList<>();
        refresh();
    }

    private void buildMenu() {

        JMenuBar jmb = new JMenuBar();

        JMenu fileMenu = new JMenu("File");

        JMenuItem itemExportAscii = new JMenuItem("Export ASCII");
        itemExportAscii.addActionListener(e -> exportAscii(null));
        fileMenu.add(itemExportAscii);

        JMenuItem itemExportJSON = new JMenuItem("Export JSON");
        itemExportJSON.addActionListener(e -> exportJSON(null));
        fileMenu.add(itemExportJSON);

        JMenuItem itemAddJSON = new JMenuItem("Add JSON");
        itemAddJSON.addActionListener(e -> addJSON(null));
        fileMenu.add(itemAddJSON);

        JMenuItem itemClearJSON = new JMenuItem("Clear JSON");
        itemClearJSON.addActionListener(e -> clearJSON());
        fileMenu.add(itemClearJSON);

        JMenuItem itemSavePNG = new JMenuItem("Save as PNG");
        itemSavePNG.addActionListener(e -> saveImage(null));
        fileMenu.add(itemSavePNG);

        jmb.add(fileMenu);

        JMenu plotMenu = new JMenu("Plot");

        JCheckBoxMenuItem jcbAutoScaleX = new JCheckBoxMenuItem("Auto Scale X");
        jcbAutoScaleX.setSelected(plotGenerator.plotProperties.autoScaleX);
        jcbAutoScaleX.addActionListener(e -> {

            plotGenerator.plotProperties.autoScaleX = jcbAutoScaleX.isSelected();
            refresh();

        });
        plotMenu.add(jcbAutoScaleX);

        JCheckBoxMenuItem jcbAutoScaleY = new JCheckBoxMenuItem("Auto Scale Y");
        jcbAutoScaleY.setSelected(plotGenerator.plotProperties.autoScaleY);
        jcbAutoScaleY.addActionListener(e -> {

            plotGenerator.plotProperties.autoScaleY = jcbAutoScaleY.isSelected();
            refresh();

        });
        plotMenu.add(jcbAutoScaleY);

        plotMenu.add(new JSeparator());

        JCheckBoxMenuItem jcbLogX = new JCheckBoxMenuItem("Log X-Scale");
        jcbLogX.setSelected(plotGenerator.plotProperties.logX);
        JCheckBoxMenuItem jcbLogY = new JCheckBoxMenuItem("Log Y-Scale");
        jcbLogY.setSelected(plotGenerator.plotProperties.logY);
        jcbLogX.addActionListener(e -> {

            plotGenerator.plotProperties.logX = jcbLogX.isSelected();
            refresh();

        });
        jcbLogY.addActionListener(e -> {

            plotGenerator.plotProperties.logY = jcbLogY.isSelected();
            refresh();

        });
        plotMenu.add(jcbLogX);
        plotMenu.add(jcbLogY);

        jmb.add(plotMenu);

        this.setJMenuBar(jmb);
    }

    private void initComponents() {

        this.setDefaultCloseOperation(HIDE_ON_CLOSE);
        this.setContentPane(rootPanel);
        buildMenu();
        pack();
        this.setMinimumSize(new Dimension(400, 200));
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        this.setLocation(dim.width / 2 - this.getWidth() / 2, dim.height / 2 - this.getHeight() / 2);
    }
}
