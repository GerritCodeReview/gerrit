// Copyright (C) 2016 The Android Open Source Project
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

import static com.google.gerrit.server.index.account.AccountField.ID;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.query.DataSource;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuceneAccountIndex extends AbstractLuceneIndex<Account.Id, AccountState>
    implements AccountIndex {
  private static final Logger log = LoggerFactory.getLogger(LuceneAccountIndex.class);

  private static final String ACCOUNTS = "accounts";

  private static final String ID_SORT_FIELD = sortFieldName(ID);

  private static Term idTerm(AccountState as) {
    return idTerm(as.getAccount().getId());
  }

  private static Term idTerm(Account.Id id) {
    return QueryBuilder.intTerm(ID.getName(), id.get());
  }

  private final GerritIndexWriterConfig indexWriterConfig;
  private final QueryBuilder<AccountState> queryBuilder;
  private final AccountCache accountCache;

  private static Directory dir(Schema<AccountState> schema, Config cfg, SitePaths sitePaths)
      throws IOException {
    if (LuceneIndexModule.isInMemoryTest(cfg)) {
      return new RAMDirectory();
    }
    Path indexDir = LuceneVersionManager.getDir(sitePaths, ACCOUNTS + "_", schema);
    return FSDirectory.open(indexDir);
  }

  @Inject
  LuceneAccountIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      AccountCache accountCache,
      @Assisted Schema<AccountState> schema)
      throws IOException {
    super(
        schema,
        sitePaths,
        dir(schema, cfg, sitePaths),
        ACCOUNTS,
        null,
        new GerritIndexWriterConfig(cfg, ACCOUNTS),
        new SearcherFactory());
    this.accountCache = accountCache;

    indexWriterConfig = new GerritIndexWriterConfig(cfg, ACCOUNTS);
    queryBuilder = new QueryBuilder<>(schema, indexWriterConfig.getAnalyzer());
  }

  @Override
  public void replace(AccountState as) throws IOException {
    try {
      // No parts of FillArgs are currently required, just use null.
      replace(idTerm(as), toDocument(as, null)).get();
    } catch (ExecutionException | InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void delete(Account.Id key) throws IOException {
    try {
      delete(idTerm(key)).get();
    } catch (ExecutionException | InterruptedException e) {
      throw new IOException(e);
    }
  }

  @Override
  public DataSource<AccountState> getSource(Predicate<AccountState> p, QueryOptions opts)
      throws QueryParseException {
    return new QuerySource(
        opts,
        queryBuilder.toQuery(p),
        new Sort(new SortField(ID_SORT_FIELD, SortField.Type.LONG, true)));
  }

  private class QuerySource implements DataSource<AccountState> {
    private final QueryOptions opts;
    private final Query query;
    private final Sort sort;

    private QuerySource(QueryOptions opts, Query query, Sort sort) {
      this.opts = opts;
      this.query = query;
      this.sort = sort;
    }

    @Override
    public int getCardinality() {
      // TODO(dborowitz): In contrast to the comment in
      // LuceneChangeIndex.QuerySource#getCardinality, at this point I actually
      // think we might just want to remove getCardinality.
      return 10;
    }

    @Override
    public ResultSet<AccountState> read() throws OrmException {
      IndexSearcher searcher = null;
      try {
        searcher = acquire();
        int realLimit = opts.start() + opts.limit();
        TopFieldDocs docs = searcher.search(query, realLimit, sort);
        List<AccountState> result = new ArrayList<>(docs.scoreDocs.length);
        for (int i = opts.start(); i < docs.scoreDocs.length; i++) {
          ScoreDoc sd = docs.scoreDocs[i];
          Document doc = searcher.doc(sd.doc, fields(opts));
          result.add(toAccountState(doc));
        }
        final List<AccountState> r = Collections.unmodifiableList(result);
        return new ResultSet<AccountState>() {
          @Override
          public Iterator<AccountState> iterator() {
            return r.iterator();
          }

          @Override
          public List<AccountState> toList() {
            return r;
          }

          @Override
          public void close() {
            // Do nothing.
          }
        };
      } catch (IOException e) {
        throw new OrmException(e);
      } finally {
        if (searcher != null) {
          try {
            release(searcher);
          } catch (IOException e) {
            log.warn("cannot release Lucene searcher", e);
          }
        }
      }
    }
  }

  private Set<String> fields(QueryOptions opts) {
    Set<String> fs = opts.fields();
    return fs.contains(ID.getName()) ? fs : Sets.union(fs, ImmutableSet.of(ID.getName()));
  }

  private AccountState toAccountState(Document doc) {
    Account.Id id = new Account.Id(doc.getField(ID.getName()).numericValue().intValue());
    // Use the AccountCache rather than depending on any stored fields in the
    // document (of which there shouldn't be any. The most expensive part to
    // compute anyway is the effective group IDs, and we don't have a good way
    // to reindex when those change.
    return accountCache.get(id);
  }
}
