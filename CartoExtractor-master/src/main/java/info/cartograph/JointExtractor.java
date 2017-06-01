package info.cartograph;

import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import org.apache.commons.cli.*;
import org.joda.time.DateTime;
import org.joda.time.Hours;
import org.joda.time.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.DefaultOptionBuilder;
import org.wikibrain.core.WikiBrainException;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LanguageSet;
import org.wikibrain.pageview.PageViewDao;
import org.wikibrain.sr.SRBuilder;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.utils.WpIOUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Shilad Sen
 */
public class JointExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(SRVectorizer.class);


    private final Map<String, Integer> id2Index;
    private final Env env;
    private final Language lang;
    private final Iterable<CartographVector> vectorIter;
    private final Iterable<CartographVector> jointVectorIter;

    public JointExtractor(Env env, Language lang, Iterable<CartographVector> vectorIter, Iterable<CartographVector> jointIter) throws ConfigurationException, DaoException {
        this.env = env;
        this.lang = lang;
        this.vectorIter = vectorIter;
        this.jointVectorIter = jointIter;
        this.id2Index = new HashMap<String, Integer>();
    }

    public void writeAll(String dir) throws IOException, DaoException {
        readIds(dir + "/ids.tsv");
        List<CartographVector> vectors = new ArrayList<CartographVector>();

        vectors.clear();
        int i = 0;
        for (CartographVector cv : jointVectorIter) {
            if (cv != null) {
                vectors.add(cv);
                assert(id2Index.containsKey(cv.getId()));
            }
            if (i++ % 10000 == 0) {
                LOG.info("Loading joint vectors for page " + i);
            }
        }
        writeVectors(vectors, dir + "/joint-vectors.tsv");
    }


    public void writeVectors(List<CartographVector> vectors, String pathVectors) throws IOException, DaoException {
        BufferedWriter w = WpIOUtils.openWriter(pathVectors);
        w.write("id\tvector\n");
        for (CartographVector cv : vectors) {
            if (id2Index.containsKey(cv.getId())) {
                int index = id2Index.get(cv.getId());
                w.write(index + "");
                for (float x : cv.getVector()) {
                    w.write("\t" + Float.toString(x));
                }
                w.write("\n");
            }
        }
        w.close();
    }

    public void readIds(String path) throws IOException {
        BufferedReader reader = WpIOUtils.openBufferedReader(new File(path));
        String header = reader.readLine();
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            String tokens[] = line.split("\\s+");
            id2Index.put(tokens[1], Integer.valueOf(tokens[0]));
        }
    }

    public static void main(String args[]) throws ConfigurationException, InterruptedException, WikiBrainException, DaoException, IOException {
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
        SRBuilder builder = new SRBuilder(env, metric, env.getDefaultLanguage());
        builder.setDeleteExistingData(false);
        builder.setSkipBuiltMetrics(true);
        builder.build();

        // Ensure enough page views are loaded
        PageViewDao pvd = env.getComponent(PageViewDao.class);
        Map<Language, SortedSet<DateTime>> loaded = pvd.getLoadedHours();
        int toLoad = cmd.hasOption("r") ? Integer.valueOf(cmd.getOptionValue("r")) : 5;
        if (loaded.containsKey(lang)) {
            toLoad -= loaded.get(lang).size();
        }
        if (toLoad > 0) {
            pvd.ensureLoaded(selectRandomIntervals(toLoad), new LanguageSet(lang));
        }
        PagePopularity pop = new PagePopularity(env, lang);
        SRMetric sr = env.getComponent(SRMetric.class, metric, lang);
        Iterable<CartographVector> basicIter = new SRVectorizer(env, pop, sr);

        Iterable<CartographVector> jointIter = new JointVectorizer(env, lang, new File(cmd.getOptionValue("v")), pop, sr);
        JointExtractor ext = new JointExtractor(env, lang, basicIter, jointIter);
        ext.writeAll(output);

    }

    private static List<Interval> selectRandomIntervals(int n) {
        DateTime now = DateTime.now();
        Interval interval = new Interval(now.plusDays(-465), now.plusDays(-100));
        Hours hours = interval.toDuration().toStandardHours();
        ArrayList result = new ArrayList();
        Random random = new Random();

        for(int i = 0; i < n; ++i) {
            int begOffset = random.nextInt(hours.getHours());
            DateTime start = interval.getStart().plusHours(begOffset);
            DateTime end = start.plusHours(1);
            result.add(new Interval(start, end));
        }

        return result;
    }
}
