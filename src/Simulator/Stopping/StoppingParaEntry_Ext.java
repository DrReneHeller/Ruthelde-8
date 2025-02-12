package Simulator.Stopping;

public class StoppingParaEntry_Ext {

    public int z1, z2;
    public double[] params;
    public ErrorData error_data;

    public StoppingParaEntry_Ext(int z1, int z2, double[] params, ErrorData errorData){

        this.z1 = z1;
        this.z2 = z2;
        this.params = params;
        this.error_data = errorData;
    }

    public StoppingParaEntry_Ext getDeepCopy() {

        double[] _params = new double[params.length];
        System.arraycopy(params, 0, _params, 0, params.length);
        return new StoppingParaEntry_Ext(z1, z2, _params, error_data.getDeepCopy());
    }

    public static class ErrorData{

        public double chi2r2;
        public double[] sigma_per_decade;

        public ErrorData(double chi2r2, double[] sigma_per_decade){

            this.chi2r2 = chi2r2;
            this.sigma_per_decade = sigma_per_decade;
        }

        public ErrorData getDeepCopy(){

            double[] _sigma_per_decade = new double[sigma_per_decade.length];
            System.arraycopy(_sigma_per_decade, 0, _sigma_per_decade,0, sigma_per_decade.length);
            return new ErrorData(chi2r2,_sigma_per_decade);
        }
    }
}
