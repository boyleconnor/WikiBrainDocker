package info.cartograph;

import gnu.trove.map.TIntIntMap;
import gnu.trove.set.TIntSet;
import org.apache.commons.collections15.Transformer;
import org.apache.commons.collections15.iterators.TransformIterator;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalLinkDao;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.dao.UniversalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LanguageSet;
import org.wikibrain.core.model.LocalLink;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.matrix.DenseMatrix;
import org.wikibrain.matrix.DenseMatrixRow;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.sr.vector.DenseVectorSRMetric;
import org.wikibrain.utils.WpIOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Vectorizes Page Navigation Data from the Wikimedia Foundation.
 *
 * https://figshare.com/articles/Wikipedia_Vectors/3146878
 *
 * @author Shilad Sen
 */
public class JointVectorizer implements Iterable<CartographVector> {
    private static final Logger LOG = LoggerFactory.getLogger(JointVectorizer.class);
    private final Env env;
    private final Language lang;
    private final PagePopularity pop;
    private final LocalPageDao pageDao;
    private final LocalLinkDao linkDao;
    private final File file;
    private final UniversalPageDao univDao;
    private final TIntIntMap concept2Id;
    private final DenseVectorSRMetric metric;
    private final DenseMatrix matrix;
    private final TIntSet validIds;
    private int vectorLength = -1;

    public JointVectorizer(Env env, Language lang, File file, PagePopularity pop, SRMetric metric) throws ConfigurationException, DaoException {
        this(env, lang, file, pop, metric, null);
    }

    public JointVectorizer(Env env, Language lang, File file, PagePopularity pop, SRMetric metric, TIntSet validIds) throws ConfigurationException, DaoException {
        this.env = env;
        this.file = file;
        this.lang = lang;
        this.metric = (DenseVectorSRMetric)metric;
        this.validIds = validIds;
        this.matrix = ((DenseVectorSRMetric) metric).getGenerator().getFeatureMatrix();
        this.univDao = env.getComponent(UniversalPageDao.class);
        this.concept2Id = univDao.getAllUnivToLocalIdsMap(new LanguageSet(lang)).get(lang);
        this.pageDao = env.getComponent(LocalPageDao.class);
        this.linkDao = env.getComponent(LocalLinkDao.class);
        this.pop = pop;
    }

    public Iterator<CartographVector> iterator() {
        BufferedReader reader;
        try {
            reader = WpIOUtils.openBufferedReader(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final AtomicInteger lineNum = new AtomicInteger();
        return new TransformIterator<String, CartographVector>(
                new LineIterator(reader),
                new Transformer<String, CartographVector>() {
                    public CartographVector transform(String line) {
                        int n = lineNum.getAndIncrement();
                        if (n % 100000 == 0) {
                            LOG.info("Processing line " + n);
                        }
                        // Read header header
                        if (vectorLength < 0) {
                            vectorLength = Integer.valueOf(line.split("\\s+")[1]);
                        } else {
                            try {
                                return makeVector(line);
                            } catch (DaoException e) {
                                LOG.warn("Error when processing line " +
                                        StringEscapeUtils.escapeJavaScript(line), e);
                            } catch (IOException e) {
                                LOG.warn("Error when processing line " +
                                        StringEscapeUtils.escapeJavaScript(line), e);
                            }
                        }
                        return null;
                    }
                }
        );
    }

    protected CartographVector makeVector(String line) throws DaoException, IOException {
        String tokens[] = line.trim().split(" ");
        if (tokens.length == 0) {
            LOG.warn("Empty line encountered!");
            return null;
        }
        assert(vectorLength > 0);
        if (tokens.length - 1 != vectorLength) {
            LOG.warn("Invalid vector length for {}. Expected {}, found {}.",
                    tokens[0], vectorLength, tokens.length - 1);
            return null;
        }

        String strId = tokens[0];
        if (strId.length() == 0 || !strId.startsWith("Q")) {
            return null;
        }
        int itemId = Integer.valueOf(strId.substring(1));
        if (!concept2Id.containsKey(itemId)) {
            return null;
        }
        int pageId = concept2Id.get(itemId);
        if (validIds != null && !validIds.contains(pageId)) {
            return null;
        }

        LocalPage page = pageDao.getById(lang, pageId);
        DenseMatrixRow row = matrix.getRow(pageId);
        if  (row == null) {
            return null;
        }

        float v1[] = new float[vectorLength];
        for (int i = 0; i < vectorLength; i++) {
            v1[i] = Float.valueOf(tokens[i+1]);
        }
        float v2[] = row.getValues();
        unitize(v1);
        unitize(v2);

        float v[] = new float[v1.length + v2.length];
        System.arraycopy(v1, 0, v, 0, v1.length);
        System.arraycopy(v2, 0, v, v1.length, v2.length);

        double pp = pop.getPopularity(pageId);

        List<String> links = new ArrayList<String>();
        for (LocalLink ll : linkDao.getLinks(lang, pageId, true)) {
            links.add("" + ll.getLocalId());
        }

        return new CartographVector(
                page.getTitle().getCanonicalTitle(),
                "" + pageId,
                links.toArray(new String[links.size()]),
                v,
                pp);
    }

    private static void unitize(float []vector) {
        double sum = 0.0;
        for (float x : vector) {
            sum += x * x;
        }
        if (sum == 0.0) {
            return;
        }
        double norm = Math.sqrt(sum);
        for (int i =0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }
}
