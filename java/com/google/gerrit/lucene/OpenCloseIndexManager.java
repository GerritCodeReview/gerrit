// Copyright (C) 2025 The Android Open Source Project
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

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gerrit.lucene.AbstractLuceneIndex.FunctionThrows;
import com.google.gerrit.lucene.AbstractLuceneIndex.IndexManager;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryableAction;
import java.io.IOException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;

public class OpenCloseIndexManager implements IndexManager {
  private final Directory dir;
  private final GerritIndexWriterConfig cfg;
  private final SearcherFactory searcherFactory;
  private final RetryHelper retryHelper;

  OpenCloseIndexManager(
      Directory dir,
      GerritIndexWriterConfig cfg,
      SearcherFactory searcherFactory,
      RetryHelper retryHelper) {
    this.dir = dir;
    this.cfg = cfg;
    this.searcherFactory = searcherFactory;
    this.retryHelper = retryHelper;
  }

  @Override
  public ListenableFuture<?> submit(FunctionThrows<IndexWriter, Long, IOException> writeTask) {
    SettableFuture<Long> future = SettableFuture.create();
    try (IndexWriter writer = getWriter()) {
      future.set(writeTask.apply(writer));
    } catch (Exception e) {
      future.setException(e);
    }
    return future;
  }

  @Override
  public void deleteAll() throws IOException {
    try (IndexWriter writer = getWriter()) {
      writer.deleteAll();
    }
  }

  @Override
  public IndexWriter acquireSnapshotWriter() throws IOException {
    IndexWriterConfig luceneConfig = cfg.getLuceneConfig();
    luceneConfig.setIndexDeletionPolicy(
        new SnapshotDeletionPolicy(luceneConfig.getIndexDeletionPolicy()));
    return getWriter(luceneConfig);
  }

  @Override
  public void releaseSnaphotWriter(IndexWriter writer) throws IOException {
    writer.close();
  }

  protected IndexWriter getWriter() throws IOException {
    return getWriter(cfg.getLuceneConfig());
  }

  protected IndexWriter getWriter(IndexWriterConfig luceneConfig) throws IOException {
    try {
      return retryHelper
          .action(
              RetryableAction.ActionType.INDEX_UPDATE,
              "open IndexWriter",
              () ->
                  new IndexWriter(dir, luceneConfig) {
                    @Override
                    public void close() throws IOException {
                      flush();
                      commit();
                      super.close();
                    }
                  })
          .retryOn(e -> e instanceof LockObtainFailedException)
          .call();
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  @Override
  public IndexSearcher acquire() throws IOException {
    return searcherFactory.newSearcher(DirectoryReader.open(dir), null);
  }
}
