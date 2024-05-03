package es.udc.fi.ri.alvarez_alvarez;

import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Compare {

    public static String resultsPath = "src/main/resources/trainingTest/";
    public static void main(String[] args) throws Exception {

        // Guardamos las entradas
        String testType = null;
        Double alpha = null;
        String results1 = null;
        String results2 = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-test":
                    testType = args[++i];
                    alpha = Double.valueOf(args[++i]);
                    break;
                case "-results":
                    results1 = args[++i];
                    results2 = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("Parámetro desconocido " + args[i]);
            }
        }

        if (alpha <= 0.0 || alpha > 0.5) {
            System.err.println("<alpha> debe estar entre 0.0 y 5.0");
            System.exit(1);
        }

        if (!testType.equals("t") && !testType.equals("wilcoxon")) {
            System.err.println("<test> debe ser t o wilcoxon");
            System.exit(1);
        }

        double[] resultList1 = loadResults(results1);
        double[] resultList2 = loadResults(results2);
        boolean testResult;
        double pValor = 0.0;


        if (testType.equals("t")) {
            pValor = new TTest().tTest(resultList1, resultList2);
        } else if (testType.equals("wilcoxon")) {
            pValor = new WilcoxonSignedRankTest().wilcoxonSignedRankTest(resultList1, resultList2, false);
        }

        testResult = pValor < alpha;

        printResults(results1, results2, testType, pValor, alpha, testResult);
    }

    private static double[] loadResults(String resultsFile) throws Exception {
        resultsFile = resultsPath + resultsFile;
        BufferedReader br = new BufferedReader(new FileReader(resultsFile));
        List<Double> resultsList = new ArrayList<>();
        String line;
        try{
            while ((line = br.readLine()) != null && !line.isEmpty()){
                String[] partes = line.split(" ");
                if (partes.length == 1){
                    resultsList.add(Double.parseDouble(partes[0]));
                }
            }
        }catch (Exception e){
            throw e;
        }

        double[] finalResults = new double[resultsList.size()];
        for (int i =0; i < resultsList.size(); i++){
            finalResults[i] = resultsList.get(i);
        }
        // Parse results from CSV file
        // Return results as double array
        return finalResults;
    }

    private static void printResults(String results1, String results2, String textType, double pValor, double alpha, boolean result){
        String[] tokens1 = results1.split("\\.");
        String[] tokens2 = results2.split("\\.");
        String aceptar = "No hay diferencias notables entre emabos test. La hipótesis nula se acepta.";
        String rechazar = "Hay una diferencia notable entre ambos tests. La hipotesis nula se rechaza.";

        System.out.println("Test: " + textType);
        System.out.println("Queries usadas en el test 1: " + tokens1[5]);
        System.out.println("Queries usadas en el test 2: " + tokens2[5]);
        System.out.println("Métrica y corte: " + tokens1[6]);
        System.out.println("Alpha: " + alpha);
        System.out.println("P-valor: " + pValor);
        System.out.println("Resultado del test: "+ (result? rechazar : aceptar));
    }
}
