
import DE_Optimizer.*;
import GUI.EAStatusWindow;
import GUI.WindowPositions;
import Helper.*;
import Helper.Plot.CFileFilter;
import Helper.Plot.DataPoint;
import Helper.Plot.PlotSeries;
import Helper.Plot.PlotWindow;
import IBAMisc.Kinematics.IBAKinematics;
import Simulator.*;
import Simulator.CalculationSetup.CalculationSetup;
import Simulator.CalculationSetup.ScreeningMode;
import Simulator.CalculationSetup.StragglingMode;
import Simulator.Detector.DetectorCalibration;
import Simulator.Detector.DetectorSetup;
import Simulator.Stopping.StoppingCalculationMode;
import Simulator.Target.*;
import IBAMisc.DepthPlotter.DepthPlotWindow;
import IBAMisc.StoppingPlotter.StoppingPlotWindow;
import IBAMisc.IBAMiscInput;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import static Simulator.FileType.*;

public class MainWindow extends JFrame implements Observer {

    final static boolean DEBUG = false;
    private final String TARGET_NOTIFICATION = "TargetModel";
    private final String FOIL_NOTIFICATION = "FoilModel";

    //------------ Global Fields -------------------------------------------------------------------------------------//
    private JPanel rootPanel, pnlSetup;
    private JTextField tfExpE0, tfExpDE0, tfExpZ1, tfExpCharge, tfExpAlpha, tfExpTheta, tFExpBeta, tfDetDE,
            tfDetCalOffset, tfDetCalFactor, tfDetSolidAngle, tf_ch_min, tf_ch_max, tfExpChargeMin, tfExpChargeMax,
            tfDetDEMin, tfDetDEMax, tfDetCalOffsetMin, tfDetCalOffsetMax, tfDetCalFactorMin, tfDetCalFactorMax;
    private JLabel lblStatus, lbl_fitness;
    private JCheckBox cb_sim_isotopes;
    private JComboBox cBoxExpM1, cBoxStopping, cBoxCompoundCorr, cBoxStraggling, cBoxScreening, cBoxChargeFraction;
    private JButton pb_show_cross_sections;
    private JButton pb_add_cross_section;
    private JButton pb_clear_cross_sections;
    private ExperimentalSetup experimentalSetup;
    private DetectorSetup detectorSetup;
    private CalculationSetup calculationSetup;
    private OutputOptions outputOptions;
    private DEParameter deParameter;
    private double[] experimentalSpectrum;
    private final TargetModel targetModel, foilModel;
    private final TargetView targetView, foilView;
    private PlotWindow spectraPlotWindow, fitnessPlotWindow;
    private StoppingPlotWindow stoppingPlotWindow;
    private DepthPlotWindow depthPlotWindow;
    private EAStatusWindow eaStatusWindow;
    private IBAKinematics ibaKinematics;
    private boolean gaRunning, blockEvents;
    private DEInputCreator deInputCreator;
    private String lastFolder, currentFileName;
    private final ClientEngine clientEngine;
    private final FitnessPlotter fitnessPlotter;
    private RutheldeSettings rutheldeSettings;
    private ReportGenerator reportGenerator;
    private String[] availableCrossSections;
    //private final Logger LOG;


    //------------ Constructor ---------------------------------------------------------------------------------------//

    public MainWindow(String args[]) {

        /*
        LOG = Logger.getLogger("Ruthelde-Logger");

        Logger parentLog = LOG.getParent();
        if (parentLog!=null&&parentLog.getHandlers().length>0) parentLog.removeHandler(parentLog.getHandlers()[0]);

        File base = new File("Log");
        String path = base.getAbsolutePath() + "/" + "log.txt";
        try{
            Handler fileHandler = new FileHandler(path, 2000, 5);
            fileHandler.setFormatter(new LogFormatter());
            LOG.addHandler(fileHandler);
        } catch (Exception ex){
            errorMsg("Could not start logger.");
        }
        */

        //StoppingParaFile_All.convert("StoppingData/SCS2024_01_GAS.json");

        String serverIP = null;
        lastFolder = null ;

        try {
            Gson gson = new Gson();
            FileReader fr = new FileReader("Settings.json");
            rutheldeSettings = gson.fromJson(fr, RutheldeSettings.class);
            serverIP = rutheldeSettings.serverAddress ;
            lastFolder = rutheldeSettings.lastFolder    ;
        } catch (Exception ex){
            errorMsg("Could not read Ruthelde settings file.");
        }

        if (serverIP.equals("") || serverIP == null) {

            ServerEngine serverEngine = new ServerEngine();
            serverEngine.start(9090);
            serverIP = "localhost";
        }

        experimentalSetup = new ExperimentalSetup();
        detectorSetup     = new DetectorSetup();
        targetModel       = new TargetModel(new Target(), TARGET_NOTIFICATION);
        targetView        = new TargetView(targetModel, "Target Configurator");
        foilModel         = new TargetModel(new Target(), FOIL_NOTIFICATION);

        foilModel.getTarget().setLayerThickness(0, 0.1d);

        foilView             = new TargetView(foilModel, "Foil Configurator");
        calculationSetup     = new CalculationSetup();
        outputOptions        = new OutputOptions();
        experimentalSpectrum = new double[calculationSetup.numberOfChannels];

        for (int i=0; i<calculationSetup.numberOfChannels; i++) experimentalSpectrum[i] = 10.0d*Math.random();

        initComponents();

        targetModel.addObserver(this);
        foilModel.addObserver(this);

        fitnessPlotter = new FitnessPlotter();

        updateIBAKinematics();

        clientEngine = new ClientEngine();
        clientEngine.addObserver(this);
        clientEngine.connect(serverIP, 9090);
        clientEngine.requestStoppingFileList();

        debugMsg("System started");

        //Load simulation file if a file name was passed via console
        if (args!=null){
            if (args.length == 1) {
                String fileName = args[0];
                loadSimulation(fileName);
            }
        }
    }

    public static void main(String args[]) {

        try {
            UIManager.setLookAndFeel("com.jtattoo.plaf.hifi.HiFiLookAndFeel");
        } catch (Exception ex) {
            System.out.println("Problem(s) with loading Look&Feel ... ");
        }

        EventQueue.invokeLater(() -> new MainWindow(args));
    }

    //------------ Update Routine ------------------------------------------------------------------------------------//

    @Override
    public void update(Observable o, Object arg) {

        if (arg.equals("SIM-RESULT")){

            SimulatorOutput simulatorOutput = clientEngine.getSimulatorOutput();

            if (simulatorOutput != null) {

                int startChannel = deParameter.startCH;
                int endChannel = deParameter.endCH;
                double[] simulatedSpectrum = simulatorOutput.spectra.get(0).data;

                double fitness = FitnessCalculator.calcFitness(detectorSetup, startChannel, endChannel, simulatedSpectrum, experimentalSpectrum);
                lbl_fitness.setText(Helper.dblToDecStr(fitness, 2));

                long time = simulatorOutput.simulationTime;
                lblStatus.setText("t=" + time + "ms");

                LinkedList<PlotSeries> plotList;

                if (outputOptions.showChannels){
                    spectraPlotWindow.getPlotGenerator().plotProperties.xAxisName = "channel";
                    DetectorCalibration emptyCalibration = new DetectorCalibration();
                    emptyCalibration.setFactor(1.0d);
                    emptyCalibration.setOffset(0.0d);
                    plotList = SimulationResultPlotter.makePlots(emptyCalibration, experimentalSpectrum, simulatorOutput.spectra);
                } else {
                    spectraPlotWindow.getPlotGenerator().plotProperties.xAxisName = "Energy (keV)";
                    plotList = SimulationResultPlotter.makePlots(detectorSetup.getCalibration(), experimentalSpectrum, simulatorOutput.spectra);
                }

                spectraPlotWindow.setPlotSeries(plotList);
                spectraPlotWindow.refresh();
            }
        }

        if (arg.equals("DE-INFO")){

            IntermediateOptimizerOutput iop = clientEngine.getIOP();

            if (iop != null){

                eaStatusWindow.ta_info.setText(iop.deStatusText);

                double bestFitness    = iop.bestFitness ;
                double averageFitness = iop.avrFitness  ;

                fitnessPlotter.addDataEntry(bestFitness, averageFitness);
                fitnessPlotWindow.setPlotSeries(fitnessPlotter.makePlots());
                fitnessPlotWindow.refresh();

                if (iop.refreshPlot){

                    SimulatorInput simulatorInput       = new SimulatorInput()            ;
                    simulatorInput.target               = iop.target                      ;
                    simulatorInput.experimentalSetup    = experimentalSetup.getDeepCopy() ;
                    simulatorInput.experimentalSetup.setCharge(iop.charge)                ;

                    if (iop.correctionFactors != null){
                        int sizeCF = iop.correctionFactors.length;
                        simulatorInput.correctionFactors = new CorrectionFactorEntry[sizeCF];
                        if (sizeCF > 0){
                            for (int i=0; i<sizeCF; i++){
                                simulatorInput.correctionFactors[i] = new CorrectionFactorEntry();
                                simulatorInput.correctionFactors[i].cF_min = calculationSetup.correctionFactors[i].cF_min;
                                simulatorInput.correctionFactors[i].cF_max = calculationSetup.correctionFactors[i].cF_max;
                                simulatorInput.correctionFactors[i].Z = calculationSetup.correctionFactors[i].Z;
                                simulatorInput.correctionFactors[i].correctionFactor = iop.correctionFactors[i];
                            }
                        }
                    }

                    simulatorInput.calculationSetup     = calculationSetup                ;
                    simulatorInput.detectorSetup        = detectorSetup.getDeepCopy()     ;
                    simulatorInput.detectorSetup.setCalibration(iop.detectorCalibration)  ;
                    double f = simulatorInput.detectorSetup.getCalibration().getFactor()  ;
                    f /= deParameter.numBins                                              ;
                    simulatorInput.detectorSetup.setCalibrationFactor(f)                  ;
                    simulatorInput.detectorSetup.setResolution(iop.resolution)            ;
                    simulatorInput.outputOptions        = outputOptions                   ;
                    //simulatorInput.experimentalSpectrum = experimentalSpectrum            ;

                    clientEngine.requestSimulation(simulatorInput);
                }
            }
        }

        if (arg.equals("DE-RESULT")){

            gaRunning = false;

            OptimizerOutput optimizerOutput = clientEngine.getOptimizerOutput();

            if (optimizerOutput != null) {

                refreshInputs(optimizerOutput);
                updateSimulation();
            }

        }

        if (arg.equals("STOPPING-FILE-LIST")){

            String[] fileNames = clientEngine.getStoppingFileList();
            int length = fileNames.length;
            fileNames[length-1] = fileNames[length-1].substring(0,fileNames[length-1].length()-2);

            DefaultComboBoxModel dml = new DefaultComboBoxModel();
            dml.addElement("Ziegler-Biersack (SRIM-98)");
            for (String fileName : fileNames) dml.addElement(fileName);
            cBoxStopping.setModel(dml);

            clientEngine.requestCrossSectionFileList();
        }

        if (arg.equals("CROSS-SECTION-FILE-LIST")){

            availableCrossSections = clientEngine.getCrossSectionFileList();
        }

        if (arg.equals(TARGET_NOTIFICATION)) {

            updateOpenPlotWindows();
        }

        if (arg.equals(FOIL_NOTIFICATION)) {

            updateOpenPlotWindows();
        }
    }

    private void updateSimulation() {

        if (!gaRunning) {

            SimulatorInput simulatorInput       = new SimulatorInput()    ;
            simulatorInput.target               = targetModel.getTarget() ;
            simulatorInput.experimentalSetup    = experimentalSetup       ;
            simulatorInput.calculationSetup     = calculationSetup        ;
            simulatorInput.detectorSetup        = detectorSetup           ;
            simulatorInput.outputOptions        = outputOptions           ;

            if (calculationSetup.correctionFactors != null){
                int sizeCF = calculationSetup.correctionFactors.length;
                simulatorInput.correctionFactors = new CorrectionFactorEntry[sizeCF];
                if (sizeCF > 0){
                    for (int i=0; i<sizeCF; i++){
                        simulatorInput.correctionFactors[i] = new CorrectionFactorEntry();
                        simulatorInput.correctionFactors[i].cF_min = calculationSetup.correctionFactors[i].cF_min;
                        simulatorInput.correctionFactors[i].cF_max = calculationSetup.correctionFactors[i].cF_max;
                        simulatorInput.correctionFactors[i].Z = calculationSetup.correctionFactors[i].Z;
                        simulatorInput.correctionFactors[i].correctionFactor = calculationSetup.correctionFactors[i].correctionFactor;
                    }
                }
            }

            clientEngine.requestSimulation(simulatorInput);
        }
    }

    private void updateStoppingCalculation() {

        IBAMiscInput input = new IBAMiscInput();

        input.projectile = experimentalSetup.getProjectile();
        input.target = targetModel.getTarget();
        input.stoppingPowerCalculationMode = calculationSetup.stoppingPowerCalculationMode;
        input.compoundCalculationMode = calculationSetup.compoundCalculationMode;

        if (calculationSetup.correctionFactors != null){
            int sizeCF = calculationSetup.correctionFactors.length;
            input.correctionFactors = new CorrectionFactorEntry[sizeCF];
            if (sizeCF > 0){
                for (int i=0; i<sizeCF; i++){
                    input.correctionFactors[i] = new CorrectionFactorEntry();
                    input.correctionFactors[i].cF_min = calculationSetup.correctionFactors[i].cF_min;
                    input.correctionFactors[i].cF_max = calculationSetup.correctionFactors[i].cF_max;
                    input.correctionFactors[i].Z = calculationSetup.correctionFactors[i].Z;
                    input.correctionFactors[i].correctionFactor = calculationSetup.correctionFactors[i].correctionFactor;
                }
            }
        }

        String path = null;
        String relativePath = calculationSetup.stoppingData;

        if (relativePath != null && !relativePath.equals("")) {

            File base = new File("StoppingData");
            path = base.getAbsolutePath() + "/" + relativePath;
        }

        input.stoppingData = path;

        stoppingPlotWindow.setInput(input);

    }

    private void updateDepthCalculation() {

        IBAMiscInput input = new IBAMiscInput();

        input.projectile = experimentalSetup.getProjectile();
        input.target = targetModel.getTarget();
        input.stoppingPowerCalculationMode = calculationSetup.stoppingPowerCalculationMode;
        input.compoundCalculationMode = calculationSetup.compoundCalculationMode;

        if (calculationSetup.correctionFactors != null){
            int sizeCF = calculationSetup.correctionFactors.length;
            input.correctionFactors = new CorrectionFactorEntry[sizeCF];
            if (sizeCF > 0){
                for (int i=0; i<sizeCF; i++){
                    input.correctionFactors[i] = new CorrectionFactorEntry();
                    input.correctionFactors[i].cF_min = calculationSetup.correctionFactors[i].cF_min;
                    input.correctionFactors[i].cF_max = calculationSetup.correctionFactors[i].cF_max;
                    input.correctionFactors[i].Z = calculationSetup.correctionFactors[i].Z;
                    input.correctionFactors[i].correctionFactor = calculationSetup.correctionFactors[i].correctionFactor;
                }
            }
        }

        String path = null;
        String relativePath = calculationSetup.stoppingData;

        if (relativePath != null && !relativePath.equals("")) {

            File base = new File("StoppingData");
            path = base.getAbsolutePath() + "/" + relativePath;
        }

        input.stoppingData = path;

        depthPlotWindow.setInput(input);
    }

    private void updateIBAKinematics(){

        IBAMiscInput input = new IBAMiscInput();

        input.projectile = experimentalSetup.getProjectile();
        input.target = targetModel.getTarget();
        input.foil = foilModel.getTarget();
        input.stoppingPowerCalculationMode = calculationSetup.stoppingPowerCalculationMode;
        input.compoundCalculationMode = calculationSetup.compoundCalculationMode;
        input.screeningMode = calculationSetup.screeningMode;
        input.theta = experimentalSetup.getTheta();
        input.alpha = experimentalSetup.getAlpha();

        if (calculationSetup.correctionFactors != null){
            int sizeCF = calculationSetup.correctionFactors.length;
            input.correctionFactors = new CorrectionFactorEntry[sizeCF];
            if (sizeCF > 0){
                for (int i=0; i<sizeCF; i++){
                    input.correctionFactors[i] = new CorrectionFactorEntry();
                    input.correctionFactors[i].cF_min = calculationSetup.correctionFactors[i].cF_min;
                    input.correctionFactors[i].cF_max = calculationSetup.correctionFactors[i].cF_max;
                    input.correctionFactors[i].Z = calculationSetup.correctionFactors[i].Z;
                    input.correctionFactors[i].correctionFactor = calculationSetup.correctionFactors[i].correctionFactor;
                }
            }
        }

        String path = null;
        String relativePath = calculationSetup.stoppingData;

        if (relativePath != null && !relativePath.equals("")) {

            File base = new File("StoppingData");
            path = base.getAbsolutePath() + "/" + relativePath;
        }

        input.stoppingData = path;

        ibaKinematics.setInput(input);
    }

    private void updateOpenPlotWindows() {
        if (spectraPlotWindow.isVisible()) updateSimulation();
        if (stoppingPlotWindow.isVisible()) updateStoppingCalculation();
        if (depthPlotWindow.isVisible()) updateDepthCalculation();
        if (ibaKinematics.isVisible()) updateIBAKinematics();
    }


    //------------ Load & Save ---------------------------------------------------------------------------------------//

    private void readDataFile(FileType fileType, File file) {

        File spectrumFile = null;

        if (file == null) {
            final JFileChooser fc;
            if (lastFolder != null) fc = new JFileChooser(lastFolder);
            else fc = new JFileChooser();
            int returnVal = fc.showOpenDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                spectrumFile = fc.getSelectedFile();
                lastFolder = fc.getSelectedFile().getParent();
                setLastFolder(lastFolder);
                currentFileName = spectrumFile.getParent() + '/' + spectrumFile.getName();
                spectraPlotWindow.setTitle("Spectra - " + currentFileName);
            }
        } else spectrumFile = file;

        if (spectrumFile != null) {

            debugMsg("Reading data file: " + spectrumFile.getParent() + "/" + spectrumFile.getName());

            experimentalSpectrum = null;

            switch (fileType) {

                case ONE_COLUMN_ASCII:
                    experimentalSpectrum = DataFileReader.readASCIIFileOneColumn(spectrumFile);
                    break;

                case TWO_COLUMN_ASCII:
                    experimentalSpectrum = DataFileReader.readASCIIFileTwoColumn(spectrumFile);
                    break;

                case IBC_RBS:
                    experimentalSpectrum = DataFileReader.readIBCDataFile(spectrumFile);
                    break;

                case IBC_3MV_SINGLE:
                    Gson gson = new Gson();
                    try {
                        FileReader fr = new FileReader(spectrumFile);
                        Spectrum_3MV spectrum = gson.fromJson(fr, Spectrum_3MV.class);
                        experimentalSpectrum = new double[spectrum.length];

                        for (int i = 0; i < experimentalSpectrum.length; i++) {
                            experimentalSpectrum[i] = spectrum.data[1][i];
                        }
                    } catch (Exception ex) {
                        errorMsg("Could not read IBC_3MV_SINGLE file.");
                    }
                    break;

                case IBC_3MV_MULTI:

                    String indexStr = JOptionPane.showInputDialog(
                            this,
                            "Select Spectrum",
                            "Select Spectrum",
                            JOptionPane.QUESTION_MESSAGE
                    );

                    if (indexStr != null) {
                        try {
                            int index = Integer.parseInt(indexStr);
                            if (index >= 1) {
                                experimentalSpectrum = DataFileReader.read3MVAllDataFile(spectrumFile, index);
                            } else {
                                experimentalSpectrum = DataFileReader.read3MVAllDataFile(spectrumFile, 1);
                            }
                        } catch (Exception ex) {
                            errorMsg("Could not load IBC_3MV_MULTI file.");
                        }
                    }


                    break;

                case IMEC:
                    experimentalSpectrum = DataFileReader.readIMECDataFile(spectrumFile);
                    break;

                case IBA_SIM:
                    experimentalSpectrum = DataFileReader.readExpSpectrumFromSimulationFile(spectrumFile);
                    break;
            }

            int ch_min = Integer.parseInt(tf_ch_min.getText());
            int ch_max = Integer.parseInt(tf_ch_max.getText());

            int length = experimentalSpectrum.length;

            if (ch_min >= length) {
                ch_min = 0;
                tf_ch_min.setText("" + ch_min);
            }

            if (ch_max >= length) {
                ch_min = 0;
                ch_max = length - 1;
                tf_ch_min.setText("" + ch_min);
                tf_ch_max.setText("" + ch_max);
            }

            calculationSetup.numberOfChannels = length;

            updateSimulation();
        }
    }

    private void saveSimulation(File file) {

        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        String fileFormat = "json";

        if (file == null) {

            final JFileChooser fc;
            if (lastFolder != null) fc = new JFileChooser(lastFolder);
            else fc = new JFileChooser();

            fc.addChoosableFileFilter(new CFileFilter("json", "Ruthelde Simulation File (*.json)"));
            fc.addChoosableFileFilter(new CFileFilter("xml", "IBA Data Format (*.xml)"));
            fc.addChoosableFileFilter(new CFileFilter("idf", "IBA Data Format (*.idf)"));
            fc.addChoosableFileFilter(new CFileFilter("xnra", "SIMNRA file (*.xnra)"));
            fc.setAcceptAllFileFilterUsed(false);

            int returnVal = fc.showSaveDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {

                CFileFilter cf = (CFileFilter) fc.getFileFilter();

                file = fc.getSelectedFile();

                String dir = file.getParentFile().toString();
                String name = file.getName();
                if (name.contains(".")) name = name.split("\\.")[0];
                name = name + "." + cf.getFileExt();
                file = new File(dir + "/" + name);

                fileFormat = cf.getFileExt();
                lastFolder = fc.getSelectedFile().getParent();
                setLastFolder(lastFolder);
            }
        }

        if (file != null) {

            Target target = targetModel.getTarget();
            //Target foil = foilModel.getTarget(); //TODO: Include Foil

            WindowPositions wp = new WindowPositions();

            wp.spectrumWindow.visible = spectraPlotWindow.isVisible();
            wp.spectrumWindow.height = spectraPlotWindow.getHeight();
            wp.spectrumWindow.width = spectraPlotWindow.getWidth();
            wp.spectrumWindow.x = spectraPlotWindow.getX();
            wp.spectrumWindow.y = spectraPlotWindow.getY();

            wp.stoppingPlotWindow.visible = stoppingPlotWindow.isVisible();
            wp.stoppingPlotWindow.height = stoppingPlotWindow.getHeight();
            wp.stoppingPlotWindow.width = stoppingPlotWindow.getWidth();
            wp.stoppingPlotWindow.x = stoppingPlotWindow.getX();
            wp.stoppingPlotWindow.y = stoppingPlotWindow.getY();

            wp.depthPlotWindow.visible = depthPlotWindow.isVisible();
            wp.depthPlotWindow.height = depthPlotWindow.getHeight();
            wp.depthPlotWindow.width = depthPlotWindow.getWidth();
            wp.depthPlotWindow.x = depthPlotWindow.getX();
            wp.depthPlotWindow.y = depthPlotWindow.getY();

            wp.eaFitnessWindow.visible = fitnessPlotWindow.isVisible();
            wp.eaFitnessWindow.height = fitnessPlotWindow.getHeight();
            wp.eaFitnessWindow.width = fitnessPlotWindow.getWidth();
            wp.eaFitnessWindow.x = fitnessPlotWindow.getX();
            wp.eaFitnessWindow.y = fitnessPlotWindow.getY();

            wp.eaStatusWindow.visible = eaStatusWindow.isVisible();
            wp.eaStatusWindow.height = eaStatusWindow.getHeight();
            wp.eaStatusWindow.width = eaStatusWindow.getWidth();
            wp.eaStatusWindow.x = eaStatusWindow.getX();
            wp.eaStatusWindow.y = eaStatusWindow.getY();

            /*
            LinkedList<PlotSeries> plotSeries = spectraPlotWindow.getPlotSeries();
            LinkedList<Spectrum> plots = new LinkedList<>();

            for (PlotSeries ps : plotSeries){
                plots.add(ps.toSpectrum());
            }
            */

            DataFile dataFile = new DataFile(target, experimentalSetup, calculationSetup, detectorSetup, deParameter,
                    outputOptions, experimentalSpectrum, currentFileName, wp);

            switch (fileFormat) {

                case "json":

                    try {
                        FileWriter fw = new FileWriter(file);
                        gson.toJson(dataFile, fw);
                        fw.flush();
                        fw.close();
                    } catch (Exception ex) {
                        errorMsg("Could not read json data file.");
                    }

                    break;

                case "xml":
                case "idf":
                case "xnra":



                    LinkedList<DataPoint> dps = spectraPlotWindow.getPlotSeries().get(0).data;
                    double[] simulatedSpectrum = new double[dps.size()];
                    int i = 0;
                    for(DataPoint dataPoint : dps){
                        simulatedSpectrum[i] = dataPoint.y;
                        i++;
                    }

                    IDF_Converter.write_To_IDF_File(dataFile, file, simulatedSpectrum);

                    break;
            }
        }
    }

    private void loadSimulation(String fileName) {

        boolean result = false;

        File file = null;
        String fileFormat = "json";

        try {

            if (fileName == null) {

                final JFileChooser fc;
                if (lastFolder != null) fc = new JFileChooser(lastFolder);
                else fc = new JFileChooser();
                fc.addChoosableFileFilter(new CFileFilter("json", "Ruthelde Simulation File (*.json)"));
                fc.addChoosableFileFilter(new CFileFilter("xml", "IBA Data Format (*.xml)"));
                fc.addChoosableFileFilter(new CFileFilter("idf", "IBA Data Format (*.idf)"));
                fc.addChoosableFileFilter(new CFileFilter("xnra", "SIMNRA file (*.xnra)"));
                fc.setAcceptAllFileFilterUsed(false);

                int returnVal = fc.showOpenDialog(this);
                if (returnVal == JFileChooser.APPROVE_OPTION) {

                    CFileFilter cf = (CFileFilter) fc.getFileFilter();
                    fileFormat = cf.getFileExt();
                    file = fc.getSelectedFile();
                }

            } else {

                file = new File(fileName);
            }

            if (file != null) {

                switch (fileFormat) {

                    case "json":

                        Gson gson = new Gson();
                        lastFolder = file.getParent();

                        FileReader fr = new FileReader(file);
                        DataFile df = gson.fromJson(fr, DataFile.class);

                        if (df != null) {

                            result = true;

                            experimentalSpectrum = df.experimentalSpectrum;
                            experimentalSetup    = df.experimentalSetup.getDeepCopy();
                            detectorSetup        = df.detectorSetup.getDeepCopy();
                            calculationSetup     = df.calculationSetup.getDeepCopy();
                            deParameter          = df.deParameter;

                            if (df.calculationSetup.crossSectionData != null && df.calculationSetup.crossSectionData.length > 0) {
                                for (String path : df.calculationSetup.crossSectionData)
                                    KinematicsCalculator.addCrossSectionData(path);
                            }

                            tfExpE0.setText(Helper.dblToDecStr(df.experimentalSetup.getE0(), 2));
                            tfExpDE0.setText(Helper.dblToDecStr(df.experimentalSetup.getDeltaE0(), 2));
                            tfExpZ1.setText(Integer.toString(df.experimentalSetup.getProjectile().getZ()));
                            fillCBoxExpM1(df.experimentalSetup.getProjectile().getM());
                            updateChargeValues();
                            updateDetDE();
                            updateCalibration();
                            tfDetSolidAngle.setText(Helper.dblToDecStr(df.detectorSetup.getSolidAngle(), 2));
                            tfExpAlpha.setText(Helper.dblToDecStr(df.experimentalSetup.getAlpha(), 2));
                            tFExpBeta.setText(Helper.dblToDecStr(df.experimentalSetup.getBeta(), 2));
                            tfExpTheta.setText(Helper.dblToDecStr(df.experimentalSetup.getTheta(), 2));

                            blockEvents = true;
                            buildMenu();

                            if (calculationSetup.stoppingPowerCalculationMode.equals(StoppingCalculationMode.ZB)){

                                cBoxStopping.setSelectedIndex(0);

                            } else {

                                DefaultComboBoxModel model = (DefaultComboBoxModel) cBoxStopping.getModel();
                                int size = model.getSize();
                                boolean foundStoppingParameterFile = false;
                                for (int i=0; i<size; i++) {
                                    if (calculationSetup.stoppingData.equals(model.getElementAt(i).toString())){
                                        cBoxStopping.setSelectedIndex(i);
                                        foundStoppingParameterFile = true;
                                        break;
                                    }
                                }
                                if (!foundStoppingParameterFile) {
                                    calculationSetup.stoppingPowerCalculationMode = StoppingCalculationMode.ZB;
                                    cBoxStopping.setSelectedIndex(0);
                                }
                            }

                            switch (calculationSetup.stragglingMode){

                                case NONE:
                                    cBoxStraggling.setSelectedIndex(0);
                                    break;

                                case BOHR:
                                    cBoxStraggling.setSelectedIndex(1);
                                    break;

                                case CHU:
                                    cBoxStraggling.setSelectedIndex(2);
                                    break;
                            }

                            switch (calculationSetup.screeningMode){

                                case NONE:
                                    cBoxScreening.setSelectedIndex(0);
                                    break;

                                case LECUYER:
                                    cBoxScreening.setSelectedIndex(1);
                                    break;

                                case ANDERSON:
                                    cBoxScreening.setSelectedIndex(2);
                                    break;
                            }

                            cb_sim_isotopes.setSelected(calculationSetup.simulateIsotopes);

                            blockEvents = false;

                            //NOTE: Just for compatibility with older versions
                            if (df.outputOptions != null) outputOptions = df.outputOptions; else outputOptions = new OutputOptions();

                            if (deParameter.startCH >= df.experimentalSpectrum.length - 1) {

                                deParameter.startCH = df.experimentalSpectrum.length - 2;
                                infoMsg("Parameter *Start_Channel to high. Changed to " + deParameter.startCH);
                            }

                            if (deParameter.endCH >= df.experimentalSpectrum.length - 1) {

                                deParameter.endCH = df.experimentalSpectrum.length - 2;
                                infoMsg("Parameter *End_Channel to high. Changed to " + deParameter.endCH);
                            }

                            if (deParameter.startCH >= deParameter.endCH) {

                                deParameter.startCH = deParameter.endCH - 2;
                                infoMsg("Parameter *Start_Channel to high. Changed to " + deParameter.startCH);
                            }

                            tf_ch_min.setText("" + (int) deParameter.startCH);
                            tf_ch_max.setText("" + (int) deParameter.endCH);

                            targetModel.setTarget(df.target.getDeepCopy());
                            targetView.updateTarget();

                            //TODO: Include Foil

                            spectraPlotWindow.setLocation(new Point((int) df.windowPositions.spectrumWindow.x, (int) df.windowPositions.spectrumWindow.y));
                            spectraPlotWindow.setSize(new Dimension((int) df.windowPositions.spectrumWindow.width, (int) df.windowPositions.spectrumWindow.height));
                            spectraPlotWindow.setVisible(df.windowPositions.spectrumWindow.visible);

                            depthPlotWindow.setLocation(new Point((int) df.windowPositions.depthPlotWindow.x, (int) df.windowPositions.depthPlotWindow.y));
                            depthPlotWindow.setSize(new Dimension((int) df.windowPositions.depthPlotWindow.width, (int) df.windowPositions.depthPlotWindow.height));
                            depthPlotWindow.setVisible(df.windowPositions.depthPlotWindow.visible);

                            stoppingPlotWindow.setLocation(new Point((int) df.windowPositions.stoppingPlotWindow.x, (int) df.windowPositions.stoppingPlotWindow.y));
                            stoppingPlotWindow.setSize(new Dimension((int) df.windowPositions.stoppingPlotWindow.width, (int) df.windowPositions.stoppingPlotWindow.height));
                            stoppingPlotWindow.setVisible(df.windowPositions.stoppingPlotWindow.visible);

                            eaStatusWindow.setLocation(new Point((int) df.windowPositions.eaStatusWindow.x, (int) df.windowPositions.eaStatusWindow.y));
                            eaStatusWindow.setSize(new Dimension((int) df.windowPositions.eaStatusWindow.width, (int) df.windowPositions.eaStatusWindow.height));
                            eaStatusWindow.setVisible(df.windowPositions.eaStatusWindow.visible);

                            fitnessPlotWindow.setLocation(new Point((int) df.windowPositions.eaFitnessWindow.x, (int) df.windowPositions.eaFitnessWindow.y));
                            fitnessPlotWindow.setSize(new Dimension((int) df.windowPositions.eaFitnessWindow.width, (int) df.windowPositions.eaFitnessWindow.height));
                            fitnessPlotWindow.setVisible(df.windowPositions.eaFitnessWindow.visible);
                        }

                        break;

                    case "xml":
                    case "idf":
                    case "xnra":

                        BufferedReader inputBuffer = new BufferedReader(new FileReader(file));
                        String currentLine;
                        boolean error = false;

                        StringBuilder sb = new StringBuilder();
                        while ((currentLine = inputBuffer.readLine()) != null) {
                            sb.append(currentLine + "\n");
                        }
                        String text = sb.toString();

                        int start = text.indexOf("<layeredstructure>");
                        int end = text.indexOf("</layeredstructure>");

                        String subStr = text.substring(start, end);

                        start = subStr.indexOf("<nlayers>") + 9;
                        end = subStr.indexOf("</nlayers>");

                        String numStr = subStr.substring(start, end);

                        int numLayers = Integer.parseInt(numStr);

                        break;
                }

                currentFileName = file.getName();
                spectraPlotWindow.setTitle("Spectra - " + currentFileName);
                lastFolder = file.getParent();
                setLastFolder(lastFolder);

                updateOpenPlotWindows();
            }

        } catch (Exception ex) {
            result = false;
            ex.printStackTrace();
        }

        if (!result) errorMsg("Could not load simulation file.");
    }

    private void saveReport(){

        JTextField tf_dateTime = new JTextField();
        JTextField tf_sample = new JTextField();
        JTextField tf_owner = new JTextField();
        JTextField tf_measured = new JTextField();
        JTextArea tf_remarks = new JTextArea();

        Object[] message = {
                "Date / Time:", tf_dateTime,
                "Sample name:", tf_sample,
                "Sample owner:", tf_owner,
                "Measured by:", tf_measured,
                "Remarks:", tf_remarks
        };

        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateTime = formatter.format(date);

        tf_dateTime.setText(dateTime);
        tf_remarks.setText("\n\n\n\n\n\n");

        if (reportGenerator != null){
            tf_sample.setText(reportGenerator.sampleName);
            tf_owner.setText(reportGenerator.sampleOwner);
            tf_measured.setText(reportGenerator.measuredBy);
            tf_remarks.setText(reportGenerator.remarks);
        }

        int option = JOptionPane.showConfirmDialog(null, message, "Report header information", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {

            reportGenerator = new ReportGenerator();
            reportGenerator.dateTime = tf_dateTime.getText();
            reportGenerator.sampleName = tf_sample.getText();
            reportGenerator.sampleOwner = tf_owner.getText();
            reportGenerator.measuredBy = tf_measured.getText();
            reportGenerator.remarks = tf_remarks.getText();
            reportGenerator.es = experimentalSetup;
            reportGenerator.ds = detectorSetup;
            reportGenerator.target = targetModel.getTarget().getDeepCopy();
            reportGenerator.plotWindow = spectraPlotWindow;
            reportGenerator.generateReport();
        }
    }

    private void makeExpSpectrum() {

        Random rand = new Random();

        int length = spectraPlotWindow.getPlotSeries().getFirst().data.size();
        double[] experimentalSpectrum = new double[length];

        for (int i = 0; i < length; i++) {

            double simValue = spectraPlotWindow.getPlotSeries().getFirst().data.get(i).y;

            double sqrt = Math.sqrt(simValue);
            double simExpValue = sqrt * rand.nextGaussian() + simValue;
            experimentalSpectrum[i] = simExpValue;
        }

        this.experimentalSpectrum = experimentalSpectrum;
        updateSimulation();
    }


    private void setLastFolder(String lastFolder) {

        spectraPlotWindow.setLastFolder(lastFolder);
        depthPlotWindow.setLastFolder(lastFolder);
        stoppingPlotWindow.setLastFolder(lastFolder);

        targetView.setLastFolder(lastFolder);
        foilView.setLastFolder(lastFolder);

        rutheldeSettings.lastFolder = lastFolder;
        writeSettingsFile();
    }

    private void writeSettingsFile(){

        try {
            FileWriter fw = new FileWriter("Settings.json");
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(rutheldeSettings, fw);
            fw.flush();
            fw.close();
        } catch (Exception ex) {
            errorMsg("Could not write Ruthelde settings file.");
        }
    }



    //------------ Actions for "Experimental Setup" ------------------------------------------------------------------//

    private void setExpZ1() {

        int Z1 = experimentalSetup.getProjectile().getZ();
        int newZ1 = Z1;
        boolean changeIt = false;
        Element element = new Element();

        try {
            newZ1 = Integer.parseInt(tfExpZ1.getText());
            if (newZ1 != Z1) changeIt = true;
        } catch (NumberFormatException ex) {
            if (element.setAtomicNumberByName(tfExpZ1.getText())) {
                newZ1 = element.getAtomicNumber();
                if (newZ1 != Z1) changeIt = true;
            }
        }

        if (changeIt) {
            experimentalSetup.getProjectile().setZ(newZ1);
            fillCBoxExpM1(0.0f);
        } else {
            blockEvents = true;
            tfExpZ1.setText(Integer.toString(experimentalSetup.getProjectile().getZ()));
            blockEvents = false;
        }

    }

    private void fillCBoxExpM1(double m) {

        DefaultComboBoxModel lm = (DefaultComboBoxModel) cBoxExpM1.getModel();
        blockEvents = true;
        lm.removeAllElements();
        LinkedList<String> ll = new LinkedList<String>();

        int Z1 = experimentalSetup.getProjectile().getZ();

        Element element = new Element();
        element.setAtomicNumber(Z1);

        if (m == 0.0f) {
            double highest = 0.0f;
            for (Isotope isotope : element.getIsotopeList()) {
                if (isotope.getAbundance() > highest) {
                    highest = isotope.getAbundance();
                    m = isotope.getMass();
                }
            }
        }

        int index = 0;
        int bestIndex = 0;
        double delta = 500.0f;

        for (Isotope isotope : element.getIsotopeList()) {
            String entry = Helper.dblToDecStr(isotope.getMass(), 3) + " (" + Helper.dblToDecStr(isotope.getAbundance(), 2) + ")";
            if (!ll.contains(entry)) {
                ll.add(entry);
                lm.addElement(entry);
                double tempDelta = Math.abs(isotope.getMass() - m);
                if (tempDelta <= delta) {
                    bestIndex = index;
                    delta = tempDelta;
                }
                index++;
            }
        }

        blockEvents = false;
        cBoxExpM1.setSelectedIndex(bestIndex);
    }

    private void setExpE0() {

        double oldE0 = experimentalSetup.getE0();
        Double E0;
        try {
            E0 = Double.parseDouble(tfExpE0.getText());
            if (E0 != oldE0) {
                experimentalSetup.setE0(E0);
                experimentalSetup.getProjectile().setE(E0);
                updateOpenPlotWindows();
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        E0 = experimentalSetup.getE0();
        tfExpE0.setText(Helper.dblToDecStr(E0, 2));
        blockEvents = false;
    }

    private void setExpDE0() {

        double oldDeltaE0 = experimentalSetup.getDeltaE0();
        double deltaE0;
        try {
            deltaE0 = Double.parseDouble(tfExpDE0.getText());
            if (deltaE0 != oldDeltaE0) {
                experimentalSetup.setDeltaE0(deltaE0);
                updateOpenPlotWindows();
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        deltaE0 = experimentalSetup.getDeltaE0();
        tfExpDE0.setText(Helper.dblToDecStr(deltaE0, 2));
        blockEvents = false;
    }

    private void setExpAlpha() {

        double oldAlpha = experimentalSetup.getAlpha();
        double alpha;
        try {
            alpha = Double.parseDouble(tfExpAlpha.getText());
            if (alpha != oldAlpha) {
                experimentalSetup.setAlpha(alpha);
                updateOpenPlotWindows();
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        alpha = experimentalSetup.getAlpha();
        tfExpAlpha.setText(Helper.dblToDecStr(alpha, 2));
        tFExpBeta.setText(Helper.dblToDecStr(experimentalSetup.getBeta(), 2));
        blockEvents = false;
    }

    private void setExpTheta() {

        double oldTheta = experimentalSetup.getTheta();
        double theta;
        try {
            theta = Double.parseDouble(tfExpTheta.getText());
            if (theta != oldTheta) {
                experimentalSetup.setTheta(theta);
                updateOpenPlotWindows();
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        theta = experimentalSetup.getTheta();
        tfExpTheta.setText(Helper.dblToDecStr(theta, 2));
        tFExpBeta.setText(Helper.dblToDecStr(experimentalSetup.getBeta(), 2));
        blockEvents = false;
    }

    private void updateChargeValues() {
        double charge = experimentalSetup.getCharge();
        double minCharge = experimentalSetup.getMinCharge();
        double maxCharge = experimentalSetup.getMaxCharge();

        tfExpCharge.setText(Helper.dblToDecStr(charge, 3));
        tfExpChargeMin.setText(Helper.dblToDecStr(minCharge, 3));
        tfExpChargeMax.setText(Helper.dblToDecStr(maxCharge, 3));
    }

    private void setExpCharge() {

        double oldCharge = experimentalSetup.getCharge();
        double charge;
        try {
            charge = Double.parseDouble(tfExpCharge.getText());
            if (charge != oldCharge) {
                experimentalSetup.setCharge(charge);
                if (!blockEvents) updateOpenPlotWindows();
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        updateChargeValues();
        blockEvents = false;
    }

    private void setExpMinCharge() {

        double oldValue = experimentalSetup.getMinCharge();
        double minCharge;
        try {
            minCharge = Double.parseDouble(tfExpChargeMin.getText());
            if (minCharge != oldValue) {
                experimentalSetup.setMinCharge(minCharge);
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        updateChargeValues();
        blockEvents = false;
    }

    private void setExpMaxCharge() {

        double oldValue = experimentalSetup.getMaxCharge();
        double maxCharge;
        try {
            maxCharge = Double.parseDouble(tfExpChargeMax.getText());
            if (maxCharge != oldValue) {
                experimentalSetup.setMaxCharge(maxCharge);
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        updateChargeValues();
        blockEvents = false;
    }

    //------------ Actions for "Detector Setup" ----------------------------------------------------------------------//

    private void setTfDetSolidAngle() {

        double oldSolidAngle = detectorSetup.getSolidAngle();
        Double solidAngle;
        try {
            solidAngle = Double.parseDouble(tfDetSolidAngle.getText());
            if (solidAngle != oldSolidAngle) {
                detectorSetup.setSolidAngle(solidAngle);
                updateOpenPlotWindows();
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        solidAngle = detectorSetup.getSolidAngle();
        tfDetSolidAngle.setText(Helper.dblToDecStr(solidAngle, 2));
        blockEvents = false;
    }

    private void updateDetDE() {

        double res = detectorSetup.getResolution();
        double minRes = detectorSetup.getMinRes();
        double maxRes = detectorSetup.getMaxRes();

        tfDetDE.setText(Helper.dblToDecStr(res, 4));
        tfDetDEMin.setText(Helper.dblToDecStr(minRes, 4));
        tfDetDEMax.setText(Helper.dblToDecStr(maxRes, 4));
    }

    private void updateCalibration() {

        double factor = detectorSetup.getCalibration().getFactor();
        double factorMin = detectorSetup.getCalibration().getFactorMin();
        double factorMax = detectorSetup.getCalibration().getFactorMax();
        double offset = detectorSetup.getCalibration().getOffset();
        double offsetMin = detectorSetup.getCalibration().getOffsetMin();
        double offsetMax = detectorSetup.getCalibration().getOffsetMax();

        tfDetCalFactor.setText(Helper.dblToDecStr(factor, 4));
        tfDetCalFactorMin.setText(Helper.dblToDecStr(factorMin, 4));
        tfDetCalFactorMax.setText(Helper.dblToDecStr(factorMax, 4));
        tfDetCalOffset.setText(Helper.dblToDecStr(offset, 2));
        tfDetCalOffsetMin.setText(Helper.dblToDecStr(offsetMin, 2));
        tfDetCalOffsetMax.setText(Helper.dblToDecStr(offsetMax, 2));
    }

    private void setDetDE() {

        double oldDE = detectorSetup.getResolution();
        Double dE;
        try {
            dE = Double.parseDouble(tfDetDE.getText());
            if (dE != oldDE) {
                detectorSetup.setResolution(dE);
                updateOpenPlotWindows();
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        updateDetDE();
        blockEvents = false;
    }

    private void setDetDEMin() {

        double oldValue = detectorSetup.getMinRes();
        Double minDE;
        try {
            minDE = Double.parseDouble(tfDetDEMin.getText());
            if (minDE != oldValue) {
                detectorSetup.setMinRes(minDE);
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        updateDetDE();
        blockEvents = false;
    }

    private void setDetDEMax() {

        double oldValue = detectorSetup.getMaxRes();
        Double maxDE;
        try {
            maxDE = Double.parseDouble(tfDetDEMax.getText());
            if (maxDE != oldValue) {
                detectorSetup.setMaxRes(maxDE);
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        updateDetDE();
        blockEvents = false;
    }

    private void setDetFactor() {

        double oldA = detectorSetup.getCalibration().getFactor();
        Double a;
        try {
            a = Double.parseDouble(tfDetCalFactor.getText());
            if (a != oldA) {
                detectorSetup.getCalibration().setFactor(a);
                updateOpenPlotWindows();
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        updateCalibration();
        blockEvents = false;
    }

    private void setDetFactorMin() {

        double oldValue = detectorSetup.getCalibration().getFactorMin();
        Double newValue;
        try {
            newValue = Double.parseDouble(tfDetCalFactorMin.getText());
            if (newValue != oldValue) {
                detectorSetup.getCalibration().setFactorMin(newValue);
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        updateCalibration();
        blockEvents = false;
    }

    private void setDetFactorMax() {

        double oldValue = detectorSetup.getCalibration().getFactorMax();
        Double newValue;
        try {
            newValue = Double.parseDouble(tfDetCalFactorMax.getText());
            if (newValue != oldValue) {

                detectorSetup.getCalibration().setFactorMax(newValue);
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        updateCalibration();
        blockEvents = false;
    }

    private void setDetOffset() {

        double oldOffset = detectorSetup.getCalibration().getOffset();
        Double offset;
        try {
            offset = Double.parseDouble(tfDetCalOffset.getText());
            if (offset != oldOffset) {
                detectorSetup.getCalibration().setOffset(offset);
                updateOpenPlotWindows();
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        updateCalibration();
        blockEvents = false;
    }

    private void setDetOffsetMin() {

        double oldValue = detectorSetup.getCalibration().getOffsetMin();
        Double newValue;
        try {
            newValue = Double.parseDouble(tfDetCalOffsetMin.getText());
            if (newValue != oldValue) {
                detectorSetup.getCalibration().setOffsetMin(newValue);
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        updateCalibration();
        blockEvents = false;
    }

    private void setDetOffsetMax() {

        double oldValue = detectorSetup.getCalibration().getOffsetMax();
        Double newValue;
        try {
            newValue = Double.parseDouble(tfDetCalOffsetMax.getText());
            if (newValue != oldValue) {
                detectorSetup.getCalibration().setOffsetMax(newValue);
            }
        } catch (NumberFormatException ex) {
        }

        blockEvents = true;
        updateCalibration();
        blockEvents = false;
    }

    //------------ Actions for "Calculation Setup" -------------------------------------------------------------------//

    private void setStragglingMode(){

        int selection = cBoxStraggling.getSelectedIndex();
        switch (selection) {

            case 0:
                calculationSetup.stragglingMode = StragglingMode.NONE;
                break;

            case 1:
                calculationSetup.stragglingMode = StragglingMode.BOHR;
                break;

            case 2:
                calculationSetup.stragglingMode = StragglingMode.CHU;
                break;
        }
    }

    private void setScreeningMode(){

        int selection = cBoxScreening.getSelectedIndex();
        switch (selection){

            case 0:
                calculationSetup.screeningMode = ScreeningMode.NONE;
                break;

            case 1:
                calculationSetup.screeningMode = ScreeningMode.ANDERSON;
                break;

            case 2:
                calculationSetup.screeningMode = ScreeningMode.LECUYER;
                break;
        }
    }

    private void setStoppingPowerCalculationMode(){

        int selection = cBoxStopping.getSelectedIndex();

        if (selection == 0) {
            calculationSetup.stoppingPowerCalculationMode = StoppingCalculationMode.ZB;
            calculationSetup.stoppingData = "";
        } else {
            calculationSetup.stoppingPowerCalculationMode = StoppingCalculationMode.PARA_FILE;
            calculationSetup.stoppingData = cBoxStopping.getSelectedItem().toString();
        }
    }


    //------------ Actions for "EA"         --------------------------------------------------------------------------//
    private void doGASimulation() {

        gaRunning = true;
        lbl_fitness.setText("---");
        lblStatus.setText("---");

        OptimizerInput optimizerInput = new OptimizerInput();

        optimizerInput.target               = targetModel.getTarget()                            ;
        optimizerInput.experimentalSetup    = experimentalSetup                                  ;
        optimizerInput.detectorSetup        = detectorSetup                                      ;
        optimizerInput.calculationSetup     = calculationSetup                                   ;
        optimizerInput.deParameter          = deParameter                                        ;
        optimizerInput.experimentalSpectrum = experimentalSpectrum                               ;

        fitnessPlotter.clear();
        fitnessPlotWindow.setPlotSeries(fitnessPlotter.makePlots());
        fitnessPlotWindow.refresh();

        clientEngine.requestOptimization(optimizerInput);
    }

    private void stopGASimulation() {

        if (gaRunning) {
            clientEngine.stopOptimization();
        }
    }

    private void refreshInputs(OptimizerOutput optimizerOutput) {

        targetModel.setTargetSilent(optimizerOutput.target.getDeepCopy());
        targetView.updateTarget();
        experimentalSetup.setCharge(optimizerOutput.charge);
        detectorSetup.setResolution(optimizerOutput.resolution);
        detectorSetup.setCalibrationFactor(optimizerOutput.factor);
        detectorSetup.setCalibrationOffset(optimizerOutput.offset);

        if (optimizerOutput.correctionFactors != null){
            int sizeCF = optimizerOutput.correctionFactors.length;
            if (sizeCF > 0){
                for (int i=0; i<sizeCF; i++){
                    calculationSetup.correctionFactors[i].correctionFactor = optimizerOutput.correctionFactors[i];
                }
            }
        }

        blockEvents = true;

        tfDetCalFactor.setText(Helper.dblToDecStr(optimizerOutput.factor, 4));
        tfDetCalOffset.setText(Helper.dblToDecStr(optimizerOutput.offset, 2));
        tfExpCharge.setText(Helper.dblToDecStr(optimizerOutput.charge, 4));
        tfDetDE.setText(Helper.dblToDecStr(optimizerOutput.resolution, 4));

        setExpCharge();
        blockEvents = false;
    }

    private void makeGACsFromCurrentSetting() {

        deInputCreator.setGAInput(deParameter);
        deInputCreator.setVisible(true);
    }


    //------------ GUI initialization --------------------------------------------------------------------------------//

    private void initComponents() {

        this.setTitle("Ruthelde V8.04 - 2025_02_12 (C) R. Heller");
        this.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setContentPane(rootPanel);
        pack();
        Dimension dim = this.getSize();
        dim.height += 10;
        this.setSize(dim);
        //this.setLocation(50,50);
        this.setResizable(true);
        this.setVisible(true);

        this.deParameter = new DEParameter();
        this.gaRunning = false;

        spectraPlotWindow = new PlotWindow("Spectra");
        spectraPlotWindow.getPlotGenerator().plotProperties.xAxisName = "Energy (keV)";
        spectraPlotWindow.getPlotGenerator().plotProperties.yAxisName = "counts";
        currentFileName = "No spectrum file loaded";
        spectraPlotWindow.setTitle("Spectra - " + currentFileName);
        stoppingPlotWindow = new StoppingPlotWindow();
        depthPlotWindow = new DepthPlotWindow();
        ibaKinematics = new IBAKinematics();

        buildMenu();

        fitnessPlotWindow = new PlotWindow("DE Fitness evolution");
        fitnessPlotWindow.getPlotGenerator().plotProperties.xAxisName = "Generation";
        fitnessPlotWindow.getPlotGenerator().plotProperties.yAxisName = "fitness";

        //parameterPlotWindow = new PlotWindow("DE Fit Results");
        //parameterPlotWindow.getPlotGenerator().plotProperties.xAxisName = "Generation";
        //parameterPlotWindow.getPlotGenerator().plotProperties.yAxisName = "quantity";

        eaStatusWindow = new EAStatusWindow("DE Status");

        deInputCreator = new DEInputCreator();

        tfExpZ1.setText(Integer.toString(experimentalSetup.getProjectile().getZ()));
        fillCBoxExpM1(0.0f);
        tfExpE0.setText(Helper.dblToDecStr(experimentalSetup.getE0(), 2));
        tfExpDE0.setText(Helper.dblToDecStr(experimentalSetup.getDeltaE0(), 2));
        tfExpAlpha.setText(Helper.dblToDecStr(experimentalSetup.getAlpha(), 2));
        tfExpTheta.setText(Helper.dblToDecStr(experimentalSetup.getTheta(), 2));
        tFExpBeta.setText(Helper.dblToDecStr(experimentalSetup.getBeta(), 2));
        updateChargeValues();

        cBoxStopping.setSelectedIndex(0);   // Default stopping calculation mode = ZB
        cBoxStraggling.setSelectedIndex(2); // Default straggling mode = CHU
        cBoxScreening.setSelectedIndex(2);  // Default screening mode = ANDERSON
        cb_sim_isotopes.setSelected(true);  // Simulate isotopes by default

        tfDetSolidAngle.setText(Helper.dblToDecStr(detectorSetup.getSolidAngle(), 2));
        updateDetDE();
        updateCalibration();

        tfExpZ1.addActionListener(e -> setExpZ1());

        tfExpZ1.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                setExpZ1();
            }
        });

        cBoxExpM1.addActionListener(e -> {

            if (!blockEvents) {

                int Z1 = experimentalSetup.getProjectile().getZ();

                Element element = new Element();
                element.setAtomicNumber(Z1);
                int M1Index = cBoxExpM1.getSelectedIndex();
                double M1 = element.getIsotopeList().get(M1Index).getMass();
                experimentalSetup.getProjectile().setM(M1);
                updateOpenPlotWindows();
            }
        });

        tfExpE0.addActionListener(e -> {
            if (!blockEvents) setExpE0();
        });

        tfExpE0.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!blockEvents) setExpE0();
            }
        });

        tfExpDE0.addActionListener(e -> {
            if (!blockEvents) setExpDE0();
        });

        tfExpDE0.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!blockEvents) setExpDE0();
            }
        });

        tfExpE0.addMouseWheelListener(e -> {
            int stepSize = -e.getUnitsToScroll();
            if (stepSize > 10) stepSize = 10;
            double E0 = experimentalSetup.getE0();
            //E0 += (double)stepSize / 100.0 * E0;
            E0 += stepSize;
            if (E0 < 0.0) E0 = 0.0;
            experimentalSetup.setE0(E0);
            updateOpenPlotWindows();
            experimentalSetup.getProjectile().setE(E0);
            blockEvents = true;
            tfExpE0.setText(Helper.dblToDecStr(E0, 2));
            blockEvents = false;
        });

        tfExpDE0.addMouseWheelListener(e -> {
            int stepSize = -e.getUnitsToScroll();
            if (stepSize > 10) stepSize = 10;
            double dE0 = experimentalSetup.getDeltaE0();
            dE0 += (double) stepSize / 100.0 * dE0;
            if (dE0 == 0.0) dE0 = 1.0;
            if (dE0 < 0.0) dE0 = 0.0;
            experimentalSetup.setDeltaE0(dE0);
            updateOpenPlotWindows();
            blockEvents = true;
            tfExpDE0.setText(Helper.dblToDecStr(dE0, 2));
            blockEvents = false;
        });

        tfExpAlpha.addActionListener(e -> {
            if (!blockEvents) setExpAlpha();
        });

        tfExpAlpha.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!blockEvents) setExpAlpha();
            }
        });

        tfExpTheta.addActionListener(e -> {
            if (!blockEvents) setExpTheta();
        });

        tfExpTheta.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!blockEvents) setExpTheta();
            }
        });

        tfExpCharge.addActionListener(e -> {
            if (!blockEvents) setExpCharge();
        });

        tfExpCharge.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!blockEvents) setExpCharge();
            }
        });

        tfExpCharge.addMouseWheelListener(e -> {
            int stepSize = -e.getUnitsToScroll();
            if (stepSize > 10) stepSize = 10;
            double charge = experimentalSetup.getCharge();
            charge += (double) stepSize / 100.0 * charge;
            if (charge == 0.0) charge = 1.0;
            if (charge < 0.0) charge = 0.1;
            experimentalSetup.setCharge(charge);
            updateOpenPlotWindows();
            blockEvents = true;
            updateChargeValues();
            blockEvents = false;
        });

        tfExpChargeMin.addActionListener(e -> {
            if (!blockEvents) {
                setExpMinCharge();
            }
        });

        tfExpChargeMax.addActionListener(e -> {
            if (!blockEvents) {
                setExpMaxCharge();
            }
        });

        tfDetSolidAngle.addActionListener(e -> {
            if (!blockEvents) setTfDetSolidAngle();
        });

        tfDetSolidAngle.addMouseWheelListener(e -> {
            int stepSize = -e.getUnitsToScroll();
            if (stepSize > 10) stepSize = 10;
            double solidAngle = detectorSetup.getSolidAngle();
            solidAngle += (double) stepSize / 100.0 * solidAngle;
            if (solidAngle == 0.0) solidAngle = 1.0;
            if (solidAngle < 0.0) solidAngle = 0.0;
            detectorSetup.setSolidAngle(solidAngle);
            updateOpenPlotWindows();
            blockEvents = true;
            tfDetSolidAngle.setText(Helper.dblToDecStr(solidAngle, 2));
            blockEvents = false;
        });

        tfDetDE.addActionListener(e -> {
            if (!blockEvents) setDetDE();
        });

        tfDetDE.addMouseWheelListener(e -> {
            int stepSize = -e.getUnitsToScroll();
            if (stepSize > 10) stepSize = 10;
            double dE = detectorSetup.getResolution();
            dE += (double) stepSize / 100.0 * dE;
            if (dE == 0.0) dE = 1.0;
            if (dE < 0.0) dE = 0.0;
            detectorSetup.setResolution(dE);
            updateOpenPlotWindows();
            blockEvents = true;
            updateDetDE();
            blockEvents = false;
        });

        tfDetDEMin.addActionListener(e -> setDetDEMin());

        tfDetDEMax.addActionListener(e -> setDetDEMax());

        tfDetCalOffset.addActionListener(e -> {
            if (!blockEvents) setDetOffset();
        });

        tfDetCalOffset.addMouseWheelListener(e -> {
            int stepSize = -e.getUnitsToScroll();
            if (stepSize > 10) stepSize = 10;
            double offset = detectorSetup.getCalibration().getOffset();
            offset += (double) stepSize / 100.0 * offset;
            if (offset == 0.0) offset = 1.0 * stepSize;
            detectorSetup.getCalibration().setOffset(offset);
            updateOpenPlotWindows();
            blockEvents = true;
            updateCalibration();
            blockEvents = false;
        });

        tfDetCalOffsetMin.addActionListener(e -> setDetOffsetMin());

        tfDetCalOffsetMax.addActionListener(e -> setDetOffsetMax());

        tfDetCalFactor.addActionListener(e -> {
            if (!blockEvents) setDetFactor();
        });

        tfDetCalFactor.addMouseWheelListener(e -> {
            int stepSize = -e.getUnitsToScroll();
            if (stepSize > 10) stepSize = 10;
            double a = detectorSetup.getCalibration().getFactor();
            a += (double) stepSize / 100.0 * a;
            if (a == 0.0) a = 1.0;
            if (a < 0.0) a = 0.0;
            detectorSetup.getCalibration().setFactor(a);
            updateOpenPlotWindows();
            blockEvents = true;
            updateCalibration();
            blockEvents = false;
        });

        tfDetCalFactorMin.addActionListener(e -> setDetFactorMin());

        tfDetCalFactorMax.addActionListener(e -> setDetFactorMax());

        cb_sim_isotopes.addActionListener(e -> {
            calculationSetup.simulateIsotopes = cb_sim_isotopes.isSelected();
            updateOpenPlotWindows();
        });

        cBoxStraggling.addActionListener(e -> {
            setStragglingMode();
            if (!blockEvents) updateOpenPlotWindows();
        });

        cBoxScreening.addActionListener(e -> {
            setScreeningMode();
            if (!blockEvents) updateOpenPlotWindows();
        });

        cBoxStopping.addActionListener(e -> {
            setStoppingPowerCalculationMode();
            if (!blockEvents) updateOpenPlotWindows();
        });

        setLastFolder(lastFolder);
    }

    private void buildMenu() {

        JMenuBar jmb = new JMenuBar();

        JMenu fileMenu = new JMenu("File");

        JMenu jmLoad = new JMenu("Import Spectrum");

        JMenuItem itemLoadASCIISpectrumOne = new JMenuItem("One Column ASCII");
        itemLoadASCIISpectrumOne.addActionListener(e -> readDataFile(ONE_COLUMN_ASCII, null));
        jmLoad.add(itemLoadASCIISpectrumOne);

        JMenuItem itemLoadASCIISpectrumTwo = new JMenuItem("Multi Column ASCII");
        itemLoadASCIISpectrumTwo.addActionListener(e -> readDataFile(TWO_COLUMN_ASCII, null));
        jmLoad.add(itemLoadASCIISpectrumTwo);

        JMenuItem itemLoadOldIBCSpectrum = new JMenuItem("IBC Spectrum (vdG)");
        itemLoadOldIBCSpectrum.addActionListener(e -> readDataFile(IBC_RBS, null));
        jmLoad.add(itemLoadOldIBCSpectrum);

        JMenuItem itemLoadNewIBCSpectrum = new JMenuItem("IBC Spectrum (3MV) Single");
        itemLoadNewIBCSpectrum.addActionListener(e -> readDataFile(IBC_3MV_SINGLE, null));
        jmLoad.add(itemLoadNewIBCSpectrum);

        JMenuItem itemLoadNewIBCAllSpectra = new JMenuItem("IBC Spectrum (3MV) Multiple");
        itemLoadNewIBCAllSpectra.addActionListener(e -> readDataFile(FileType.IBC_3MV_MULTI, null));
        jmLoad.add(itemLoadNewIBCAllSpectra);

        JMenuItem itemLoadIMECSpectrum = new JMenuItem("IMEC Spectrum");
        itemLoadIMECSpectrum.addActionListener(e -> readDataFile(IMEC, null));
        jmLoad.add(itemLoadIMECSpectrum);

        fileMenu.add(jmLoad);

        fileMenu.add(new JSeparator());

        JMenuItem itemLoadSimulation = new JMenuItem("Load Simulation");
        itemLoadSimulation.addActionListener(e -> loadSimulation(null));
        fileMenu.add(itemLoadSimulation);

        JMenuItem itemSaveSpectrum = new JMenuItem("Save Simulation");
        itemSaveSpectrum.addActionListener(e -> saveSimulation(null));
        itemSaveSpectrum.setAccelerator(KeyStroke.getKeyStroke('S', Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()));
        fileMenu.add(itemSaveSpectrum);

        fileMenu.add(new JSeparator());

        JMenuItem itemMakeExpSpectrum = new JMenuItem("Generate Measured Spectrum");
        itemMakeExpSpectrum.addActionListener(e -> makeExpSpectrum());
        fileMenu.add(itemMakeExpSpectrum);

        jmb.add(fileMenu);


        JMenu targetMenu = new JMenu("Target");

        JMenuItem itemShowTargetConfigurator = new JMenuItem("Show target configurator");
        itemShowTargetConfigurator.addActionListener(e -> targetView.setVisible(true));
        targetMenu.add(itemShowTargetConfigurator);

        JMenuItem itemShowFoilConfigurator = new JMenuItem("Show foil configurator");
        itemShowFoilConfigurator.addActionListener(e -> foilView.setVisible(true));
        targetMenu.add(itemShowFoilConfigurator);

        jmb.add(targetMenu);

        JMenu plotMenu = new JMenu("Plot");

        JCheckBoxMenuItem jcbPlotElements = new JCheckBoxMenuItem("Show Element Contributions");
        jcbPlotElements.setSelected(outputOptions.showElementContributions);
        jcbPlotElements.addActionListener(e -> {
            if (!blockEvents) {
                outputOptions.showElementContributions = jcbPlotElements.isSelected();
                updateOpenPlotWindows();
            }
        });
        plotMenu.add(jcbPlotElements);

        JCheckBoxMenuItem jcbPlotElementsIsotopes = new JCheckBoxMenuItem("Show Isotope Contributions");
        jcbPlotElementsIsotopes.setSelected(outputOptions.showIsotopeContributions);
        jcbPlotElementsIsotopes.addActionListener(e -> {
            if (!blockEvents) {
                outputOptions.showIsotopeContributions = jcbPlotElementsIsotopes.isSelected();
                updateOpenPlotWindows();
            }
        });
        plotMenu.add(jcbPlotElementsIsotopes);

        JCheckBoxMenuItem jcbPlotLayers = new JCheckBoxMenuItem("Show Layer Contributions");
        jcbPlotLayers.setSelected(outputOptions.showLayerContributions);
        jcbPlotLayers.addActionListener(e -> {
            if (!blockEvents) {
                outputOptions.showLayerContributions = jcbPlotLayers.isSelected();
                updateOpenPlotWindows();
            }
        });
        plotMenu.add(jcbPlotLayers);

        JCheckBoxMenuItem jcbPlotChannels = new JCheckBoxMenuItem("Show Channels");
        jcbPlotChannels.setSelected(outputOptions.showLayerContributions);
        jcbPlotChannels.addActionListener(e -> {
            if (!blockEvents) {
                outputOptions.showChannels = jcbPlotChannels.isSelected();
                updateOpenPlotWindows();
            }
        });
        plotMenu.add(jcbPlotChannels);

        plotMenu.add(new JSeparator());

        JMenuItem itemShowSpectra = new JMenuItem("Show Spectrum Window");
        itemShowSpectra.addActionListener(e -> {
            spectraPlotWindow.setVisible(true);
            updateOpenPlotWindows();
        });
        plotMenu.add(itemShowSpectra);

        jmb.add(plotMenu);

        JMenu gaMenu = new JMenu("DE");

        JMenuItem itemStartGA = new JMenuItem("Start");
        itemStartGA.addActionListener(e -> doGASimulation());
        gaMenu.add(itemStartGA);

        JMenuItem itemStopGA = new JMenuItem("Stop");
        itemStopGA.addActionListener(e -> stopGASimulation());
        gaMenu.add(itemStopGA);

        gaMenu.add(new JSeparator());

        JMenuItem itemMakeGAConstrains = new JMenuItem("Settings");
        itemMakeGAConstrains.addActionListener(e -> makeGACsFromCurrentSetting());
        gaMenu.add(itemMakeGAConstrains);

        gaMenu.add(new JSeparator());

        JMenuItem itemShowEAStatus = new JMenuItem("Show status");
        itemShowEAStatus.addActionListener(e -> eaStatusWindow.setVisible(true));
        gaMenu.add(itemShowEAStatus);

        JMenuItem itemShowFitness = new JMenuItem("Show fitness trend");
        itemShowFitness.addActionListener(e -> {
            fitnessPlotWindow.setVisible(true);
        });
        gaMenu.add(itemShowFitness);

        //JMenuItem itemShowParameters = new JMenuItem("Show fit results");
        //itemShowParameters.addActionListener(e -> {
        //    parameterPlotWindow.setVisible(true);
        //});
        //gaMenu.add(itemShowParameters);

        jmb.add(gaMenu);

        JMenu miscMenu = new JMenu("Misc");

        JMenuItem itemShowStopping = new JMenuItem("Show Stopping Plotter");
        itemShowStopping.addActionListener(e -> {
            stoppingPlotWindow.setVisible(true);
            updateOpenPlotWindows();
        });
        miscMenu.add(itemShowStopping);

        JMenuItem itemShowPenetration = new JMenuItem("Show Depth Plotter");
        itemShowPenetration.addActionListener(e -> {
            depthPlotWindow.setVisible(true);
            updateOpenPlotWindows();
        });
        miscMenu.add(itemShowPenetration);

        JMenuItem itemKinematics = new JMenuItem("IBA Kinematics");
        itemKinematics.addActionListener(e -> {
            if (!ibaKinematics.isVisible()) {
                ibaKinematics.setVisible(true);
            }
        });
        miscMenu.add(itemKinematics);

        JMenuItem itemReport = new JMenuItem("Generate Report");
        itemReport.addActionListener(e -> saveReport());
        miscMenu.add(itemReport);

        jmb.add(miscMenu);

        this.setJMenuBar(jmb);
    }

    private void createUIComponents() {

        pb_add_cross_section = new JButton();
        pb_add_cross_section.addActionListener(e -> {

            if (availableCrossSections != null && availableCrossSections.length > 0) {

                JComboBox list = new JComboBox<>();
                DefaultComboBoxModel dml = new DefaultComboBoxModel();
                for (String fileName : availableCrossSections) dml.addElement(fileName);
                list.setModel(dml);

                Object[] message = {
                        "Available Cross Section Files:", list
                };

                int option = JOptionPane.showConfirmDialog(null, message, "Select cross cection file to add", JOptionPane.OK_CANCEL_OPTION);
                if (option == JOptionPane.OK_OPTION) {

                    String fileName = availableCrossSections[list.getSelectedIndex()];
                    String[] crossSectionData;

                    if (calculationSetup.crossSectionData == null) {

                        crossSectionData = new String[1];
                        crossSectionData[0] = fileName;
                    } else {

                        crossSectionData = new String[calculationSetup.crossSectionData.length+1];
                        crossSectionData[calculationSetup.crossSectionData.length] = fileName;
                        int index = 0;
                        for (String name : calculationSetup.crossSectionData){
                            crossSectionData[index] = name;
                            index++;
                        }
                    }

                    calculationSetup.crossSectionData = crossSectionData;
                    //for (String name : calculationSetup.crossSectionData) System.out.println(name);
                    updateOpenPlotWindows();
                }
            } else {

                JOptionPane.showMessageDialog(
                        null,
                        "No R33 files found in folder \"CrossSectionFiles\" on server.",
                        "No files found",
                        JOptionPane.INFORMATION_MESSAGE);
            }

        });

        pb_show_cross_sections = new JButton();
        pb_show_cross_sections.addActionListener(e -> {

            if (calculationSetup.crossSectionData != null && calculationSetup.crossSectionData.length > 0){

                String names = "";
                for (String name : calculationSetup.crossSectionData){
                    names += name + "\n";
                }

                JOptionPane.showMessageDialog(
                        null,
                        names,
                        "Active cross section file(s)",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {

                JOptionPane.showMessageDialog(
                        null,
                        "No cross section file(s) loaded.",
                        "Active cross section file(s)",
                        JOptionPane.INFORMATION_MESSAGE);
            }

        });

        pb_clear_cross_sections = new JButton();
        pb_clear_cross_sections.addActionListener(e -> {
            calculationSetup.crossSectionData = new String[0];
            updateOpenPlotWindows();
        });

    }

    private void debugMsg(String msg){

        if (DEBUG){
            System.out.println(this.getClass().getName() + ": " + msg);
        }
    }
    private void errorMsg(String msg){

        System.out.println(this.getClass().getName() + " - An error occurred: " + msg);
    }

    private void infoMsg(String msg){

        System.out.println(this.getClass().getName() + " - Info: " + msg);
    }
}
