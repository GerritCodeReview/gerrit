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

package com.google.gerrit.server.change;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.server.AccountExternalIdAccess;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
/**
 * The suggest oracle may be called many times in rapid succession during the
 * course of one operation.
 * It would be easy to have a simple Cache<Boolean, List<Account>> with a short
 * expiration time of 30s.
 * Cache only has a single key we're just using Cache for the expiration behavior.
 */
@Singleton
public class ReviewerSuggestionCache {
  private static final Logger log = LoggerFactory
      .getLogger(ReviewerSuggestionCache.class);

  private final LoadingCache<Boolean, IndexSearcher> cache;
  private final Provider<ReviewDb> dbProvider;

  @Inject
  ReviewerSuggestionCache(Provider<ReviewDb> dbProvider,
      @GerritServerConfig Config cfg) {
    this.dbProvider = dbProvider;
    long expiration = ConfigUtil.getTimeUnit(cfg,
        "suggest", null, "fullTextSearchRefresh",
        TimeUnit.HOURS.toMillis(1),
        TimeUnit.MILLISECONDS);
    this.cache =
        CacheBuilder.newBuilder().maximumSize(1)
            .expireAfterWrite(expiration, TimeUnit.MILLISECONDS)
            .build(new CacheLoader<Boolean, IndexSearcher>() {
              @Override
              public IndexSearcher load(Boolean key) throws Exception {
                return index();
              }
            });
  }

  private IndexSearcher index() throws IOException, OrmException {
    RAMDirectory idx = new RAMDirectory();
    @SuppressWarnings("deprecation")
    IndexWriterConfig config = new IndexWriterConfig(
        Version.LUCENE_4_10_1,
        new StandardAnalyzer(CharArraySet.EMPTY_SET));
    config.setOpenMode(OpenMode.CREATE);
    IndexWriter writer = new IndexWriter(idx, config);

    ReviewDb db = dbProvider.get();

    for (Account a : db.accounts().all()) {
      if (a.isActive()) {
        addAccount(writer, a, db);
      }
    }

    writer.close();
    IndexReader reader = DirectoryReader.open(idx);
    IndexSearcher searcher  = new IndexSearcher(reader);
    return searcher;
  }

  private void addAccount(IndexWriter writer, Account a, ReviewDb db)
      throws IOException, OrmException {
    Document doc = new Document();
    doc.add(new IntField("id", a.getId().get(), Field.Store.YES));
    if (a.getFullName() != null) {
      doc.add(new TextField("name", a.getFullName(), Field.Store.YES));
    }
    if (a.getPreferredEmail() != null) {
      doc.add(new TextField("email", a.getPreferredEmail(), Field.Store.YES));
    }
    AccountExternalIdAccess extIdAccess = db.accountExternalIds();
    String username = AccountState.getUserName(
        extIdAccess.byAccount(a.getId()).toList());
    if (username != null) {
      doc.add(new StringField("username", username, Field.Store.YES));
    }
    writer.addDocument(doc);
  }

  IndexSearcher get() {
    try {
      return cache.get(true);
    } catch (ExecutionException e) {
      log.warn("Cannot fetch reviewers from cache", e);
      return null;
    }
  }
}
