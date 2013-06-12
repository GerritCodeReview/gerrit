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

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.ListenableFuture;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.NRTManager;
import org.apache.lucene.search.NRTManager.TrackingIndexWriter;
import org.apache.lucene.search.NRTManagerReopenThread;
import org.apache.lucene.search.ReferenceManager.RefreshListener;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Piece of the change index that is implemented as a separate Lucene index. */
class SubIndex {
  private static final Logger log = LoggerFactory.getLogger(SubIndex.class);

  private final Directory dir;
  private final TrackingIndexWriter writer;
  private final NRTManager nrtManager;
  private final NRTManagerReopenThread reopenThread;
  private final ConcurrentMap<RefreshListener, Boolean> refreshListeners;

  SubIndex(File file, IndexWriterConfig writerConfig) throws IOException {
    dir = FSDirectory.open(file);
    writer = new NRTManager.TrackingIndexWriter(new IndexWriter(dir, writerConfig));
    nrtManager = new NRTManager(writer, new SearcherFactory());

    refreshListeners = Maps.newConcurrentMap();
    nrtManager.addListener(new RefreshListener() {
      @Override
      public void beforeRefresh() throws IOException {
      }

      @Override
      public void afterRefresh(boolean didRefresh) throws IOException {
        for (RefreshListener l : refreshListeners.keySet()) {
          l.afterRefresh(didRefresh);
        }
      }
    });

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

  ListenableFuture<Void> insert(Document doc) throws IOException {
    return new NrtFuture(writer.addDocument(doc));
  }

  ListenableFuture<Void> replace(Term term, Document doc) throws IOException {
    return new NrtFuture(writer.updateDocument(term, doc));
  }

  ListenableFuture<Void> delete(Term term) throws IOException {
    return new NrtFuture(writer.deleteDocuments(term));
  }

  void deleteAll() throws IOException {
    writer.deleteAll();
  }

  IndexSearcher acquire() throws IOException {
    return nrtManager.acquire();
  }

  void release(IndexSearcher searcher) throws IOException {
    nrtManager.release(searcher);
  }

  private final class NrtFuture extends AbstractFuture<Void>
      implements RefreshListener {
    private final long gen;
    private final AtomicBoolean hasListeners = new AtomicBoolean();

    NrtFuture(long gen) {
      this.gen = gen;
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
      if (!isDone()) {
        nrtManager.waitForGeneration(gen);
        set(null);
      }
      return super.get();
    }

    @Override
    public Void get(long timeout, TimeUnit unit) throws InterruptedException,
        TimeoutException, ExecutionException {
      if (!isDone()) {
        nrtManager.waitForGeneration(gen, timeout, unit);
        set(null);
      }
      return super.get(timeout, unit);
    }

    @Override
    public boolean isDone() {
      if (super.isDone()) {
        return true;
      } else if (gen <= nrtManager.getCurrentSearchingGen()) {
        set(null);
        return true;
      }
      return false;
    }

    @Override
    public void addListener(Runnable listener, Executor executor) {
      if (hasListeners.compareAndSet(false, true) && !isDone()) {
        nrtManager.addListener(this);
      }
      super.addListener(listener, executor);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      if (hasListeners.get()) {
        refreshListeners.put(this, true);
      }
      return super.cancel(mayInterruptIfRunning);
    }

    @Override
    public void beforeRefresh() throws IOException {
    }

    @Override
    public void afterRefresh(boolean didRefresh) throws IOException {
      if (gen <= nrtManager.getCurrentSearchingGen()) {
        refreshListeners.remove(this);
        set(null);
      }
    }
  }
}
