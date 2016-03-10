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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.extensions.common.AccountInfo;
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
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.RAMDirectory;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
/**
 * The suggest oracle may be called many times in rapid succession during the
 * course of one operation.
 * It would be easy to have a simple {@code Cache<Boolean, List<Account>>}
 * with a short expiration time of 30s.
 * Cache only has a single key we're just using Cache for the expiration behavior.
 */
@Singleton
public class ReviewerSuggestionCache {
  private static final Logger log = LoggerFactory
      .getLogger(ReviewerSuggestionCache.class);

  private static final String ID = "id";
  private static final String NAME = "name";
  private static final String EMAIL = "email";
  private static final String USERNAME = "username";
  private static final String[] ALL = {ID, NAME, EMAIL, USERNAME};

  private final LoadingCache<Boolean, IndexSearcher> cache;
  private final Provider<ReviewDb> db;

  @Inject
  ReviewerSuggestionCache(Provider<ReviewDb> db,
      @GerritServerConfig Config cfg) {
    this.db = db;
    long expiration = ConfigUtil.getTimeUnit(cfg,
        "suggest", null, "fullTextSearchRefresh",
        TimeUnit.HOURS.toMillis(1),
        TimeUnit.MILLISECONDS);
    this.cache =
        CacheBuilder.newBuilder().maximumSize(1)
            .refreshAfterWrite(expiration, TimeUnit.MILLISECONDS)
            .build(new CacheLoader<Boolean, IndexSearcher>() {
              @Override
              public IndexSearcher load(Boolean key) throws Exception {
                return index();
              }
            });
  }

  public List<AccountInfo> search(String query, int n) throws IOException {
    IndexSearcher searcher = get();
    if (searcher == null) {
      return Collections.emptyList();
    }

    List<String> segments = Splitter.on(' ').omitEmptyStrings().splitToList(
        query.toLowerCase());
    BooleanQuery.Builder q = new BooleanQuery.Builder();
    for (String field : ALL) {
      BooleanQuery.Builder and = new BooleanQuery.Builder();
      for (String s : segments) {
        and.add(new PrefixQuery(new Term(field, s)), Occur.MUST);
      }
      q.add(and.build(), Occur.SHOULD);
    }

    TopDocs results = searcher.search(q.build(), n);
    ScoreDoc[] hits = results.scoreDocs;

    List<AccountInfo> result = new LinkedList<>();

    for (ScoreDoc h : hits) {
      Document doc = searcher.doc(h.doc);

      IndexableField idField = checkNotNull(doc.getField(ID));
      AccountInfo info = new AccountInfo(idField.numericValue().intValue());
      info.name = doc.get(NAME);
      info.email = doc.get(EMAIL);
      info.username = doc.get(USERNAME);
      result.add(info);
    }

    return result;
  }

  private IndexSearcher get() {
    try {
      return cache.get(true);
    } catch (ExecutionException e) {
      log.warn("Cannot fetch reviewers from cache", e);
      return null;
    }
  }

  private IndexSearcher index() throws IOException, OrmException {
    RAMDirectory idx = new RAMDirectory();
    IndexWriterConfig config = new IndexWriterConfig(
        new StandardAnalyzer(CharArraySet.EMPTY_SET));
    config.setOpenMode(OpenMode.CREATE);

    try (IndexWriter writer = new IndexWriter(idx, config)) {
      for (Account a : db.get().accounts().all()) {
        if (a.isActive()) {
          addAccount(writer, a);
        }
      }
    }

    return new IndexSearcher(DirectoryReader.open(idx));
  }

  private void addAccount(IndexWriter writer, Account a)
      throws IOException, OrmException {
    Document doc = new Document();
    doc.add(new IntField(ID, a.getId().get(), Store.YES));
    if (a.getFullName() != null) {
      doc.add(new TextField(NAME, a.getFullName(), Store.YES));
    }
    if (a.getPreferredEmail() != null) {
      doc.add(new TextField(EMAIL, a.getPreferredEmail(), Store.YES));
      doc.add(new StringField(EMAIL, a.getPreferredEmail().toLowerCase(),
          Store.YES));
    }
    AccountExternalIdAccess extIdAccess = db.get().accountExternalIds();
    String username = AccountState.getUserName(
        extIdAccess.byAccount(a.getId()).toList());
    if (username != null) {
      doc.add(new StringField(USERNAME, username, Store.YES));
    }
    writer.addDocument(doc);
  }
}
