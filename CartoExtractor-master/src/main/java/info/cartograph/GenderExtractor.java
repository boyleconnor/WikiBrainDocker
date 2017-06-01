package info.cartograph;

import edu.emory.mathcs.backport.java.util.Arrays;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.DefaultOptionBuilder;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.*;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LanguageSet;
import org.wikibrain.core.model.CategoryGraph;
import org.wikibrain.core.model.LocalLink;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.core.model.RawPage;
import org.wikibrain.core.nlp.StringTokenizer;
import org.wikibrain.utils.ParallelForEach;
import org.wikibrain.utils.Procedure;
import org.wikibrain.utils.WpIOUtils;
import org.wikibrain.wikidata.WikidataDao;
import org.wikibrain.wikidata.WikidataEntity;
import org.wikibrain.wikidata.WikidataStatement;
import org.wikibrain.wikidata.WikidataValue;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Shilad Sen
 */
public class GenderExtractor {
    private static final Logger LOG = LoggerFactory.getLogger(GenderExtractor.class);

    public static int FEMALE_SIGN = +1;
    public static int MALE_SIGN = -1;

    public static WikidataEntity WIKIDATA_SEX = new WikidataEntity(WikidataEntity.Type.PROPERTY, 21);
    public static WikidataValue WIKIDATA_MALE = WikidataValue.forItem(6581097);
    public static WikidataValue WIKIDATA_FEMALE = WikidataValue.forItem(6581072);

    public static Set<String> FEMALE_INDICATORS = new HashSet<String>(
            Arrays.asList(new String[] {"women", "woman", "female" }));

    public static Set<String> MALE_INDICATORS = new HashSet<String>(
            Arrays.asList(new String[] {"men", "man", "male" }));


    public static Set<String> MALE_PRONOUNS = new HashSet<String>(
            Arrays.asList(new String[] {"he", "him", "his" }));

    public static Set<String> FEMALE_PRONOUNS = new HashSet<String>(
            Arrays.asList(new String[] {"she", "her", "hers" }));

    private final Env env;
    private final RawPageDao rawPageDao;
    private final LocalCategoryMemberDao catDao;
    private final Language lang;
    private final CategoryGraph catGraph;
    private final WikidataDao wdDao;
    private final UniversalPageDao conceptDao;
    private final LocalPageDao pageDao;
    private final LocalLinkDao linkDao;

    private TIntIntMap wdGenders = new TIntIntHashMap();
    private TIntIntMap catGenders = new TIntIntHashMap();
    private TIntIntMap textGenders = new TIntIntHashMap();

    public GenderExtractor(Env env, Language lang) throws ConfigurationException, DaoException {
        this.env = env;
        this.lang = lang;
        this.catDao = env.getComponent(LocalCategoryMemberDao.class);
        this.linkDao = env.getComponent(LocalLinkDao.class);
        this.rawPageDao = env.getComponent(RawPageDao.class);
        this.conceptDao = env.getComponent(UniversalPageDao.class);
        this.wdDao = env.getComponent(WikidataDao.class);
        this.pageDao =env.getComponent(LocalPageDao.class);
        this.catGraph = catDao.getGraph(lang);

        getWikidataGenders();
//        findGenderedCategories();
//        findTextGenders();
    }

    private void getWikidataGenders() throws DaoException {
        LOG.info("finding gender by wikidata");
        Map<Language, TIntIntMap> map = conceptDao.getAllUnivToLocalIdsMap(new LanguageSet(lang));
        if (!map.containsKey(lang)) {
            throw new IllegalStateException("No concepts found for " + lang + ". Did you run the concept detector?");
        }
        TIntIntMap univ2Local = map.get(lang);

        for (WikidataStatement st : wdDao.getByValue(WIKIDATA_SEX, WIKIDATA_FEMALE)) {
            int localId = univ2Local.get(st.getItem().getId());
            if (localId != 0) {
                wdGenders.adjustOrPutValue(localId, FEMALE_SIGN, FEMALE_SIGN);
            }
        }
        for (WikidataStatement st : wdDao.getByValue(WIKIDATA_SEX, WIKIDATA_MALE)) {
            int localId = univ2Local.get(st.getItem().getId());
            if (localId != 0) {
                wdGenders.adjustOrPutValue(localId, MALE_SIGN, MALE_SIGN);
            }
        }
        LOG.info("found gender for {} pages using wikidata", wdGenders.size());
    }

    private void findGenderedCategories() {
        LOG.info("finding gender by category");
        for (int i = 0; i < catGraph.cats.length; i++) {
            String c = catGraph.cats[i];
            int numMen = countInstances(c, MALE_INDICATORS);
            int numWomen = countInstances(c, FEMALE_INDICATORS);
            int gender = 0;
            if (numMen > 0 && numWomen > 0) {
                System.err.println("BOTH: " + c);
            } else if (numMen > 0) {
                gender = MALE_SIGN;
            } else if (numWomen > 0) {
                gender = FEMALE_SIGN;
            }
            if (gender != 0) {
                for (int pageId : catGraph.catPages[i]) {
                    catGenders.adjustOrPutValue(pageId, gender, gender);
                }
            }
        }
        LOG.info("found gender for {} pages using cat", catGenders.size());
    }

    private void findTextGenders() throws DaoException {
        LOG.info("finding gender by text");
        final ThreadLocal<StringTokenizer> stHolder = new ThreadLocal<StringTokenizer>();
        ParallelForEach.iterate(rawPageDao.get(DaoFilter.normalPageFilter(lang)).iterator(),
                new Procedure<RawPage>() {
                    public void call(RawPage p) throws Exception {
                        stHolder.set(new StringTokenizer());
                        StringTokenizer st = stHolder.get();
                        String text = p.getPlainText(false);
                        if (text.length() > 1000) {
                            text = text.substring(0, 1000);
                        }
                        int gender = 0;
                        List<String> sentences = st.getSentences(lang, text);
                        for (int i = 0; i < Math.min(5, sentences.size()); i++) {
                            for (String w : st.getWords(lang, sentences.get(i))) {
                                if (MALE_PRONOUNS.contains(w)) gender += MALE_SIGN;
                                if (FEMALE_PRONOUNS.contains(w)) gender += FEMALE_SIGN;
                            }
                        }
                        if (gender != 0) textGenders.put(p.getLocalId(), gender);
                    }

                });
        
        LOG.info("found gender for {} pages using text", textGenders.size());
    }

    public String getGender(RawPage page) {
        return null;
    }

    private static int countInstances(String phrase, Set<String> interesting) {
        int n = 0;
        for (String token : phrase.toLowerCase().split("[^a-z0-9A-Z+]")) {
            if (interesting.contains(token)) {
                n++;
            }
        }
        return n;
    }

    public void writeAll(String file) throws DaoException, IOException {
        BufferedWriter writer = WpIOUtils.openWriter(file);
        writer.write(
                "id\t" +
                "men\t" +
                "women\n"
            );

        TIntIntMap numMen = new TIntIntHashMap();
        TIntIntMap numWomen = new TIntIntHashMap();
        for (LocalLink ll : linkDao.get(DaoFilter.normalPageFilter(lang))) {
            int w = wdGenders.get(ll.getDestId());
            if (w > 0) {
                numWomen.adjustOrPutValue(ll.getSourceId(), +1, +1);
            }
            if (w < 0) {
                numMen.adjustOrPutValue(ll.getSourceId(), +1, +1);
            }
        }
        for (LocalPage lp : pageDao.get(DaoFilter.normalPageFilter(lang))) {
            int pw = wdGenders.get(lp.getLocalId());
            int m = 0, w = 0;
            if (pw > 0) {
                w = 10000;
            } else if (pw < 0) {
                m = 10000;
            } else {
                m = numMen.get(lp.getLocalId());
                w = numWomen.get(lp.getLocalId());
            }
            if (w != 0 || m != 0) {
                writer.write(lp.getLocalId() + "\t" + m + "\t" + w + "\n");
            }
        }
        writer.close();
    }

    public static void main(String args[]) throws ConfigurationException, DaoException, IOException {
        Options options = new Options();

        // Specify the output directory
        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .withLongOpt("output")
                        .withDescription("output file")
                        .create("o"));

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
        String output = cmd.hasOption("o") ? cmd.getOptionValue("o") : "genders.tsv";

        Env env = new EnvBuilder(cmd).build();
        GenderExtractor ge = new GenderExtractor(env, env.getDefaultLanguage());
        ge.writeAll(output);
    }
}
