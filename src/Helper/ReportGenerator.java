package Helper;

import Helper.Plot.CFileFilter;
import Helper.Plot.PlotSeries;
import Helper.Plot.PlotWindow;
import Simulator.Detector.DetectorSetup;
import Simulator.ExperimentalSetup;
import Simulator.Target.Element;
import Simulator.Target.Layer;
import Simulator.Target.Target;
import javax.swing.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
public class ReportGenerator {

    public ExperimentalSetup es;
    public DetectorSetup ds;
    public Target target;
    public PlotWindow plotWindow;
    public String lastFolder, sampleName, sampleOwner, measuredBy, remarks, dateTime;

    public ReportGenerator(){

    }

    public void generateReport(){

        try {
            final JFileChooser fc;
            if (lastFolder != null) fc = new JFileChooser(lastFolder);
            else fc = new JFileChooser();

            fc.addChoosableFileFilter(new CFileFilter("html", "Ruthelde Report File (*.html)"));
            fc.setAcceptAllFileFilterUsed(false);


            int returnVal = fc.showSaveDialog(null);

            if (returnVal == JFileChooser.APPROVE_OPTION) {

                CFileFilter cf = (CFileFilter) fc.getFileFilter();
                File file = fc.getSelectedFile();

                String dir = file.getParentFile().toString();
                String name = file.getName();
                if (name.contains(".")) name = name.split("\\.")[0];
                name = name + "." + cf.getFileExt();
                file = new File(dir + "/" + name);

                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write(buildReportString(file));
                writer.close();
                lastFolder = file.getParent();
            }

        } catch (Exception ex) {
            System.out.println("Error writing Report file.");
            //ex.printStackTrace();
        }


    }

    private String buildReportString(File file){

        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>RBS-Report</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            font-family: Arial, sans-serif;\n" +
                "            margin: 0;\n" +
                "            padding: 20px;\n" +
                "            background-color: #f4f4f9;\n" +
                "            color: #333;\n" +
                "        }\n" +
                "        .container {\n" +
                "            max-width: 1200px;\n" +
                "            margin: auto;\n" +
                "            background: white;\n" +
                "            padding: 20px;\n" +
                "            border-radius: 8px;\n" +
                "            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);\n" +
                "            position: relative;\n" +
                "        }\n" +
                "        h1, h2 {\n" +
                "            color: #333;\n" +
                "        }\n" +
                "        .section {\n" +
                "            margin-bottom: 30px;\n" +
                "        }\n" +
                "        .container img {\n" +
                "           height: 100%;\n" +
                "           width: 100%;\n" +
                "           object-fit: contain\n" +
                "         }\n" +
                "        .parameters {\n" +
                "            display: grid;\n" +
                "            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));\n" +
                "            gap: 15px;\n" +
                "        }\n" +
                "        .parameter {\n" +
                "            padding: 15px;\n" +
                "            background: #f9f9f9;\n" +
                "            border: 1px solid #ddd;\n" +
                "            border-radius: 5px;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .graph {\n" +
                "            width: 100%;\n" +
                "            height: 400px;\n" +
                "            background: #f9f9f9;\n" +
                "            border: 1px solid #ddd;\n" +
                "            border-radius: 5px;\n" +
                "            display: flex;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            font-size: 18px;\n" +
                "            color: #888;\n" +
                "        }\n" +
                "        .remarks {\n" +
                "            background: #f9f9f9;\n" +
                "            border: 1px solid #ddd;\n" +
                "            border-radius: 5px;\n" +
                "            padding: 15px;\n" +
                "            white-space: pre-line;\n" +
                "        }\n" +
                "        table {\n" +
                "            width: 100%;\n" +
                "            border-collapse: collapse;\n" +
                "            margin-top: 10px;\n" +
                "        }\n" +
                "        th, td {\n" +
                "            padding: 10px;\n" +
                "            text-align: left;\n" +
                "            border: 1px solid #ddd;\n" +
                "        }\n" +
                "        th {\n" +
                "            background-color: #f4f4f9;\n" +
                "        }\n" +
                "        .logo {\n" +
                "            position: absolute;\n" +
                "            top: 10px;\n" +
                "            right: 10px; /* Adjusted to top-right corner */\n" +
                "            width: 100px;\n" +
                "            height: auto;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n\n");

        sb.append("<div class=\"container\">\n" +
                "\n" +
                "    <h1>RBS Measurement Report</h1>\n" +
                "\n" +
                "    <!-- General -->\n" +
                "    <div class=\"section\">\n" +
                "        <h2>General</h2>\n" +
                "        <div class=\"parameters\">\n");

        sb.append("<div class=\"parameter\">Date/Time <h4>");
        sb.append(dateTime);
        sb.append("</h4></div>\n");

        sb.append("<div class=\"parameter\">Sample <h4>");
        sb.append(sampleName);
        sb.append("</h4></div>\n");

        sb.append("<div class=\"parameter\">Owner <h4>");
        sb.append(sampleOwner);
        sb.append("</h4></div>\n");

        sb.append("<div class=\"parameter\">Measured by <h4>");
        sb.append(measuredBy);
        sb.append("</h4></div>\n");

        sb.append("</div>\n" +
                "    </div>\n\n");

        sb.append("<!-- Experimental Setup -->\n" +
                "    <div class=\"section\">\n" +
                "        <h2>Experimental Setup</h2>\n" +
                "        <div class=\"parameters\">\n");

        sb.append("<div class=\"parameter\">Projectile<h4>");

        Element temp = new Element();
        temp.setAtomicNumber(es.getProjectile().getZ());
        String projectileStr = temp.getName();
        double mass = es.getProjectile().getM();
        projectileStr += " (M=" + String.format("%.3f", mass) + ")";
        sb.append(projectileStr);
        sb.append("</h4></div>\n");

        sb.append("<div class=\"parameter\">Energy<h4>");
        String energyStr = String.format("%.1f", es.getProjectile().getE());
        sb.append(energyStr + " keV");
        sb.append("</h4></div>\n");

        double alpha = es.getAlpha();
        String alphaStr = String.format("%.1f", alpha);
        sb.append("<div class=\"parameter\">Impact angle (alpha)<h4>");
        sb.append(alphaStr + "°");
        sb.append("</h4></div>\n");

        double theta =es.getTheta();
        String thetaStr = String.format("%.1f", theta);
        sb.append("<div class=\"parameter\">Scattering angle (theta)<h4>");
        sb.append(thetaStr + "°");
        sb.append("</h4></div>\n");

        double charge = es.getCharge();
        String chargeStr = String.format("%.2f", charge);
        sb.append("<div class=\"parameter\">Applied Charge<h4>");
        sb.append(chargeStr + " µC");
        sb.append("</h4></div>\n");

        double omega = ds.getSolidAngle();
        String omegaStr = String.format("%.2f", omega);
        sb.append("<div class=\"parameter\">Solid angle<h4>");
        sb.append(omegaStr + " msr");
        sb.append("</h4></div>\n");

        double factor = ds.getCalibration().getFactor();
        double offset = ds.getCalibration().getOffset();
        String factorStr = String.format("%.4f", factor);
        String offsetStr = String.format("%.1f", Math.abs(offset));
        sb.append("<div class=\"parameter\">Detector calibration<h4>");
        sb.append("E (keV) = " + factorStr + " x ch ");
        if (offset >= 0) sb.append("+ "); else sb.append("- ");
        sb.append(offsetStr);
        sb.append("</h4></div>\n");

        double res = ds.getResolution();
        String resStr = String.format("%.2f", res);
        sb.append("<div class=\"parameter\">Detector resolution<h4>");
        sb.append(resStr + " keV");
        sb.append("</h4></div>\n");

        sb.append("</div>\n" +
                "    </div>\n\n");

        sb.append("<!-- Data Table -->\n" +
                "    <div class=\"section\">\n" +
                "        <h2>Target Model</h2>\n" +
                "        <table>\n" +
                "            <thead>\n" +
                "                <tr>\n" +
                "                    <th>Layer</th>\n" +
                "                    <th>Element(s)</th>\n" +
                "                    <th>Atomic Ratio</th>\n" +
                "                    <th>Areal Density (1E15 at/cm2)</th>\n" +
                "                </tr>\n" +
                "            </thead>\n" +
                "            <tbody>\n");


        int layerIndex = 1;
        for (Layer layer : target.getLayerList()){

            layer.normalizeElements();
            int elementIndex = 0;

            for (Element element : layer.getElementList()){

                sb.append("<tr>");
                if (elementIndex == 0) sb.append("<td>" + layerIndex + "</td>"); else sb.append("<td> </td>");
                sb.append("<td>" + element.getName() + "</td>");
                double elementRatio = element.getRatio();
                String elementRatioStr = String.format("%.2f", elementRatio);
                sb.append("<td>" + elementRatioStr + "</td>");
                double elementAD = element.getArealDensity();
                String elementADStr = String.format("%.2f", elementAD);
                sb.append("<td>" + elementADStr + "</td>");
                sb.append("</tr>");
                elementIndex++;
            }

            sb.append("<tr>");

            sb.append("<td> </td>");
            sb.append("<td> </td>");
            sb.append("<td> </td>");

            if (layerIndex < target.getLayerList().size()) {
                double layerAD = layer.getArealDensity();
                String layerADStr = String.format("%.2f", layerAD);
                sb.append("<td><b>" + layerADStr + "</b> (");
                double layerThickness = layer.getThickness();
                String layerThicknessStr = String.format("%.2f", layerThickness);
                sb.append(layerThicknessStr + "nm) [");
                double layerMassDensity = layer.getMassDensity();
                String layerMassDensityStr = String.format("%.2f", layerMassDensity);
                sb.append(layerMassDensityStr + "g/cm3]</td>");
            } else {
                sb.append("<td><b>" + "Bulk"+ "</b></td>");
            }

            sb.append("</tr>\n");
            layerIndex++;
        }

        sb.append("</tbody>\n" +
                "        </table>\n" +
                "    </div>\n\n");


        String imageFileName = "";

        if (file.getName().split("\\.").length > 1) imageFileName = file.getName().split("\\.")[0]; else imageFileName = file.getName();

        imageFileName = file.getParent() + "/" + imageFileName + ".png";

        plotWindow.saveImage(new File(imageFileName));

        sb.append("<!-- Data Graph -->\n" +
                "    <div class=\"section\">\n" +
                "        <h2>Spectrum and Fit</h2>\n" +
                "        <img src=\"");

        sb.append(imageFileName);

        sb.append("\" alt=\"Spectrum\">\n" +
                "    </div>\n\n");

        sb.append("<!-- Remarks Section -->\n" +
                "    <div class=\"section\">\n" +
                "        <h2>Remarks</h2>\n" +
                "        <div class=\"remarks\">\n");

        sb.append(remarks);

        sb.append("\n" +
                "        </div>\n" +
                "    </div>\n\n");

        sb.append("<!-- Data Table -->\n" +
                "    <div class=\"section\">\n" +
                "        <h2>Data</h2>\n" +
                "        <table>\n" +
                "            <thead>\n");

        sb.append("<tr>");

        sb.append("<td> Channel </td>");
        sb.append("<td> Energy (keV) </td>");
        sb.append("<td> Experimental Spectrum </td>");
        sb.append("<td> Simulated Spectrum </td>");

        if (plotWindow.getPlotSeries().size()>2){
            for (int i=2; i<plotWindow.getPlotSeries().size(); i++){
                sb.append("<td>");
                sb.append(plotWindow.getPlotSeries().get(i).seriesProperties.name);
                sb.append("</td>");
            }
        }

        sb.append("</tr>");

        sb.append("</thead>\n" +
                "            <tbody>\n");

        // <tr><td>0</td><td>0.0</td><td>0.0</td><td>0.0</td></tr>\n"

        int size = plotWindow.getPlotSeries().getFirst().data.size();

        for (int i=0; i<size; i++){

            sb.append("<tr>");

            // channel
            sb.append("<td>" + i + "</td>");

            // energy
            double _energy = (double) i * ds.getCalibration().getFactor() + ds.getCalibration().getOffset();
            String _energyStr = String.format("%.2f", _energy);
            sb.append("<td>" + _energyStr + "</td>");

            for (PlotSeries ps : plotWindow.getPlotSeries()){

                double _value =  ps.data.get(i).y;
                String _valueStr = String.format("%.2f", _value);
                sb.append("<td>" + _valueStr + "</td>");
            }

            sb.append("</tr>\n");
        }

        sb.append("</tbody>\n" +
                "        </table>\n" +
                "    </div>\n\n");

        sb.append("</div>\n" +
                "\n" +
                "</body>\n" +
                "</html>");

        return sb.toString();
    }
}
