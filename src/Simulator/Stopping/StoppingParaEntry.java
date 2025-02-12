package Simulator.Stopping;

public class StoppingParaEntry {

    public int z2;
    public double[] params;
    public ErrorData error_data;

    public StoppingParaEntry(){

    }

    public StoppingParaEntry(int z2, double[] params, ErrorData errorData){

        this.z2 = z2;
        this.params = params;
        this.error_data = errorData;
    }

    public StoppingParaEntry getDeepCopy() {

        double[] _params = new double[params.length];
        System.arraycopy(params, 0, _params, 0, params.length);
        return new StoppingParaEntry(z2, _params, error_data.getDeepCopy());
    }

    public static class ErrorData{

        public double chi2r2;
        public double[] sigma_per_decade;

        public ErrorData(){

        }

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
