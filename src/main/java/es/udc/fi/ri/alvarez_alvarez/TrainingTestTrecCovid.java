package es.udc.fi.ri.alvarez_alvarez;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import java.nio.file.Paths;

public class TrainingTestTrecCovid {
    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.err.println("Usage: TrainingTestTrecCovid -evaljm <int1-int2> <int3-int4> | -evalbm25 <int1-int2> <int3-int4> -cut <n> -metrica <metrica> -index <ruta>");
            System.exit(1);
        }

        String evalType = args[1];
        String[] evalParams = evalType.split("-");
        String rangeTrain = args[2];
        String rangeTest = args[3];
        int cut = Integer.parseInt(args[5]);
        String metric = args[7];
        String indexPath = args[9];

        Directory dir = FSDirectory.open(Paths.get(indexPath));
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);
        StandardAnalyzer analyzer = new StandardAnalyzer();

        // Training and test logic here

        reader.close();
    }
}
