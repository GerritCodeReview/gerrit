// Copyright (C) 2014 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.lucene;

import org.apache.lucene.analysis.*;
import org.apache.lucene.analysis.charfilter.MappingCharFilter;
import org.apache.lucene.analysis.charfilter.NormalizeCharMap;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.analysis.util.StopwordAnalyzerBase;
import org.apache.lucene.analysis.util.WordlistLoader;
import org.apache.lucene.util.Version;

import java.io.IOException;
import java.io.Reader;

public final class QueryAnalyzer extends StopwordAnalyzerBase {

  /** Default maximum allowed token length */
  public static final int DEFAULT_MAX_TOKEN_LENGTH = 255;

  private int maxTokenLength = DEFAULT_MAX_TOKEN_LENGTH;

  /** An unmodifiable set containing some common English words that are usually not
  useful for searching. */
  public static final CharArraySet STOP_WORDS_SET = StopAnalyzer.ENGLISH_STOP_WORDS_SET;

  /** Builds an analyzer with the given stop words.
   * @param matchVersion Lucene version to match See {@link
   * <a href="#version">above</a>}
   * @param stopWords stop words */
  public QueryAnalyzer(Version matchVersion, CharArraySet stopWords) {
    super(matchVersion, stopWords);
  }

  /** Builds an analyzer with the default stop words ({@link
   * #STOP_WORDS_SET}).
   * @param matchVersion Lucene version to match See {@link
   * <a href="#version">above</a>}
   */
  public QueryAnalyzer(Version matchVersion) {
    this(matchVersion, STOP_WORDS_SET);
  }

  /** Builds an analyzer with the stop words from the given reader.
   * @see WordlistLoader#getWordSet(Reader, Version)
   * @param matchVersion Lucene version to match See {@link
   * <a href="#version">above</a>}
   * @param stopwords Reader to read stop words from */
  public QueryAnalyzer(Version matchVersion, Reader stopwords) throws IOException {
    this(matchVersion, loadStopwordSet(stopwords, matchVersion));
  }

  /**
   * Set maximum allowed token length.  If a token is seen
   * that exceeds this length then it is discarded.  This
   * setting only takes effect the next time tokenStream or
   * tokenStream is called.
   */
  public void setMaxTokenLength(int length) {
    maxTokenLength = length;
  }

  /**
   * @see #setMaxTokenLength
   */
  public int getMaxTokenLength() {
    return maxTokenLength;
  }

  @Override
  protected TokenStreamComponents createComponents(final String fieldName, final Reader reader) {
    final StandardTokenizer src = new StandardTokenizer(matchVersion, reader);
    src.setMaxTokenLength(maxTokenLength);
    TokenStream tok = new StandardFilter(matchVersion, src);
    tok = new LowerCaseFilter(matchVersion, tok);
    tok = new StopFilter(matchVersion, tok, stopwords);
    return new TokenStreamComponents(src, tok) {
      @Override
      protected void setReader(final Reader reader) throws IOException {
        src.setMaxTokenLength(QueryAnalyzer.this.maxTokenLength);
        super.setReader(reader);
      }
    };
  }

  @Override
  protected Reader initReader(String fieldName, Reader reader) {
    NormalizeCharMap.Builder builder = new NormalizeCharMap.Builder();
    builder.add(".", " ");
    builder.add("_", " ");
    NormalizeCharMap normMap = builder.build();
    return new MappingCharFilter(normMap, reader);
  }
}
