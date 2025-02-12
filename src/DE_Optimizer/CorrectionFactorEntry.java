package DE_Optimizer;

public class CorrectionFactorEntry {

    public int Z;
    public double correctionFactor;
    public double cF_min, cF_max;

    public CorrectionFactorEntry(){

        Z = 1;
        correctionFactor = 1.0d;
        cF_min = 1.0d;
        cF_max = 1.0d;
    }
}


