package org.wikibrain.lucene.tokenizers;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.miscellaneous.SetKeywordMarkerFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.sv.SwedishAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;
import org.tartarus.snowball.ext.SwedishStemmer;
import org.wikibrain.core.lang.Language;
import org.wikibrain.lucene.TokenizerOptions;

/**
 * @author Ari Weiland
 */
public class SwedishTokenizer extends LanguageTokenizer {

    protected SwedishTokenizer(Version version, TokenizerOptions options, Language language) {
        super(version, options, language);
    }

    @Override
    public TokenStream getTokenStream(Tokenizer tokenizer, CharArraySet stemExclusionSet) {
        TokenStream stream = new StandardFilter(matchVersion, tokenizer);
        if (caseInsensitive)
            stream = new LowerCaseFilter(matchVersion, stream);
        if (useStopWords)
            stream = new StopFilter(matchVersion, stream, SwedishAnalyzer.getDefaultStopSet());
        if (useStem) {
            if (!stemExclusionSet.isEmpty())
                stream = new SetKeywordMarkerFilter(stream, stemExclusionSet);
            stream = new SnowballFilter(stream, new SwedishStemmer());
        }
        return stream;
    }
}
