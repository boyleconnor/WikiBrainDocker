package info.cartograph;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.apache.commons.cli.*;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.DefaultOptionBuilder;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LocalId;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.core.model.Title;
import org.wikibrain.phrases.PhraseAnalyzer;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.utils.WpIOUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;

/**
 * @author Shilad Sen
 */
public class DomainSpecificLayer {
    private final PhraseAnalyzer resolver;
    private final TIntHashSet wpIds;
    private final Language lang;
    private final LocalPageDao pageDao;

    DomainSpecificLayer(Env env, Language lang, File wpIdMapping) throws ConfigurationException, IOException {
        this.lang = lang;
        pageDao = env.getComponent(LocalPageDao.class);
        resolver = env.getComponent(PhraseAnalyzer.class, "fast-cascading");
        wpIds = new TIntHashSet();
        BufferedReader reader = WpIOUtils.openBufferedReader(wpIdMapping);
        reader.readLine();  // Skip the header
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            String [] tokens = line.split("\t", 2);
            wpIds.add(Integer.valueOf(tokens[1].trim()));
        }
        reader.close();;
    }

    public void createLayer(File input, File output) throws IOException, DaoException {
        BufferedReader reader = WpIOUtils.openBufferedReader(input);
        BufferedWriter writer = WpIOUtils.openWriter(output);
        writer.write(reader.readLine());    // header
        int numLines = 0;
        int numMatches = 0;

        while (true) {
            String line = reader.readLine();
            if (line == null) {
                break;
            }
            numLines++;
            String [] tokens = line.split("\t", 2);
            LinkedHashMap<LocalId, Float> matches = resolver.resolve(lang, tokens[0], 20);
            int pageId = -1;
            for (LocalId lid : matches.keySet()) {
                if (wpIds.contains(lid.getId())) {
                    pageId = lid.getId();
                    break;
                }
            }
            if (pageId >= 0) {
                numMatches++;
                writer.write(pageId + "\t" + tokens[1]);
            }
        }

        System.err.println("matched " + numMatches + " out of " + numLines);

        reader.close();
        writer.close();
    }

    public static void main(String args[]) throws ConfigurationException, IOException, DaoException {
        Options options = new Options();

        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .isRequired()
                        .withLongOpt("input")
                        .withDescription("Input layer dataset")
                        .create("i"));

        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .isRequired()
                        .withLongOpt("output")
                        .withDescription("Output layer dataset")
                        .create("o"));

        options.addOption(
                new DefaultOptionBuilder()
                        .hasArg()
                        .isRequired()
                        .withLongOpt("wpids")
                        .withDescription("Wikipedia id mapping file")
                        .create("w"));

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

        DomainSpecificLayer dsl = new DomainSpecificLayer(env, env.getDefaultLanguage(),
                                                          new File(cmd.getOptionValue("w")));
        dsl.createLayer(new File(cmd.getOptionValue("i")),
                        new File(cmd.getOptionValue("o")));
    }
}
