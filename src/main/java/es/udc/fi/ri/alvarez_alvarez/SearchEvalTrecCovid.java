/*
package es.udc.fi.ri.alvarez_alvarez;


import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import java.io.*;
import java.nio.file.Paths;
import java.util.*;

public class SearchEvalTrecCovid {
    public static void main(String[] args) throws Exception {
        if (args.length < 7) {
            System.err.println("Usage: SearchEvalTrecCovid -search <model> <param> -index <ruta> -cut <n> -top <m> -queries <range>");
            System.exit(1);
        }

        String searchModel = args[1];
        double param = Double.parseDouble(args[2]);
        String indexPath = args[4];
        int cut = Integer.parseInt(args[6]);
        int top = Integer.parseInt(args[8]);
        String queryRange = args[10];

        Directory dir = FSDirectory.open(Paths.get(indexPath));
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);
        StandardAnalyzer analyzer = new StandardAnalyzer();

        QueryParser parser = new QueryParser("text", analyzer);

        // Search and evaluation logic here

        reader.close();
    }
}
*/