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

package com.google.gerrit.lucene;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NRTManager;
import org.apache.lucene.search.NRTManager.TrackingIndexWriter;
import org.apache.lucene.search.NRTManagerReopenThread;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Piece of the change index that is implemented as a separate Lucene index. */
class SubIndex {
  private static final Logger log = LoggerFactory.getLogger(SubIndex.class);

  private final Directory dir;
  private final TrackingIndexWriter writer;
  private final NRTManager nrtManager;
  private final NRTManagerReopenThread reopenThread;

  SubIndex(File file, IndexWriterConfig writerConfig) throws IOException {
    dir = FSDirectory.open(file);
    writer = new NRTManager.TrackingIndexWriter(new IndexWriter(dir, writerConfig));
    nrtManager = new NRTManager(writer, new SearcherFactory());

    reopenThread = new NRTManagerReopenThread(
        nrtManager,
        0.500 /* maximum stale age (seconds) */,
        0.010 /* minimum stale age (seconds) */);
    reopenThread.setName("NRT " + file.getName());
    reopenThread.setPriority(Math.min(
        Thread.currentThread().getPriority() + 2,
        Thread.MAX_PRIORITY));
    reopenThread.setDaemon(true);
    reopenThread.start();
  }

  void close() {
    reopenThread.close();
    try {
      nrtManager.close();
    } catch (IOException e) {
      log.warn("error closing Lucene searcher", e);
    }
    try {
      writer.getIndexWriter().close();
    } catch (IOException e) {
      log.warn("error closing Lucene writer", e);
    }
    try {
      dir.close();
    } catch (IOException e) {
      log.warn("error closing Lucene directory", e);
    }
  }

  Future<Void> insert(Document doc) throws IOException {
    return new NrtFuture(writer.addDocument(doc));
  }

  Future<Void> replace(Term term, Document doc) throws IOException {
    return new NrtFuture(writer.updateDocument(term, doc));
  }

  Future<Void> delete(Term term) throws IOException {
    return new NrtFuture(writer.deleteDocuments(term));
  }

  IndexSearcher acquire() throws IOException {
    return nrtManager.acquire();
  }

  void release(IndexSearcher searcher) throws IOException {
    nrtManager.release(searcher);
  }

  private final class NrtFuture implements Future<Void> {
    private final long gen;

    NrtFuture(long gen) {
      this.gen = gen;
    }

    @Override
    public Void get() {
      nrtManager.waitForGeneration(gen);
      return null;
    }

    @Override
    public Void get(long timeout, TimeUnit unit) {
      nrtManager.waitForGeneration(gen, timeout, unit);
      return null;
    }

    @Override
    public boolean isDone() {
      return gen <= nrtManager.getCurrentSearchingGen();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      return true;
    }

    @Override
    public boolean isCancelled() {
      return false;
    }
  }
}
