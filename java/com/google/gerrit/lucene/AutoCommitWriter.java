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

import java.io.IOException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;

/** Writer that optionally flushes/commits after every write. */
public class AutoCommitWriter extends IndexWriter {
  private boolean autoCommit;

  AutoCommitWriter(Directory dir, IndexWriterConfig config) throws IOException {
    this(dir, config, false);
  }

  AutoCommitWriter(Directory dir, IndexWriterConfig config, boolean autoCommit) throws IOException {
    super(dir, config);
    setAutoCommit(autoCommit);
  }

  /**
   * This method will override Gerrit configuration index.name.commitWithin until next Gerrit
   * restart (or reconfiguration through this method).
   *
   * @param enable auto commit
   */
  public void setAutoCommit(boolean enable) {
    this.autoCommit = enable;
  }

  @Override
  public long addDocument(Iterable<? extends IndexableField> doc) throws IOException {
    long ret = super.addDocument(doc);
    autoFlush();
    return ret;
  }

  @Override
  public long addDocuments(Iterable<? extends Iterable<? extends IndexableField>> docs)
      throws IOException {
    long ret = super.addDocuments(docs);
    autoFlush();
    return ret;
  }

  @Override
  public long updateDocuments(
      Term delTerm, Iterable<? extends Iterable<? extends IndexableField>> docs)
      throws IOException {
    long ret = super.updateDocuments(delTerm, docs);
    autoFlush();
    return ret;
  }

  @Override
  public long deleteDocuments(Term... term) throws IOException {
    long ret = super.deleteDocuments(term);
    autoFlush();
    return ret;
  }

  @Override
  public synchronized long tryDeleteDocument(IndexReader readerIn, int docID) throws IOException {
    long ret = super.tryDeleteDocument(readerIn, docID);
    if (ret != -1) {
      autoFlush();
    }
    return ret;
  }

  @Override
  public long deleteDocuments(Query... queries) throws IOException {
    long ret = super.deleteDocuments(queries);
    autoFlush();
    return ret;
  }

  @Override
  public long updateDocument(Term term, Iterable<? extends IndexableField> doc) throws IOException {
    long ret = super.updateDocument(term, doc);
    autoFlush();
    return ret;
  }

  @Override
  public long deleteAll() throws IOException {
    long ret = super.deleteAll();
    autoFlush();
    return ret;
  }

  void manualFlush() throws IOException {
    flush();
    if (autoCommit) {
      commit();
    }
  }

  public void autoFlush() throws IOException {
    if (autoCommit) {
      manualFlush();
    }
  }
}
