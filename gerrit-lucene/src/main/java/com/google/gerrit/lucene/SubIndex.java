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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.lucene;

import static com.google.gerrit.lucene.LuceneChangeIndex.LUCENE_VERSION;
import static com.google.gerrit.server.query.change.ChangeQueryBuilder.FIELD_CHANGE;

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.query.change.ChangeData;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** Piece of the change index that is implemented as a separate Lucene index. */
class SubIndex {
  private static final Logger log = LoggerFactory.getLogger(SubIndex.class);

  private final Directory dir;
  private final IndexWriter writer;
  private final SearcherManager searcherManager;

  SubIndex(File file) throws IOException {
    dir = FSDirectory.open(file);
    IndexWriterConfig writerConfig =
        new IndexWriterConfig(LUCENE_VERSION, new StandardAnalyzer(LUCENE_VERSION));
    writerConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
    writer = new IndexWriter(dir, writerConfig);
    searcherManager = new SearcherManager(writer, true, null);
  }

  void close() {
    try {
      searcherManager.close();
    } catch (IOException e) {
      log.warn("error closing Lucene searcher", e);
    }
    try {
      writer.close();
    } catch (IOException e) {
      log.warn("error closing Lucene writer", e);
    }
    try {
      dir.close();
    } catch (IOException e) {
      log.warn("error closing Lucene directory", e);
    }
  }

  void insert(Document doc) throws IOException {
    writer.addDocument(doc);
  }

  void replace(Term term, Document doc) throws IOException {
    writer.updateDocument(term, doc);
  }

  void delete(Term term) throws IOException {
    writer.deleteDocuments(term);
  }

  List<ChangeData> search(Query query, int limit) throws IOException {
    IndexSearcher searcher = searcherManager.acquire();
    try {
      ScoreDoc[] docs = searcher.search(query, limit).scoreDocs;
      List<ChangeData> result = Lists.newArrayListWithCapacity(docs.length);
      for (ScoreDoc sd : docs) {
        Document doc = searcher.doc(sd.doc);
        Number v = doc.getField(FIELD_CHANGE).numericValue();
        result.add(new ChangeData(new Change.Id(v.intValue())));
      }
      return Collections.unmodifiableList(result);
    } finally {
      searcherManager.release(searcher);
    }
  }

  void maybeRefresh() {
    try {
      searcherManager.maybeRefresh();
    } catch (IOException e) {
      log.warn("error refreshing indexer", e);
    }
  }
}
