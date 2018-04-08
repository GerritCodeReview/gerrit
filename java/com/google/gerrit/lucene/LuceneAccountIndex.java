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

import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.eclipse.jgit.lib.Config;

public class LuceneAccountIndex extends AbstractLuceneIndex<Account.Id, AccountState>
    implements AccountIndex {
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
  private final Provider<AccountCache> accountCache;

  private static Directory dir(Schema<AccountState> schema, Config cfg, SitePaths sitePaths)
      throws IOException {
    if (LuceneIndexModule.isInMemoryTest(cfg)) {
      return new RAMDirectory();
    }
    Path indexDir = LuceneVersionManager.getDir(sitePaths, ACCOUNTS, schema);
    return FSDirectory.open(indexDir);
  }

  @Inject
  LuceneAccountIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      Provider<AccountCache> accountCache,
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
      replace(idTerm(as), toDocument(as)).get();
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
    return new LuceneQuerySource(
        opts.filterFields(IndexUtils::accountFields),
        queryBuilder.toQuery(p),
        new Sort(new SortField(ID_SORT_FIELD, SortField.Type.LONG, true)));
  }

  @Override
  protected AccountState fromDocument(Document doc) {
    Account.Id id = new Account.Id(doc.getField(ID.getName()).numericValue().intValue());
    // Use the AccountCache rather than depending on any stored fields in the document (of which
    // there shouldn't be any). The most expensive part to compute anyway is the effective group
    // IDs, and we don't have a good way to reindex when those change.
    // If the account doesn't exist return an empty AccountState to represent the missing account
    // to account the fact that the account exists in the index.
    return accountCache.get().getEvenIfMissing(id);
  }
}
