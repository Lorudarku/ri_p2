package es.udc.fi.ri.alvarez_alvarez;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class SearchEvalTrecCovid {
    private static String searchModel = null;
    private static String param = null;
    private static String cut = null;
    private static String top = null;
    //Para Jelinek-Mercer
    private static Float lambda = null;
    //Para BM25
    private static Float k1 = null;
    private static Float b = null;
    //Rango de queries
    private static String queryRange = null;
    //Rutas
    private static String indexPath = null;
    private static String searchEvalPath = "src/main/resources/searchEval/";
    private static String trecCovidPath = "src/test/resources/trec-covid";

    private static String relevantDocsPath = "src/test/resources/trec-covid/qrels/test.tsv";

    //Metricas globales
    private static double promPAtN = 0.0;
    private static double promRecallAtN = 0.0;
    private static double mrr = 0.0;
    private static double mapAtN = 0.0;
    private static int numQueries = 0; //Solo las queries con documentos relevantes
    public static void main(String[] args) throws Exception {
        if (args.length < 7) {
            System.err.println("Usage: SearchEvalTrecCovid -search <model> <param> -index <ruta> -cut <n> -top <m> -queries <range>");
            System.exit(1);
        }

        for (int i=0; i<args.length; i++){
            switch (args[i]){
                case "-search": //search, indica el modelo de RI para la búsqueda y el valor del parámetro (para bm25 se usará el valor por defecto b=0.75)
                    searchModel = args[++i];
                    param = args[++i];
                    break;
                case "-index": //ruta de la carpeta que contiene el índice
                    indexPath = args[++i];
                    break;
                case "-cut": //cut n indica el corte en el ranking para el cómputo de las metricas P, R, y AP
                    cut = args[++i];
                    break;
                case "-top": //visualiza el top m de documentos del ranking
                    top = args[++i];
                    break;
                case "-queries": //all | int1 | int1-int2 (lanza y evalua la query int1, las queries en el rango int1-int2, ambas inclusive, o todas la queries)
                    queryRange = args[++i];
                    break;
            }
        }

        // Validar entradas
        if (!searchModel.equals("jm") && !searchModel.equals("bm25")) {
            System.err.println("Error: searchModel must be 'jm' or 'bm25'");
            System.exit(1);
        }
        //comprobamos que el indexPath exista
        if (!Files.exists(Path.of(indexPath))){
            System.err.println("Error: indexPath does not exist");
            System.exit(1);
        }
        //comprobamos que el searchEvalPath exista y si no existe lo creamos
        if (!Files.exists(Path.of(searchEvalPath))){
            Files.createDirectory(Path.of(searchEvalPath));
        }

        try {
            Directory dir = FSDirectory.open(Paths.get(indexPath));
            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);
            StandardAnalyzer analyzer = new StandardAnalyzer();

            QueryParser qParser = new QueryParser("text", analyzer);

            // Definir el modelo de búsqueda
            if (searchModel.equals("jm")) {
                lambda = Float.parseFloat(param);
                searcher.setSimilarity(new LMJelinekMercerSimilarity(lambda));
            } else if (searchModel.equals("bm25")) {
                k1 = Float.parseFloat(param);
                b = 0.75f; // valor por defecto para b
                searcher.setSimilarity(new BM25Similarity(k1, b));
            }

            // Ruta del archivo con las queries
            Path queryPath = Path.of((trecCovidPath + "/queries.jsonl")); //Documento con queries

            //Creamos un diccionario con clave el id de la query y valor la query
            HashMap<String, String> queriesDict = getQueries(queryPath);

            // Escribir top m documentos en archivo .txt
            String fileNameTxt = searchEvalPath + "TREC-COVID." + searchModel + "." + top + ".hits." + (searchModel.equals("jm") ? "lambda" : "k1") + "." + param + ".q" + queryRange + ".txt";
            //Creamos un writer para escribir los resultados en un archivo
            PrintWriter writerTxt = new PrintWriter(fileNameTxt);
            //Escribir cut n en archivo .cvs
            String fileNameCsv = searchEvalPath + "TREC-COVID." + searchModel + "." + cut + ".cut." + (searchModel.equals("jm") ? "lambda" : "k1") + "." + param + ".q" + queryRange + ".csv";
            PrintWriter writerCsv = new PrintWriter(fileNameCsv);

            //Cabeceras del archivo .csv
            writerCsv.println("Query ID ||| P@" + cut + " ||| Recall@" + cut + " ||| RR ||| MAP@" + cut);

            //Procesamos todas las queries del diccionario
            for (Map.Entry<String, String> entry : queriesDict.entrySet()) {
                String id = entry.getKey();
                String query = entry.getValue();
                processQuery(searcher, id, query, writerTxt, writerCsv);
            }

            //Calcular las métricas globales
            promPAtN = promPAtN / numQueries;
            promRecallAtN = promRecallAtN / numQueries;
            mrr = mrr / numQueries;
            mapAtN = mapAtN / numQueries;

            //Imprimir las métricas globales
            writerTxt.println("/////////////////////////////////////////////////////////////////////////////////////////");
            writerTxt.println("/////////////////////////////////////////////////////////////////////////////////////////");
            writerTxt.println("--Global Metrics--");
            writerTxt.println("                  P@" + cut + ": " + promPAtN);
            writerTxt.println("                  Recall@" + cut + ": " + promRecallAtN);
            writerTxt.println("                  MRR: " + mrr);
            writerTxt.println("                  MAP@" + cut + ": " + mapAtN);
            writerTxt.println("/////////////////////////////////////////////////////////////////////////////////////////");
            writerTxt.println("/////////////////////////////////////////////////////////////////////////////////////////");

            System.out.println("Search Evaluation finished successfully");

            //Esperamos a que se cierre el writer
            writerTxt.close();
            writerCsv.close();
            //Cerramos el reader
            reader.close();
        } catch (IOException e) {
            throw e;
        }
    }

    //Procesar la query y escribir los resultados en un archivo
    private static void processQuery(IndexSearcher searcher, String id, String query, PrintWriter writerTxt, PrintWriter writerCsv) throws ParseException, IOException {
        // Ejecutar la búsqueda
        Query q = new QueryParser("text", new StandardAnalyzer()).parse(query);
        TopDocs results = searcher.search(q, Integer.parseInt(top));
        ScoreDoc[] scoreHits = results.scoreDocs; //Son los documentos recuperados por la búsqueda

        // Inicializar ObjectMapper de Jackson
        //Obtenemos los juicios de relevancia para la query con ese id
        List<String> relevantDocs = getRelevantDocsForQuery(id);
        //Extraemos los documentos recuperados por la búsqueda
        List<String> retrievedDocs = getRetrievedDocsForQuery(searcher, scoreHits, relevantDocs);

        //Solo incrementamos el número de queries si hay documentos recuperados
        if (!retrievedDocs.isEmpty()) {
            numQueries++;
        } else { //Si no hay documentos relevantes recuperados, no se calculan las métricas
            return;
        }

        //Imprimir los resultados en un archivo .csv
        //#############################################################################################################################################################################################################################################################################################
        writerCsv.print(id + " ||| ");

        //Imprimir los resultados en un archivo .txt
        writerTxt.println("///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////");
        writerTxt.println("Query ID: " + id);
        writerTxt.println("Query: " + query);
        writerTxt.println("Relevant Docs: " + relevantDocs);
        writerTxt.println("Num Relevant Docs: " + relevantDocs.size());
        writerTxt.println("Retrieved Docs: " + retrievedDocs);
        writerTxt.println("Num Retrieved Docs: " + retrievedDocs.size());
        writerTxt.println("--------------------------------------------------- Top " + top + " retrieved documents for query " + id + ": ---------------------------------------------------");
        for (int i = 0; i < scoreHits.length; i++) {
            int docId = scoreHits[i].doc;
            Document doc = searcher.doc(docId);
            // Para cada documento, se visualizan todos los campos del índice (id, title, text, url, pubmed_id), el score y una marca que diga si es relevante o no
            if (relevantDocs.contains(doc.get("id"))) {
                String lineaAPrintear = "Document " + (i + 1) + " ||| id: " + doc.get("id") + " ||| title: " + doc.get("title") + " ||| text: " + doc.get("text") + " ||| url: " + doc.get("url") + " ||| pubmed_id: " + doc.get("pubmed_id") + " ||| Score: " + scoreHits[i].score + " ||| Relevant";
                writerTxt.println(lineaAPrintear);
            } else {
                String lineaAPrintear = "Document " + (i + 1) + " ||| id: " + doc.get("id") + " ||| title: " + doc.get("title") + " ||| text: " + doc.get("text") + " ||| url: " + doc.get("url") + " ||| pubmed_id: " + doc.get("pubmed_id") + " ||| Score: " + scoreHits[i].score + " ||| Not Relevant";
                writerTxt.println(lineaAPrintear);
            }
        }
        writerTxt.println("--------------------------------------------------- Metrics for query " + id + ": -------------------------------------------------------------------------------");
        evaluateMetrics(relevantDocs, retrievedDocs, writerTxt, writerCsv);
        writerTxt.println("///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////");
    }

    //Extraer los documentos recuperados por la búsqueda
    private static List<String> getRetrievedDocsForQuery(IndexSearcher searcher, ScoreDoc[] scoreHits, List<String> relevantDocs) throws IOException {
        List<String> retrievedDocs = new ArrayList<>();
        for (ScoreDoc scoreDoc : scoreHits) {
            Document doc = searcher.doc(scoreDoc.doc);
            //System.out.println("Añadiendo documento: " + doc.get("id"));
            if (relevantDocs.contains(doc.get("id"))) {
                retrievedDocs.add(doc.get("id"));
            }
        }
        return retrievedDocs;
    }

    //Almacena los juicios de relevancia para una query en una lista
    private static List<String> getRelevantDocsForQuery(String queryId) throws IOException {
        List<String> relevantDocs = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Path.of(relevantDocsPath), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Dividir la línea en sus componentes
                String[] parts = line.split("\t");
                if (parts.length >= 3) {
                    String currentQueryId = parts[0];
                    if (currentQueryId.equals(queryId)) {
                        // La línea corresponde a la query actual
                        if (parts[2].equals("1") || parts[2].equals("2")) { //es documento relevante
                            relevantDocs.add(parts[1]);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return relevantDocs;
    }

    //Calcula las métricas de evaluación
    private static void evaluateMetrics(List<String> relevantDocs, List<String> retrievedDocs, PrintWriter writerTxt, PrintWriter writerCsv) throws IOException {
        // Supongamos que n es el valor de corte para P@n, Recall@n y AP@n
        int n = Integer.parseInt(cut); // El valor de n para P@n, Recall@n y AP@n

        //////////////////////////////////////////////// MÉTRICAS QUERYS INDIVIDUALES

        // P@n = (Número de documentos relevantes en los primeros n documentos recuperados) / n
        int relevantCount = 0;
        for (int i = 0; i < n && i < retrievedDocs.size(); i++) {
            if (relevantDocs.contains(retrievedDocs.get(i))) {
                relevantCount++;
            }
        }
        double pAtN = (double) relevantCount / n;

        // Recall@n = (Número de documentos relevantes en los primeros n documentos recuperados) / (Número total de documentos relevantes)
        double recallAtN = (double) relevantCount / relevantDocs.size();

        // RR = 1 / rank del primer documento relevante
        double rr = 0.0;
        for (int i = 0; i < retrievedDocs.size(); i++) {
            if (relevantDocs.contains(retrievedDocs.get(i))) {
                rr = 1.0 / (i + 1);
                break;
            }
        }

        // AP@n = (Sumatoria de los valores de Precisión en los cortes k donde el k-ésimo documento es relevante) / (Número total de documentos relevantes)
        double sumPrecisions = 0.0;
        relevantCount = 0;
        for (int i = 0; i < n && i < retrievedDocs.size(); i++) { // Iterar sobre los primeros n documentos recuperados
            if (relevantDocs.contains(retrievedDocs.get(i))) { // Si el documento es relevante
                relevantCount++;
                sumPrecisions += (double) relevantCount / (i + 1);
            }
        }
        double apAtN = sumPrecisions / relevantDocs.size();

        //#############################################################################################################################################################################################################################################################################################
        //Imprimir las métricas en el archivo .csv
        writerCsv.print(pAtN + " ||| ");
        writerCsv.print(recallAtN + " ||| ");
        writerCsv.print(rr + " ||| ");
        writerCsv.println(apAtN);

        // Imprimir las métricas
        writerTxt.println("                  P@" + n + ": " + pAtN);
        writerTxt.println("                  Recall@" + n + ": " + recallAtN);
        writerTxt.println("                  RR: " + rr);
        writerTxt.println("                  AP@" + n + ": " + apAtN);

        //Sumamos las métricas para calcular las métricas globales
        promPAtN += pAtN;
        promRecallAtN += recallAtN;
        mrr += rr;
        mapAtN += apAtN;

    }


    //Procesar el documento con las queries para extraerlas utilizando el rango especificado
    private static HashMap<String, String> getQueries(Path path) throws IOException{

        ObjectMapper mapper = new ObjectMapper();
        File file = path.toFile();
        Scanner scanner = new Scanner(file);
        HashMap<String, String> queriesDict = new HashMap<String, String>();

        //Si queryRange es all, procesamos todas las queries
        if (queryRange.equals("all")) {
            while (scanner.hasNextLine()) {
                String jsonString = scanner.nextLine();
                JsonNode jsonObject = mapper.readTree(jsonString);

                String id = jsonObject.get("_id").asText();
                String query = jsonObject.get("metadata").get("query").asText();

                //Guardamos el id y la query en un diccionario
                queriesDict.put(id, query);

                System.out.println("ID: " + id + ", Query: " + query);
            }
        } else if (queryRange.contains("-")) {
            //Si queryRange es un rango de queries, procesamos las queries en ese rango
            String[] range = queryRange.split("-");
            int start = Integer.parseInt(range[0]);
            int end = Integer.parseInt(range[1]);

            while (scanner.hasNextLine()) {
                String jsonString = scanner.nextLine();
                JsonNode jsonObject = mapper.readTree(jsonString);

                String id = jsonObject.get("_id").asText();
                String query = jsonObject.get("metadata").get("query").asText();

                if (Integer.parseInt(id) >= start && Integer.parseInt(id) <= end) {
                    //Guardamos el id y la query en un diccionario
                    queriesDict.put(id, query);

                    System.out.println("ID: " + id + ", Query: " + query);
                }
            }
        } else if (queryRange.matches("\\d+")) {
            //Si queryRange es un solo entero, procesamos solo esa query
            while (scanner.hasNextLine()) {
                String jsonString = scanner.nextLine();
                JsonNode jsonObject = mapper.readTree(jsonString);

                String id = jsonObject.get("_id").asText();
                String query = jsonObject.get("metadata").get("query").asText();

                if (Integer.parseInt(id) == Integer.parseInt(queryRange)) {
                    //Guardamos el id y la query en un diccionario
                    queriesDict.put(id, query);

                    System.out.println("ID: " + id + ", Query: " + query);
                }
            }
        } else {
            System.err.println("Error: queryRange must be 'all', 'int1', or 'int1-int2'");
            System.exit(1);
        }

        scanner.close();

        return queriesDict;
    }
}
