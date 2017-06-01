package info.cartograph;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.utils.WpIOUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Shilad Sen
 */
public class DatasetWriter {
    private final List<CartographVector> vectors;
    private final Map<String, Integer> id2Index;

    public DatasetWriter(List<CartographVector> vectors) {
        this(vectors, null, false);
    }

    public DatasetWriter(List<CartographVector> vectors, Map<String, Integer> id2Index, boolean skipMissing) {
        this.vectors = new ArrayList<CartographVector>();
        this.id2Index = (id2Index != null) ? id2Index : new HashMap<String, Integer>();
        Set<String> added = new HashSet<String>();
        for (CartographVector v : vectors) {
            if (v != null) {
                if (!this.id2Index.containsKey(v.getId())) {
                    if (skipMissing) continue;
                    this.id2Index.put(v.getId(), this.id2Index.size() + 1);
                }
                if (!added.contains(v.getId())) {
                    added.add(v.getId());
                    this.vectors.add(v);
                }
            }
        }
    }


    public void writeAll(File outputDir) throws IOException, DaoException {
        if (!outputDir.isDirectory()) outputDir.mkdirs();
        writeIds(new File(outputDir, "ids.tsv"));
        writeTitles(new File(outputDir, "names.tsv"));
        writeVectors(new File(outputDir, "vectors.tsv"));
        writePopularity(new File(outputDir, "popularity.tsv"));
        writeLinks(new File(outputDir, "links.tsv"));
    }


    public void writeTitles(File out) throws IOException, DaoException {
        BufferedWriter w = WpIOUtils.openWriter(out);
        w.write("id\tname\n");
        for (CartographVector v : vectors) {
            w.write(id2Index.get(v.getId()) + "\t" + v.getName()+ "\n");
        }
        w.close();
    }

    public void writeIds(File out) throws IOException, DaoException {
        BufferedWriter w = WpIOUtils.openWriter(out);
        w.write("id\texternalId\n");
        for (CartographVector v : vectors) {
            w.write(id2Index.get(v.getId()) + "\t" + v.getId()+ "\n");
        }
        w.close();
    }

    public void writeVectors(File out) throws IOException, DaoException {
        BufferedWriter w = WpIOUtils.openWriter(out);
        w.write("id\tvector\n");
        for (CartographVector cv : vectors) {
            int index = id2Index.get(cv.getId());
            w.write(index + "");
            for (float x : cv.getVector()) {
                w.write("\t" + Float.toString(x));
            }
            w.write("\n");
        }
        w.close();
    }

    public void writePopularity(File out) throws DaoException, IOException {
        BufferedWriter w = WpIOUtils.openWriter(out);
        w.write("id\tpopularity\n");
        for (CartographVector v : vectors) {
            w.write(id2Index.get(v.getId()) + "\t" + v.getPopularity()+ "\n");
        }
        w.close();
    }

    public void writeLinks(File out) throws DaoException, IOException {
        BufferedWriter w = WpIOUtils.openWriter(out);
        w.write("id\tlinks\n");
        for (CartographVector cv : vectors) {
            int index = id2Index.get(cv.getId());
            w.write(index + "");
            for (String id2 : cv.getLinkIds()) {
                if (id2Index.containsKey(id2)) {
                    w.write("\t" + id2Index.get(id2));
                }
            }
            w.write("\n");
        }
        w.close();
    }

    public Map<String, Integer> getId2Index() {
        return id2Index;
    }
}
