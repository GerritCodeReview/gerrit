// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.index;

import static org.apache.lucene.util.Version.LUCENE_32;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gson.Gson;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.SimpleFSDirectory;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
public class LuceneIndex {
  private static final Logger log = LoggerFactory.getLogger(LuceneIndex.class);

  private static final int MB = 1024 * 1024;

  private final File indexDir;
  private final Directory store;
  private final IndexWriterConfig indexWriterConfig;
  private final Gson gson;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Inject
  LuceneIndex(final SitePaths site, @GerritServerConfig final Config cfg)
      throws IOException {
    String dir = cfg.getString("index", null, "directory");
    if (dir == null) {
      dir = "index";
    }
    if (dir.isEmpty()) {
      throw new IllegalStateException("index.directory must be configured");
    }
    indexDir = site.resolve(dir);

    log.info("Opening index " + indexDir);
    SimpleFSDirectory fs = new SimpleFSDirectory(indexDir);

    long maxMerge = cfg.getLong("index", null, "mergeLimit", 5 * MB) / MB;
    long maxCached = cfg.getLong("index", null, "cacheLimit", 60 * MB) / MB;
    //store = new NRTCachingDirectory(fs, maxMerge, maxCached);
    store = fs;

    PerFieldAnalyzerWrapper analyzer =
        new PerFieldAnalyzerWrapper(new StandardAnalyzer(LUCENE_32));
    indexWriterConfig = new IndexWriterConfig(LUCENE_32, analyzer);
    //indexWriterConfig.setMergeScheduler(store.getMergeScheduler());

    gson = JsonServlet.defaultGsonBuilder().create();
  }

  public List<ChangeData> scan(Predicate<ChangeData> query, int limit, String after) {
    Query luceneQuery = ((LucenePredicate) query).getQuery();
    SortField[] sort = {
        new SortField("last-update", SortField.INT),
        SortField.FIELD_SCORE,
    };

    IndexSearcher s;
    try {
      s = searcher();
    } catch (IndexingException e) {
      log.warn("Cannot scan Lucene index for " + query, e);
      return Collections.emptyList();
    }
    try {
      int base = after == null || after.isEmpty() || "z".equals(after) ? 0 : 1 + Integer.parseInt(after);
      int max = base + limit;

      ChangeFilter topFilter = null;
      if (luceneQuery instanceof FilteredQuery
          && ((FilteredQuery) luceneQuery).getFilter() instanceof ChangeFilter) {
        topFilter = (ChangeFilter) ((FilteredQuery) luceneQuery).getFilter();
        luceneQuery = ((FilteredQuery) luceneQuery).getQuery();
      }

      List<ChangeData> out = new ArrayList<ChangeData>(Math.min(128, limit));
      while (out.size() < limit) {
        TopFieldDocs top = s.search(luceneQuery, max, new Sort(sort));
        if (top.scoreDocs.length == 0) {
          break;
        }
        for (; out.size() < limit && base < top.scoreDocs.length; base++) {
          ScoreDoc doc = top.scoreDocs[base];
          Document d = s.doc(doc.doc);
          Change c = gson.fromJson(d.get("json"), Change.class);
          if (topFilter != null && !topFilter.match(c)) {
            continue;
          }
          c.setSortKey(Integer.toString(base));
          out.add(new ChangeData(c));
        }
        if (topFilter != null && out.size() < limit) {
          max += nextChunkSize(limit);
        }
      }
      return out;
    } catch (IOException e) {
      log.warn("Cannot scan Lucene index for " + query, e);
      return Collections.emptyList();
    } finally {
      try {
        s.getIndexReader().decRef();
      } catch (IOException e) {
        // Should never happen as deletes do not occur on this reader.
        log.warn("Error closing Lucene IndexReader", e);
      }
    }
  }

  private int nextChunkSize(int limit) {
    return Math.min(512, limit);
  }

  private synchronized IndexSearcher searcher() throws IndexingException {
    IndexReader r = reader;
    if (r == null) {
      try {
        r = IndexReader.open(store, true /* read only */);
        reader = r;
      } catch (CorruptIndexException e) {
        throw new IndexingException(e);
      } catch (IOException e) {
        throw new IndexingException(e);
      }
    }

    IndexSearcher s = searcher;
    if (s == null) {
      s = new IndexSearcher(reader);
      searcher = s;
    }
    s.getIndexReader().incRef();
    return s;
  }

  public UpdateTransaction update() throws IndexingException {
    try {
      return new UpdateTransaction(new IndexWriter(store, indexWriterConfig));
    } catch (CorruptIndexException e) {
      throw new IndexingException(e);
    } catch (LockObtainFailedException e) {
      throw new IndexingException(e);
    } catch (IOException e) {
      throw new IndexingException(e);
    }
  }
}
