package es.udc.fi.ri.alvarez_alvarez;

import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;
import java.io.BufferedReader;
import java.io.FileReader;

public class Compare {
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: Compare -test <testType> <alpha> -results <results1.csv> <results2.csv>");
            System.exit(1);
        }

        String testType = args[1];
        double alpha = Double.parseDouble(args[2]);
        String resultsFile1 = args[4];
        String resultsFile2 = args[5];

        // Load results from CSV files
        // Parse and compare results logic here

        if (testType.equals("t")) {
            // Perform t-test
            // TTest tTest = new TTest();
            // double pValue = tTest.tTest(sample1, sample2);
            // Perform test and print results
        } else if (testType.equals("wilcoxon")) {
            // Perform Wilcoxon signed-rank test
            // WilcoxonSignedRankTest wilcoxonTest = new WilcoxonSignedRankTest();
            // double pValue = wilcoxonTest.wilcoxonSignedRankTest(sample1, sample2, false);
            // Perform test and print results
        }
    }

    private static double[] loadResults(String resultsFile) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(resultsFile));
        String line;
        // Parse results from CSV file
        // Return results as double array
        return new double[0];
    }
}
