package info.cartograph;

import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalLinkDao;
import org.wikibrain.core.dao.MetaInfoDao;
import org.wikibrain.core.dao.sql.SqlCache;
import org.wikibrain.core.lang.Language;
import org.wikibrain.pageview.PageViewDao;
import org.wikibrain.pageview.PageViewSqlDao;
import org.wikibrain.utils.ParallelForEach;
import org.wikibrain.utils.Procedure;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;

/**
 * @author Shilad Sen
 */
public class PagePopularity {
    private static final String MEDIAN_VIEWS_KEY = "median_views";
    private static final Logger LOG = LoggerFactory.getLogger(PagePopularity.class);
    private final PageViewDao viewDao;
    private final Env env;
    private final Language lang;
    private final TIntIntMap views;
    private final LocalLinkDao linkDao;
    private final MetaInfoDao metaDao;

    public PagePopularity(Env env, Language lang) throws ConfigurationException, DaoException {
        this.env = env;
        this.lang = lang;
        this.linkDao = env.getComponent(LocalLinkDao.class);
        this.viewDao = env.getComponent(PageViewDao.class);
        this.metaDao = env.getComponent(MetaInfoDao.class);
        this.views = getMedianViews();
    }

    public double getPopularity(int id) {
        return Math.max(1, views.get(id)) * linkDao.getPageRank(lang, id);
    }

    private TIntIntMap getMedianViews() throws DaoException {
        String cachePath = env.getConfiguration().getString("dao.sqlCachePath");
        SqlCache cache = new SqlCache(metaDao, new File(cachePath));
        TIntIntMap medians = (TIntIntMap) cache.get(MEDIAN_VIEWS_KEY);
        if (medians != null) {
            return medians;
        }

        LOG.info("Getting loaded page views.");
        Map<Language, SortedSet<DateTime>> hours = viewDao.getLoadedHours();
        if (!hours.containsKey(lang) || !hours.containsKey(lang)) {
            LOG.warn("No page views loaded for language {}", lang);
            return new TIntIntHashMap();
        }
        LOG.info("Loading {} hours worth of page views", hours.get(lang).size());
        final Map<Integer, TIntList> pageSamples = new HashMap<Integer, TIntList>();
        ParallelForEach.loop(hours.get(lang), 8, new Procedure<DateTime>() {
            public void call(DateTime dt) throws Exception {
                LOG.info("Loading pageviews for hour {}", dt);
                TIntIntMap pv = viewDao.getAllViews(lang, dt.minusMinutes(1), dt.plusMinutes(1));
                synchronized (pageSamples) {
                    for (int pageId : pv.keys()) {
                        if (!pageSamples.containsKey(pageId)) {
                            pageSamples.put(pageId, new TIntArrayList());
                        }
                        pageSamples.get(pageId).add(pv.get(pageId));
                    }
                }
            }
        }, 1);
        medians = new TIntIntHashMap();
        for (int pageId : pageSamples.keySet()) {
            TIntList sample = pageSamples.get(pageId);
            sample.sort();
            medians.put(pageId, sample.get(sample.size() / 2));
        }
        LOG.info("Loaded {} total page views", medians.size());
        cache.put(MEDIAN_VIEWS_KEY, medians);
        return medians;
    }
}
