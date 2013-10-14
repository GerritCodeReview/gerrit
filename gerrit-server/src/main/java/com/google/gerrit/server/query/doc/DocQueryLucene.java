// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.query.doc;

import com.google.common.collect.Lists;
import com.google.gerrit.server.query.doc.QueryDocs.DocResult;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class DocQueryLucene implements DocQueryProcessor {
  private static final Logger log =
      LoggerFactory.getLogger(DocQueryLucene.class);

  private static final int BUFSIZE = 4096;
  // XXX: Magic number for pagination limit. We have too few documents to worry about search result
  // pagination, so we just set a high limit and show all results at once for now.
  private static final int LIMIT = 999;
  private static final String INDEX_PATH = "/Documentation/.index.zip";
  private static final Version LUCENE_VERSION = Version.LUCENE_44;

  public static final String DOC_FIELD = "doc";
  public static final String TITLE_FIELD = "title";
  public static final String URL_FIELD = "url";
  public static final String DOC_OPERATOR = "doc:";
  public static final String OR_OPERATOR = "OR";
  public static final String AND_OPERATOR = "AND";

  private IndexSearcher searcher;
  private QueryParser parser;

  @Inject
  DocQueryLucene() {
    try {
      IndexReader reader = DirectoryReader.open(readIndexDirectory());
      searcher = new IndexSearcher(reader);
      StandardAnalyzer analyzer = new StandardAnalyzer(LUCENE_VERSION);
      parser = new QueryParser(LUCENE_VERSION, DOC_FIELD, analyzer);
    } catch (IOException e) {
      log.error("", e);
      searcher = null;
      parser = null;
    }
  }

  @Override
  public int getLimit() {
    return LIMIT;
  }

  @Override
  public void setLimit(int n) {
    // Do nothing.
  }

  @Override
  public List<DocResult> queryDocs(List<String> origQueries)
      throws OrmException {
    if (parser == null || searcher == null) {
      throw new OrmException("not initialized");
    }
    try {
      // strip out "doc:" operators
      StringBuilder queryLine = new StringBuilder();
      for (String query : origQueries) {
        log.debug(String.format("orig: \"%s\"", query));
        if (query.startsWith(DOC_OPERATOR)) {
          queryLine.append(query.substring(DOC_OPERATOR.length()));
          queryLine.append(" ");
        } else if (query.equalsIgnoreCase(OR_OPERATOR)) {
          queryLine.append(OR_OPERATOR);
          queryLine.append(" ");
        } else if (query.equalsIgnoreCase(AND_OPERATOR)) {
          queryLine.append(AND_OPERATOR);
          queryLine.append(" ");
        }
      }
      log.debug(String.format("line: \"%s\"", queryLine.toString()));
      Query query = parser.parse(queryLine.toString());

      TopDocs results = searcher.search(query, LIMIT + 1);
      ScoreDoc[] hits = results.scoreDocs;
      int totalHits = results.totalHits;

      List<DocResult> out = Lists.newArrayListWithCapacity(totalHits);
      for (int i = 0; i < totalHits; i++) {
        DocResult result = new DocResult();
        Document doc = searcher.doc(hits[i].doc);
        result.url = doc.get(URL_FIELD);
        result.title = doc.get(TITLE_FIELD);
        out.add(result);
      }
      return out;
    } catch (IOException e) {
      throw new OrmException(e);
    } catch (ParseException e) {
      // Generally this means empty query string or something. So instead of throwing a 500 error
      // page, returning empty result sounds more appropriate.
      log.error("", e);
      return Lists.newArrayListWithCapacity(0);
    }
  }

  @Override
  public boolean isDisabled() {
    return false;
  }

  protected Directory readIndexDirectory() throws IOException {
    Directory dir = new RAMDirectory();
    byte[] buffer = new byte[BUFSIZ];
    ZipInputStream zip = new ZipInputStream(
        DocQueryLucene.class.getClass().getResourceAsStream(INDEX_PATH));
    ZipEntry entry;
    while ((entry = zip.getNextEntry()) != null) {
      IndexOutput out = dir.createOutput(entry.getName(), null);
      int count;
      while ((count = zip.read(buffer)) != -1) {
        out.writeBytes(buffer, count);
      }
      out.close();
    }
    zip.close();
    // We must NOT call dir.close(), as DirectoryReader.open() expects a opened directory.
    return dir;
  }
}
