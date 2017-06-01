package info.cartograph;

import org.apache.commons.collections15.Transformer;
import org.apache.commons.collections15.iterators.TransformIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalLinkDao;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.matrix.DenseMatrix;
import org.wikibrain.matrix.DenseMatrixRow;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.sr.vector.DenseVectorSRMetric;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Shilad Sen
 */
public class SRVectorizer implements Iterable<CartographVector> {
    private static final Logger LOG = LoggerFactory.getLogger(SRVectorizer.class);
    private final Env env;
    private final DenseVectorSRMetric metric;
    private final DenseMatrix matrix;
    private final Language lang;
    private final PagePopularity pop;
    private final LocalPageDao pageDao;
    private final LocalLinkDao linkDao;

    public SRVectorizer(Env env, PagePopularity pop, SRMetric metric) throws ConfigurationException, DaoException {
        this.env = env;
        this.metric = (DenseVectorSRMetric)metric;
        this.lang = metric.getLanguage();
        this.matrix = ((DenseVectorSRMetric) metric).getGenerator().getFeatureMatrix();
        this.pageDao = env.getComponent(LocalPageDao.class);
        this.linkDao = env.getComponent(LocalLinkDao.class);
        this.pop = pop;
    }

    public Iterator<CartographVector> iterator() {
        return new TransformIterator<DenseMatrixRow, CartographVector>(
                matrix.iterator(),
                new Transformer<DenseMatrixRow, CartographVector>() {
                    public CartographVector transform(DenseMatrixRow row) {
                        try {
                            return makeVector(row);
                        } catch (DaoException e) {
                            LOG.warn("Error when processing page " + row.getRowIndex(), e);
                            return null;
                        }
                    }
                }
        );
    }

    public CartographVector getVector(String id) throws IOException, DaoException {
        DenseMatrixRow row = matrix.getRow(Integer.valueOf(id));
        return (row == null) ? null : makeVector(row);
    }

    protected CartographVector makeVector(DenseMatrixRow row) throws DaoException {
        LocalPage p = pageDao.getById(lang, row.getRowIndex());
        if (p == null) {
            return null;
        }
        double pp = pop.getPopularity(p.getLocalId());

        List<String> links = new ArrayList<String>();
//        for (LocalLink ll : linkDao.getLinks(lang, p.getLocalId(), true)) {
//            links.add("" + ll.getLocalId());
//        }

        return new CartographVector(
                p.getTitle().getCanonicalTitle(),
                "" + p.getLocalId(),
                links.toArray(new String[links.size()]),
                row.getValues(),
                pp);
    }
}
