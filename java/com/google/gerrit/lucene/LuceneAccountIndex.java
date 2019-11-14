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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.gerrit.server.index.account.AccountField.FULL_NAME;
import static com.google.gerrit.server.index.account.AccountField.ID;
import static com.google.gerrit.server.index.account.AccountField.ID_STR;
import static com.google.gerrit.server.index.account.AccountField.PREFERRED_EMAIL_EXACT;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.FieldDef;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.Schema;
import com.google.gerrit.index.Schema.Values;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;
import org.eclipse.jgit.lib.Config;

public class LuceneAccountIndex extends AbstractLuceneIndex<Account.Id, AccountState>
    implements AccountIndex {
  private static final String ACCOUNTS = "accounts";

  private static final String FULL_NAME_SORT_FIELD = sortFieldName(FULL_NAME);
  private static final String EMAIL_SORT_FIELD = sortFieldName(PREFERRED_EMAIL_EXACT);
  private static final String ID_SORT_FIELD = sortFieldName(ID);
  private static final String ID2_SORT_FIELD = sortFieldName(ID_STR);

  private static Term idTerm(boolean useLegacyNumericFields, AccountState as) {
    return idTerm(useLegacyNumericFields, as.account().id());
  }

  private static Term idTerm(boolean useLegacyNumericFields, Account.Id id) {
    FieldDef<AccountState, ?> idField = useLegacyNumericFields ? ID : ID_STR;
    if (useLegacyNumericFields) {
      return QueryBuilder.intTerm(idField.getName(), id.get());
    }
    return QueryBuilder.stringTerm(idField.getName(), Integer.toString(id.get()));
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
        ImmutableSet.of(),
        null,
        new GerritIndexWriterConfig(cfg, ACCOUNTS),
        new SearcherFactory());
    this.accountCache = accountCache;

    indexWriterConfig = new GerritIndexWriterConfig(cfg, ACCOUNTS);
    queryBuilder = new QueryBuilder<>(schema, indexWriterConfig.getAnalyzer());
  }

  @Override
  void add(Document doc, Values<AccountState> values) {
    // Add separate DocValues fields for those fields needed for sorting.
    FieldDef<AccountState, ?> f = values.getField();
    if (f == ID) {
      int v = (Integer) getOnlyElement(values.getValues());
      doc.add(new NumericDocValuesField(ID_SORT_FIELD, v));
    } else if (f == ID_STR) {
      String v = (String) getOnlyElement(values.getValues());
      doc.add(new NumericDocValuesField(ID2_SORT_FIELD, Integer.valueOf(v)));
    } else if (f == FULL_NAME) {
      String value = (String) getOnlyElement(values.getValues());
      doc.add(new SortedDocValuesField(FULL_NAME_SORT_FIELD, new BytesRef(value)));
    } else if (f == PREFERRED_EMAIL_EXACT) {
      String value = (String) getOnlyElement(values.getValues());
      doc.add(new SortedDocValuesField(EMAIL_SORT_FIELD, new BytesRef(value)));
    }
    super.add(doc, values);
  }

  @Override
  public void replace(AccountState as) {
    try {
      replace(idTerm(getSchema().useLegacyNumericFields(), as), toDocument(as)).get();
    } catch (ExecutionException | InterruptedException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public void delete(Account.Id key) {
    try {
      delete(idTerm(getSchema().useLegacyNumericFields(), key)).get();
    } catch (ExecutionException | InterruptedException e) {
      throw new StorageException(e);
    }
  }

  @Override
  public DataSource<AccountState> getSource(Predicate<AccountState> p, QueryOptions opts)
      throws QueryParseException {
    queryBuilder.getSchema().useLegacyNumericFields();
    return new LuceneQuerySource(
        opts.filterFields(o -> IndexUtils.accountFields(o, getSchema().useLegacyNumericFields())),
        queryBuilder.toQuery(p),
        getSort());
  }

  private Sort getSort() {
    String idSortField = getSchema().useLegacyNumericFields() ? ID_SORT_FIELD : ID2_SORT_FIELD;
    return new Sort(
        new SortField(FULL_NAME_SORT_FIELD, SortField.Type.STRING, false),
        new SortField(EMAIL_SORT_FIELD, SortField.Type.STRING, false),
        new SortField(idSortField, SortField.Type.LONG, false));
  }

  @Override
  protected AccountState fromDocument(Document doc) {
    FieldDef<AccountState, ?> idField = getSchema().useLegacyNumericFields() ? ID : ID_STR;
    Account.Id id =
        Account.id(
            getSchema().useLegacyNumericFields()
                ? doc.getField(idField.getName()).numericValue().intValue()
                : Integer.valueOf(doc.getField(idField.getName()).stringValue()));
    // Use the AccountCache rather than depending on any stored fields in the document (of which
    // there shouldn't be any). The most expensive part to compute anyway is the effective group
    // IDs, and we don't have a good way to reindex when those change.
    // If the account doesn't exist return an empty AccountState to represent the missing account
    // to account the fact that the account exists in the index.
    return accountCache.get().getEvenIfMissing(id);
  }
}
