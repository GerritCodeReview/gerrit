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

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Singleton
public class QueryDocumentationExecutor {
  private static final Logger log =
      LoggerFactory.getLogger(QueryDocumentationExecutor.class);

  private IndexSearcher searcher;
  private QueryParser parser;

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
      parser = new QueryParser(Constants.DOC_FIELD, new StandardAnalyzer());
    } catch (IOException e) {
      log.error("Cannot initialize documentation full text index", e);
      searcher = null;
      parser = null;
    }
  }

  public List<DocResult> doQuery(String q) throws DocQueryException {
    if (parser == null || searcher == null) {
      throw new DocQueryException("Documentation search not available");
    }
    try {
      Query query = parser.parse(q);
      // TODO(fishywang): Currently as we don't have much documentation, we just use MAX_VALUE here
      // and skipped paging. Maybe add paging later.
      TopDocs results = searcher.search(query, Integer.MAX_VALUE);
      ScoreDoc[] hits = results.scoreDocs;
      int totalHits = results.totalHits;

      List<DocResult> out = Lists.newArrayListWithCapacity(totalHits);
      for (int i = 0; i < totalHits; i++) {
        DocResult result = new DocResult();
        Document doc = searcher.doc(hits[i].doc);
        result.url = doc.get(Constants.URL_FIELD);
        result.title = doc.get(Constants.TITLE_FIELD);
        out.add(result);
      }
      return out;
    } catch (IOException e) {
      throw new DocQueryException(e);
    } catch (ParseException e) {
      throw new DocQueryException(e);
    }
  }

  protected Directory readIndexDirectory() throws IOException {
    Directory dir = new RAMDirectory();
    byte[] buffer = new byte[4096];
    InputStream index = getClass().getResourceAsStream(Constants.INDEX_ZIP);
    if (index == null) {
      log.warn("No index available");
      return null;
    }

    ZipInputStream zip = new ZipInputStream(index);
    try {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        IndexOutput out = dir.createOutput(entry.getName(), null);
        int count;
        while ((count = zip.read(buffer)) != -1) {
          out.writeBytes(buffer, count);
        }
        out.close();
      }
    } finally {
      zip.close();
    }
    // We must NOT call dir.close() here, as DirectoryReader.open() expects an opened directory.
    return dir;
  }

  @SuppressWarnings("serial")
  public static class DocQueryException extends Exception {
    DocQueryException() {
    }

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
