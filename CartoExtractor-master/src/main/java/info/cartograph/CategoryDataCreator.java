package info.cartograph;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.cli.*;
import org.apache.commons.collections.IteratorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.DefaultOptionBuilder;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalCategoryMemberDao;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.dao.UniversalPageDao;
import org.wikibrain.core.dao.sql.CategoryBfs;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LocalId;
import org.wikibrain.core.model.CategoryGraph;
import org.wikibrain.core.model.NameSpace;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.sr.vector.DenseVectorSRMetric;
import org.wikibrain.wikidata.*;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Shilad Sen
 */
public class CategoryDataCreator {

    private static final Logger LOG = LoggerFactory.getLogger(CategoryDataCreator.class);

    private static final int INSTANCE_OF_ID = 31;
    private static final int SUBCLASS_OF_ID = 279;
    private final Language lang;
    private final Env env;
    private final LocalCategoryMemberDao catDao;
    private final CategoryGraph graph;
    private final LocalPageDao pageDao;
    private final PagePopularity pop;
    private final DenseVectorSRMetric metric;
    private final WikidataDao wdDao;
    private final UniversalPageDao univDao;
    private TIntSet pageIds = null;

    public CategoryDataCreator(Env env, Language lang, SRMetric metric) throws ConfigurationException, DaoException {
        this.env = env;
        this.lang = lang;
        this.pop = new PagePopularity(env, lang);
        this.wdDao = env.getComponent(WikidataDao.class);
        this.pageDao = env.getComponent(LocalPageDao.class);
        this.catDao = env.getComponent(LocalCategoryMemberDao.class);
        this.univDao = env.getComponent(UniversalPageDao.class);
        this.graph = catDao.getGraph(lang);
        this.metric = (DenseVectorSRMetric) metric;
    }

    public void create(File wmfFile, File outDir) throws DaoException, ConfigurationException, IOException {
        if (!outDir.isDirectory()) {
            outDir.mkdirs();
        }
        WMFPageNavVectorizer vectorizer = new WMFPageNavVectorizer(env, lang, pop, wmfFile, pageIds);
        List<CartographVector> vectors = new ArrayList<CartographVector>();
        for (CartographVector v : vectorizer) {
            if (v != null) {
                vectors.add(v);
            }
        }

        DatasetWriter writer = new DatasetWriter(vectors);
        writer.writeAll(outDir);
        Map<String, Integer> id2Index = writer.getId2Index();

        vectors.clear();
        SRVectorizer srVectorizer = new SRVectorizer(env, pop, metric);
        for (String id : id2Index.keySet()) {
            vectors.add(srVectorizer.getVector(id));
        }

        DatasetWriter writer2 = new DatasetWriter(vectors, id2Index, true);
        writer2.writeVectors(new File(outDir, "vectors2.tsv"));
    }

    public void setPagesUnderWikidataCategory(String parentType, boolean expand) throws DaoException {
        int pageId = pageDao.getIdByTitle(parentType, lang, NameSpace.ARTICLE);
        if (pageId < 0) {
            throw new IllegalArgumentException("Couldn't find page for " + parentType);
        }
        int wdId = wdDao.getItemId(new LocalId(lang, pageId));
        if (wdId < 0) {
            throw new IllegalArgumentException("Couldn't find wikidata id for " + parentType);
        }
        TIntSet classes = new TIntHashSet();
        classes.add(wdId);
        if (expand) {
            classes.addAll(wdDao.conceptsWithValue("subclass of", WikidataValue.forItem(wdId)));
            LOG.info("expanded " + wdId + " to " + (classes.size() - 1) + " total subclasses");
        }
        TIntSet res = new TIntHashSet();
        for (int wdId2 : classes.toArray()) {
            Set<LocalId> lids = wdDao.pagesWithValue("instance of", WikidataValue.forItem(wdId2), lang);
            for (LocalId l : lids) {
                res.add(l.getId());
            }
        }
        this.pageIds = res;

        LOG.info("found " + res.size() + " pages under " + parentType);

    }

    public void setPagesUnderCategory(String categoryName, int maxPages, TIntSet parentClassIds, int maxInheritance) throws DaoException {
        int catId = pageDao.getIdByTitle(categoryName, lang, NameSpace.CATEGORY);
        CategoryBfs bfs = new CategoryBfs(graph, catId, NameSpace.CATEGORY, lang, maxPages, null, catDao, -1);
        TIntSet results = new TIntHashSet();
        int numPages = 0;
        while (bfs.hasMoreResults()) {
            CategoryBfs.BfsVisited visited = bfs.step();
            for (int pageId : visited.pages.keys()) {
                if (parentClassIds != null) {
                    boolean b = hasParentClass(pageId, parentClassIds, maxInheritance);
//                    if (numPages++ % 100 < 5) System.out.format("%d. %s %s\n", numPages, pageDao.getById(lang, pageId), b);
                    if (!b) {
                        continue;
                    }
                }
                results.add(pageId);
                if (results.size() >= maxPages) {
                    break;
                }
            }
        }
        this.pageIds = results;
    }

    private WikidataStatement getFromIterable(Iterable<WikidataStatement> iterable) {
        WikidataStatement result = null;
        for (WikidataStatement ws : iterable) {
            result = ws;
        }
        return result;
    }

    private TIntIntMap isMatch = new TIntIntHashMap();
    private Map<Integer, Set<Integer>> parents = new HashMap<Integer, Set<Integer>>();
    public boolean hasParentClass(int pageId, TIntSet validParentIds, int maxSteps) throws DaoException {
        int conceptId = univDao.getUnivPageId(lang, pageId);
        if (conceptId < 0) {
            LOG.info("No concept associated with page " + pageId);
            return false;
        }
        WikidataFilter filter = new WikidataFilter.Builder()
                                        .withEntityId(conceptId)
                                        .withEntityType(WikidataEntity.Type.ITEM)
                                        .withPropertyId(INSTANCE_OF_ID)
                                        .build();
        List<WikidataStatement> statements = IteratorUtils.toList(wdDao.get(filter).iterator());
        for (WikidataStatement st : statements) {

            // Do a BFS from this entityType
            int startEntityType = st.getValue().getItemValue();
            TIntSet frontier = new TIntHashSet();
            TIntSet visited = new TIntHashSet();
            frontier.add(startEntityType);

            for (int i = 0; i < maxSteps; i++) {

                TIntSet nextFrontier = new TIntHashSet();

                for (int entityType : frontier.toArray()) {

                    // See if we already know the answer
                    if (isMatch.containsKey(entityType)) {
                        if (isMatch.get(entityType) == 1) {
                            return true;
                        } else {
                            continue;
                        }
                    }

                    // Are we a match?
                    if (validParentIds.contains(entityType)) {
                        isMatch.put(startEntityType, 1);
                        return true;
                    }

                    // Have we already headed down this path?
                    if (visited.contains(entityType)) {
                        continue;
                    }
                    visited.add(entityType);

                    // Add the mapping forthe parent entity
                    if (!parents.containsKey(entityType)) {
                        filter = new WikidataFilter.Builder()
                                .withEntityId(entityType)
                                .withEntityType(WikidataEntity.Type.ITEM)
                                .withPropertyId(SUBCLASS_OF_ID)
                                .build();

                        Set<Integer> s = new HashSet<Integer>();
                        for (WikidataStatement st2 : wdDao.get(filter)) {
                            s.add(st2.getValue().getItemValue());
                        }
                        parents.put(entityType, s);
                    }

                    for (int entityType2 : parents.get(entityType)) {
                        if (!visited.contains(entityType2)) nextFrontier.add(entityType2);
                    }
                }
                frontier = nextFrontier;
            }
            isMatch.put(startEntityType, 0);
        }
        return false;
    }

    public static void main(String args[]) throws ConfigurationException, DaoException, IOException {
        Options options = new Options();

        // Specify the Metrics
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("metric")
                        .withDescription("set a local metric")
                        .create("m"));

        // Specify the output directory
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("output")
                        .withDescription("output directory")
                        .create("o"));

        // Specify the output directory
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("vectors")
                        .withDescription("WMF vector file")
                        .isRequired()
                        .create("v"));

        // Type of of category (movies, businesses, etc.)
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .isRequired()
                        .withLongOpt("category")
                        .withDescription("Name of predifined category")
                        .create("y"));

        // Specify the minimum number of hours worth of pageviews
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("hours")
                        .withDescription("hours worth of page views")
                        .create("r"));

        EnvBuilder.addStandardOptions(options);


        CommandLineParser parser = new PosixParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Invalid option usage: " + e.getMessage());
            new HelpFormatter().printHelp("SRBuilder", options);
            return;
        }
        Env env = new EnvBuilder(cmd).build();

        Language lang = env.getDefaultLanguage();

        String output = cmd.hasOption("o") ? cmd.getOptionValue("o") : ".";


        // Build word2vec if necessary
        String metric = cmd.hasOption("m") ? cmd.getOptionValue("m") : "prebuiltword2vec";
        SRMetric sr = env.getComponent(SRMetric.class, metric, lang);
        CategoryDataCreator cdc = new CategoryDataCreator(env, env.getDefaultLanguage(), sr);

        if (cmd.getOptionValue("y").equals("movies")) {
            cdc.setPagesUnderWikidataCategory("Film", false);
        } else if(cmd.getOptionValue("y").equals("companies")) {
            TIntSet validParentIds = new TIntHashSet();
            validParentIds.add(4830453);    // Business enterprise: https://www.wikidata.org/wiki/Q4830453
            validParentIds.add(6881511);    // Enterprise: https://www.wikidata.org/wiki/Q6881511
            validParentIds.add(783794);     // Company: https://www.wikidata.org/wiki/Q783794

//            cdc.hasParentClass(cdc.pageDao.getIdByTitle("Zantigo", lang, NameSpace.ARTICLE), validParentIds, 4);
            cdc.setPagesUnderCategory("Category:Companies", 100000, validParentIds, 5);
        } else {
            throw new IllegalArgumentException("Unrecognized cateogyr: " + cmd.getOptionValue("y"));
        }
        cdc.create(new File(cmd.getOptionValue('v')), new File(output));
    }
}
