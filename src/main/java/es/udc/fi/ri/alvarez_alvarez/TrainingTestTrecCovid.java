package es.udc.fi.ri.alvarez_alvarez;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.document.Document;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import org.apache.lucene.store.*;




public class TrainingTestTrecCovid {

    public static String QUERY_FILE = "src/test/resources/trec-covid/queries.jsonl";
    public static String REL_FILE = "src/test/resources/trec-covid/qrels/test.tsv";
    private static String trainingTestPath = "src/main/resources/trainingTest/";

    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.err.println("Usage: TrainingTestTrecCovid -evaljm <int1-int2> <int3-int4> | -evalbm25 <int1-int2> <int3-int4> -cut <n> -metrica <metrica> -index <ruta>");
            System.exit(1);
        }

        // Guardamos las entradas
        String eval_1 = null;
        String eval_2 = null;
        Boolean jm = false;
        Boolean bm25 = false;
        int cut = 0;
        String metrica = null;
        String indexPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-evaljm":
                    eval_1 = args[++i];
                    eval_2 = args[++i];
                    jm = true;
                    break;
                case "-evalbm25":
                    eval_1 = args[++i];
                    eval_2 = args[++i];
                    bm25 = true;
                    break;
                case "-cut":
                    cut = Integer.parseInt(args[++i]);
                    break;
                case "-metrica":
                    metrica = args[++i];
                    break;
                case "-indexin":
                    indexPath = args[++i];
                    break;
                default:
                    throw new IllegalArgumentException("Unknown parameter " + args[i]);
            }
        }

        //comprobamos que el trainingTestPath exista y si no existe lo creamos
        if (!Files.exists(Path.of(trainingTestPath))){
            Files.createDirectory(Path.of(trainingTestPath));
        }

        String[] evalSplit1 = eval_1.split("-");
        String[] evalSplit2 = eval_2.split("-");
        int int1 = Integer.parseInt(evalSplit1[0]);
        int int2 = Integer.parseInt(evalSplit1[1]);
        int int3 = Integer.parseInt(evalSplit2[0]);
        int int4 = Integer.parseInt(evalSplit2[1]);

        Double[] lambdaValues = {0.001, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
        Double[] k1Values = {0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0};
        Double[] values = jm? lambdaValues : k1Values;
        float b = 0.75f;

        Directory dir = FSDirectory.open(Paths.get(indexPath));
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);
        StandardAnalyzer analyzer = new StandardAnalyzer();
        QueryParser parser = new QueryParser("text", analyzer);

        // Training and test logic here

        File csvTraining = new File(trainingTestPath + "TREC-COVID." + (jm ? "jm" : "bm25") + ".training." + int1 + "-" + int2 + ".test." + int3 + "-" + int4 + "." + metrica.toLowerCase() + cut + ".training.csv");
        FileWriter csvTrainingWriter = new FileWriter(csvTraining);


        csvTrainingWriter.write("Valores de entrenamiento " + (jm? "lambda ": "K1 ") + valueToString(values) + "\n");
        csvTrainingWriter.write("Métrica = " + metrica + "@" + cut + "\n");

        List<MyQuery> queries = parseQueriesFromFile(QUERY_FILE, int1, int2);
        List<RelevantDoc> relevantDocs = parseRelDocsFromFile(REL_FILE, int1, int2);

        double bestValue = 0.0;
        double bestMeanValue = 0.0;
        double[][] trainingValues = new double[queries.size()][values.length];
        double[] meanValues = new double[values.length];

        for (int i = 0; i < values.length; i++) {
            System.out.println("lambda = " + values[i]);
            if (jm) searcher.setSimilarity(new LMJelinekMercerSimilarity((lambdaValues[i].floatValue())));
            else searcher.setSimilarity(new BM25Similarity(k1Values[i].floatValue(), b));

            double meanMetricvalue;
            meanMetricvalue = calculateStats(queries, relevantDocs, reader, searcher, parser, trainingValues, i, metrica, cut);
            System.out.println("Meranmetricvalue " + meanMetricvalue);
            meanValues[i] = meanMetricvalue;

            if (meanMetricvalue > bestMeanValue){
                bestMeanValue = meanMetricvalue;
                bestValue = values[i];
            }
        }
        //printMatrix(trainingValues, queries.size(), values.length);
        writeResults(csvTrainingWriter, trainingValues, meanValues, queries, values.length);
        csvTrainingWriter.close();
        reader.close();

        //Parte de Test

        IndexReader readerTest = DirectoryReader.open(dir);
        IndexSearcher searcherTest = new IndexSearcher(readerTest);
        QueryParser parserTest = new QueryParser("text", analyzer);

        File csvTest = new File(trainingTestPath + "TREC-COVID." + (jm ? "jm" : "bm25") + ".training." + int1 + "-" + int2 + ".test." + int3 + "-" + int4 + "." + metrica.toLowerCase() + cut + ".test.csv");
        FileWriter csvTestWriter = new FileWriter(csvTest);

        csvTestWriter.write(String.format("Valor de %s = %f", jm? "lambda":"k1", bestValue) + "\n");
        csvTestWriter.write("Métrica = " + metrica + "@" + cut + "\n");

        List<MyQuery> queriesTest = parseQueriesFromFile(QUERY_FILE, int3, int4);
        List<RelevantDoc> relevantDocsTest = parseRelDocsFromFile(REL_FILE, int3, int4);

        double[][] testValues = new double[queriesTest.size()][1];

        if (jm) searcher.setSimilarity(new LMJelinekMercerSimilarity(((float) bestValue)));
        else searcher.setSimilarity(new BM25Similarity((float) bestValue, b));

        double[] testMeanValue = new double[1];
        testMeanValue[0] = calculateStats(queriesTest, relevantDocsTest, readerTest, searcherTest, parserTest, testValues, 0, metrica, cut);

        //printMatrix(testValues, queriesTest.size(), 1);
        writeResults(csvTestWriter, testValues, testMeanValue, queriesTest, 1);

        readerTest.close();
        csvTestWriter.close();
    }

    private static String valueToString(Double[] values){

        String valuesString = "{ ";
        for ( int i = 0; i < values.length; i++){
            valuesString = valuesString + values[i] + ", ";
        }

        valuesString = valuesString + "}";
        return valuesString;
    }

    private static double calculateStats(List<MyQuery> queries, List<RelevantDoc> relevantDocs, IndexReader reader, IndexSearcher searcher,
                                         QueryParser parser,  double[][] values, int pos, String metrica, int cut) throws ParseException, IOException {

        double promedio = 0;
        int cont = 0;
        int queriesNum = 0;

        for (MyQuery myQuery: queries){
            Query query = parser.parse(QueryParser.escape(myQuery.getQuery()));
            TopDocs topDocs = searcher.search(query, cut);

            //System.out.println(query);
            //System.out.println("Scoredocs recovered" + topDocs.scoreDocs.length);
            //System.out.println("Query" + myQuery.getQqueryID()+ ": " + myQuery.getQuery());

            int relevantsForQuery = 0;
            int relevantsRecovered = 0;
            double acumulatePrecisions = 0;
            double metricvalue = 0;
            double RR = 0;

            //Calculo del numero de documentos relevantes para la query
            for (RelevantDoc doc: relevantDocs){
                if (doc.getQueryID() == myQuery.getQqueryID()) relevantsForQuery++;
            }

            //Calculo de documentos relevantes recuperados, precisión y RR
            for (int j = 0; j<topDocs.scoreDocs.length; j++) {
                //System.out.println("Doc ID: " + topDocs.scoreDocs[j].doc);
                Document document = reader.document(topDocs.scoreDocs[j].doc);
                String docCorpusID = document.get("id");

                for (RelevantDoc doc: relevantDocs){
                    if (doc.getQueryID()== myQuery.getQqueryID() && docCorpusID.equals(doc.getCorpusID())){
                        relevantsRecovered++;
                        acumulatePrecisions += (double) relevantsRecovered / (j+1);
                        if (RR == 0) RR = (double) 1/(j+1);
                    }
                }
            }

            if (relevantsRecovered != 0) queriesNum++;

            System.out.println(relevantsRecovered);
            System.out.println(relevantsForQuery);
            switch (metrica){
                case "P": //Se calcula P
                    metricvalue = (double) relevantsRecovered / Math.min(cut, topDocs.scoreDocs.length);;
                    break;
                case "R": //Se calcula R
                    metricvalue = (double) relevantsRecovered / relevantsForQuery;
                    break;
                case "MRR": //Se calcula el RR
                    metricvalue = RR;
                    break;
                case "MAP": //Se calcula el AP
                    metricvalue = relevantsRecovered == 0? 0: acumulatePrecisions / relevantsRecovered;
            }
            //System.out.println("lamda:" + LambdaValues[pos] + "\t" + "query:" + myQuery.getQqueryID() + "\t" + "values = "+metricvalue);
            promedio += metricvalue;
            values[cont][pos] = metricvalue;
            cont++;
        }

        return queriesNum == 0? 0: promedio / queriesNum;
    }

    private static void printMatrix (double[][] trainingValues, int rows, int columns){

        for (int i = 0; i<rows; i++){
            System.out.println("Query" + i);
            for (int j = 0; j<columns; j++){
                System.out.println(trainingValues[i][j]);
            }
            System.out.println("\n");
        }
    }
    private static void writeResults (FileWriter writer, double[][] values, double[] meanValues, List<MyQuery> queries, int columns) throws IOException {

        for (int i = 0; i< queries.size(); i++){
            //writer.write("Query " + queries.get(i).getQqueryID());
            for (int j = 0; j<columns; j++){
                writer.write((values[i][j]) + "\t");
            }
            writer.write("\n");
        }
        for (double meanValue : meanValues) {
            writer.write(meanValue + "\t");
        }
    }

    private static List<MyQuery> parseQueriesFromFile(String filePath, int startQuery, int endQuery) {
        List<MyQuery> queries = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int queryID = 0;

            // Iterar sobre cada línea del archivo JSONL
            while ((line = br.readLine()) != null) {
                queryID++;

                // Verificar si la query está dentro del rango especificado
                if (queryID >= startQuery && queryID <= endQuery) {
                    // Parsear la línea como una query y agregarla a la lista de queries
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode entry = mapper.readTree(line);
                    JsonNode metadata = entry.get("metadata");
                    String query = metadata.get("query").asText();

                    queries.add(new MyQuery(queryID, query));
                }

                // Salir del bucle si ya hemos parseado todas las queries del rango
                if (queryID > endQuery) break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return queries;
    }

    private static List<RelevantDoc> parseRelDocsFromFile(String filePath, int startQuery, int endQuery) {
        List<RelevantDoc> relevants = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            br.readLine(); // Saltar la primera línea
            // Iterar sobre cada línea del archivo
            while ((line = br.readLine()) != null) {
                String[] tokens = line.split("\t");
                int queryID = Integer.parseInt(tokens[0]);
                int score = Integer.parseInt(tokens[2]);

                // Verificar si la query está dentro del rango especificado
                if (queryID >= startQuery && queryID <= endQuery && score >= 1) {
                    String corpusID = tokens[1];
                    relevants.add(new RelevantDoc(queryID, corpusID, score));
                }
                if (queryID > endQuery) break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return relevants;
    }

    static class MyQuery {
        private final int queryID;
        private final String query;

        MyQuery(int qqueryID, String query) {
            this.queryID = qqueryID;
            this.query = query;
        }

        public int getQqueryID() {
            return queryID;
        }

        public String getQuery() {
            return query;
        }

        @Override
        public String toString() {
            return "MyQuery{" +
                    "qqueryID=" + queryID +
                    ", query='" + query + '\'' +
                    '}';
        }
    }

    static class RelevantDoc {

        private final int queryID;
        private final String corpusID;
        private final int score;

        RelevantDoc(int queryID, String docID, int score) {
            this.queryID = queryID;
            this.corpusID = docID;
            this.score = score;
        }

        public int getQueryID() {
            return queryID;
        }

        public String getCorpusID() {
            return corpusID;
        }

        public int getScore() {
            return score;
        }

        @Override
        public String toString() {
            return "RelevantDoc{" +
                    "queryID=" + queryID +
                    ", docID='" + corpusID + '\'' +
                    ", score=" + score +
                    '}';
        }
    }
}
