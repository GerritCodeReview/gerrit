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

package com.google.gerrit.server.documentation;

import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.RAMDirectory;

@Singleton
public class QueryDocumentationExecutor {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static Map<String, Float> WEIGHTS =
      ImmutableMap.of(
          Constants.TITLE_FIELD, 2.0f,
          Constants.DOC_FIELD, 1.0f);

  private IndexSearcher searcher;
  private SimpleQueryParser parser;

  public static class DocResult {
    public String title;
    public String url;
    public String content;
  }

  @Inject
  public QueryDocumentationExecutor() {
    try {
      Directory dir = readIndexDirectory();
      if (dir == null) {
        searcher = null;
        parser = null;
        return;
      }
      IndexReader reader = DirectoryReader.open(dir);
      searcher = new IndexSearcher(reader);
      parser = new SimpleQueryParser(new StandardAnalyzer(), WEIGHTS);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Cannot initialize documentation full text index");
      searcher = null;
      parser = null;
    }
  }

  public List<DocResult> doQuery(String q) throws DocQueryException {
    if (!isAvailable()) {
      throw new DocQueryException("Documentation search not available");
    }
    Query query = parser.parse(q);
    try {
      // TODO(fishywang): Currently as we don't have much documentation, we just use MAX_VALUE here
      // and skipped paging. Maybe add paging later.
      TopDocs results = searcher.search(query, Integer.MAX_VALUE);
      ScoreDoc[] hits = results.scoreDocs;
      // TODO(davido): Do wen need to interpret TotalHits.relation?
      TotalHits totalHits = results.totalHits;

      List<DocResult> out = new ArrayList<>();
      for (int i = 0; i < totalHits.value; i++) {
        DocResult result = new DocResult();
        Document doc = searcher.doc(hits[i].doc);
        result.url = doc.get(Constants.URL_FIELD);
        result.title = doc.get(Constants.TITLE_FIELD);
        out.add(result);
      }
      return out;
    } catch (IOException e) {
      throw new DocQueryException(e);
    }
  }

  protected Directory readIndexDirectory() throws IOException {
    Directory dir = new RAMDirectory();
    byte[] buffer = new byte[4096];
    InputStream index = getClass().getResourceAsStream(Constants.INDEX_ZIP);
    if (index == null) {
      logger.atWarning().log("No index available");
      return null;
    }

    try (ZipInputStream zip = new ZipInputStream(index)) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        try (IndexOutput out = dir.createOutput(entry.getName(), null)) {
          int count;
          while ((count = zip.read(buffer)) != -1) {
            out.writeBytes(buffer, count);
          }
        }
      }
    }
    // We must NOT call dir.close() here, as DirectoryReader.open() expects an opened directory.
    return dir;
  }

  public boolean isAvailable() {
    return parser != null && searcher != null;
  }

  public static class DocQueryException extends Exception {
    private static final long serialVersionUID = 1L;

    DocQueryException() {}

    DocQueryException(String msg) {
      super(msg);
    }

    DocQueryException(String msg, Throwable e) {
      super(msg, e);
    }

    DocQueryException(Throwable e) {
      super(e);
    }
  }
}
