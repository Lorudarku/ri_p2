package es.udc.fi.ri.alvarez_alvarez;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.*;
import org.apache.lucene.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.json.JsonMapper;


public class IndexTrecCovid {
    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Usage: IndexTrecCovid -openmode <openmode> -index <ruta> -docs <ruta> -indexingmodel <model> [<param>]");
            System.exit(1);
        }

        String openMode = null;
        String indexPath = null;
        String docsPath = null;
        String indexingModel = null;
        String modelVar = null;

        //Para Jelinek-Mercer
        Float lambda = null;
        //Para BM25
        Float k1 = null;
        Float b = null;

        for (int i=0; i<args.length; i++){
            switch (args[i]){
                case "-openMode": //append, create o create_or_append
                    openMode = args[++i];
                    break;
                case "-index": //ruta de la carpeta que contendrá el índice
                    indexPath = args[++i];
                    break;
                case "-docs": //ruta de la carpeta que contiene documentos, queries y juicios de relevancia
                    docsPath = args[++i];
                    break;
                case "-indexingModel": //jm <lambda> o bm25 <k1> (para bm25 se usará el valor por defecto b=0.75)
                    indexingModel = args[++i];
                    modelVar = args[++i];
                    break;
            }
        }

        // Crear directorios si no existen
        if (!Files.exists(Path.of(indexPath))){
            Files.createDirectory(Path.of(indexPath));
        }

        // Validar entradas
        if (!openMode.equals("append") && !openMode.equals("create") && !openMode.equals("create_or_append")) {
            System.err.println("Error: openMode must be 'append', 'create' or 'create_or_append'");
            System.exit(1);
        }
        if (!indexingModel.equals("jm") && !indexingModel.equals("bm25")) {
            System.err.println("Error: indexingModel must be 'jm' or 'bm25'");
            System.exit(1);
        }
        // Validar parámetros de los modelos de indexación
        if (indexingModel.equals("jm")) {
            if (modelVar.equals("")){
                System.err.println("Error: indexingModel 'jm' must have a parameter");
                System.exit(1);
            } else {
                lambda = Float.parseFloat(modelVar);
            }
        } else if (indexingModel.equals("bm25")) {
            if (modelVar.equals("")){
                System.err.println("Error: indexingmodel 'bm25' must have a parameter");
                System.exit(1);
            } else {
                k1 = Float.parseFloat(modelVar);
                b = 0.75f;
            }
        }


        try{
            // Crear el índice
            System.out.println("Indexing to directory '" + indexPath + "'...");
            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            iwc.setOpenMode(IndexWriterConfig.OpenMode.valueOf(openMode.toUpperCase()));

            //Logica para configurar el modelo de indexación
            if (indexingModel.equals("jm")) {
                iwc.setSimilarity(new LMJelinekMercerSimilarity(lambda));
            } else if (indexingModel.equals("bm25")) {
                iwc.setSimilarity(new BM25Similarity(k1, b));
            }
            //Creación del indexWriter
            IndexWriter writer = new IndexWriter(dir, iwc);

            // Parseamos e indexamos el documento
            Path path = Paths.get(docsPath);
            parseDocument(path, writer);


            writer.close();
        } catch (IOException e) {
            System.err.println("Error opening directory: " + e.getMessage());
            System.exit(1);
        }

    }

    private static void parseDocument(Path path, IndexWriter indexWriter) throws IOException{

        try{
            InputStream stream = Files.newInputStream(path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String line;

            while ((line  = reader.readLine()) != null){
                indexDocument(indexWriter, line);
            }

            reader.close();

        }catch (IOException e){
            throw e;
        }

    }

    private static void indexDocument(IndexWriter indexWriter, String content) throws IOException{

        try {
            // Inicializar ObjectMapper de Jackson
            ObjectMapper objectMapper = new ObjectMapper();

            JsonNode document = objectMapper.readTree(content);

            // Extraer los campos requeridos (id, title, text, url, pubmed_id)
            String id = document.get("id").asText();
            String title = document.get("title").asText();
            String text = document.get("text").asText();
            String url = document.get("url").asText();
            String pubmedId = document.get("pubmed_id").asText();

            System.out.println("ID: " + id);
            System.out.println("Title: " + title);
            System.out.println("Text: " + text);
            System.out.println("URL: " + url);
            System.out.println("PubMed ID: " + pubmedId);
            System.out.println();

        } catch (IOException e){
            throw e;
        }
    }
}
