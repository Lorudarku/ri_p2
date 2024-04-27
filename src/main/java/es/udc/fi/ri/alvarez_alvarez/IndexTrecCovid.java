package es.udc.fi.ri.alvarez_alvarez;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.*;
import org.apache.lucene.util.*;
import java.io.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

public class IndexTrecCovid {
    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Usage: IndexTrecCovid -openmode <openmode> -index <ruta> -docs <ruta> -indexingmodel <model> [<param>]");
            System.exit(1);
        }

        String openmode = args[1];
        String indexPath = args[3];
        String docsPath = args[5];
        String indexingModel = args[7];
        double lambda = -1;
        double k1 = -1;

        if (indexingModel.equals("jm")) {
            lambda = Double.parseDouble(args[8]);
        } else if (indexingModel.equals("bm25")) {
            k1 = Double.parseDouble(args[8]);
        }

        Directory dir = FSDirectory.open(Paths.get(indexPath));
        Analyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setOpenMode(IndexWriterConfig.OpenMode.valueOf(openmode.toUpperCase()));
        if (indexingModel.equals("jm")) {
            iwc.setSimilarity(new LMJelinekMercerSimilarity(lambda));
        } else if (indexingModel.equals("bm25")) {
            iwc.setSimilarity(new BM25Similarity(k1));
        }

        IndexWriter writer = new IndexWriter(dir, iwc);

        // Parse and index documents
        File docDir = new File(docsPath);
        if (!docDir.exists() || !docDir.canRead()) {
            System.err.println("Document directory '" + docDir.getAbsolutePath() + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        // Indexing logic here

        writer.close();
    }
}
