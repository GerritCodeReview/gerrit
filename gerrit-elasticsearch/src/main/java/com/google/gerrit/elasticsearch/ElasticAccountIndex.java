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

package com.google.gerrit.elasticsearch;

import static com.google.gerrit.server.index.account.AccountField.ID;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.elasticsearch.ElasticMapping.MappingProperties;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.IndexUtils;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.account.AccountField;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.query.DataSource;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import io.searchbox.client.JestResult;
import io.searchbox.core.Bulk;
import io.searchbox.core.Bulk.Builder;
import io.searchbox.core.Search;
import io.searchbox.core.search.sort.Sort;
import io.searchbox.core.search.sort.Sort.Sorting;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticAccountIndex extends AbstractElasticIndex<Account.Id, AccountState>
    implements AccountIndex {
  public static class AccountMapping {
    MappingProperties accounts;

    public AccountMapping(Schema<AccountState> schema) {
      this.accounts = ElasticMapping.createMapping(schema);
    }
  }

  public static final String ACCOUNTS = "accounts";

  private static final Logger log = LoggerFactory.getLogger(ElasticAccountIndex.class);

  private final AccountMapping mapping;
  private final Provider<AccountCache> accountCache;

  @AssistedInject
  ElasticAccountIndex(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      Provider<AccountCache> accountCache,
      JestClientBuilder clientBuilder,
      @Assisted Schema<AccountState> schema) {
    // No parts of FillArgs are currently required, just use null.
    super(cfg, null, sitePaths, schema, clientBuilder, ACCOUNTS);
    this.accountCache = accountCache;
    this.mapping = new AccountMapping(schema);
  }

  @Override
  public void replace(AccountState as) throws IOException {
    Bulk bulk =
        new Bulk.Builder()
            .defaultIndex(indexName)
            .defaultType(ACCOUNTS)
            .addAction(insert(ACCOUNTS, as))
            .refresh(true)
            .build();
    JestResult result = client.execute(bulk);
    if (!result.isSucceeded()) {
      throw new IOException(
          String.format(
              "Failed to replace account %s in index %s: %s",
              as.getAccount().getId(), indexName, result.getErrorMessage()));
    }
  }

  @Override
  public DataSource<AccountState> getSource(Predicate<AccountState> p, QueryOptions opts)
      throws QueryParseException {
    return new QuerySource(p, opts);
  }

  @Override
  protected Builder addActions(Builder builder, Account.Id c) {
    return builder.addAction(delete(ACCOUNTS, c));
  }

  @Override
  protected String getMappings() {
    ImmutableMap<String, AccountMapping> mappings = ImmutableMap.of("mappings", mapping);
    return gson.toJson(mappings);
  }

  @Override
  protected String getId(AccountState as) {
    return as.getAccount().getId().toString();
  }

  private class QuerySource implements DataSource<AccountState> {
    private final Search search;
    private final Set<String> fields;

    QuerySource(Predicate<AccountState> p, QueryOptions opts) throws QueryParseException {
      QueryBuilder qb = queryBuilder.toQueryBuilder(p);
      fields = IndexUtils.accountFields(opts);
      SearchSourceBuilder searchSource =
          new SearchSourceBuilder()
              .query(qb)
              .from(opts.start())
              .size(opts.limit())
              .fields(Lists.newArrayList(fields));

      Sort sort = new Sort(AccountField.ID.getName(), Sorting.ASC);
      sort.setIgnoreUnmapped();

      search =
          new Search.Builder(searchSource.toString())
              .addType(ACCOUNTS)
              .addIndex(indexName)
              .addSort(ImmutableList.of(sort))
              .build();
    }

    @Override
    public int getCardinality() {
      return 10;
    }

    @Override
    public ResultSet<AccountState> read() throws OrmException {
      try {
        List<AccountState> results = Collections.emptyList();
        JestResult result = client.execute(search);
        if (result.isSucceeded()) {
          JsonObject obj = result.getJsonObject().getAsJsonObject("hits");
          if (obj.get("hits") != null) {
            JsonArray json = obj.getAsJsonArray("hits");
            results = Lists.newArrayListWithCapacity(json.size());
            for (int i = 0; i < json.size(); i++) {
              results.add(toAccountState(json.get(i)));
            }
          }
        } else {
          log.error(result.getErrorMessage());
        }
        final List<AccountState> r = Collections.unmodifiableList(results);
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
      }
    }

    @Override
    public String toString() {
      return search.toString();
    }

    private AccountState toAccountState(JsonElement json) {
      JsonElement source = json.getAsJsonObject().get("_source");
      if (source == null) {
        source = json.getAsJsonObject().get("fields");
      }

      Account.Id id = new Account.Id(source.getAsJsonObject().get(ID.getName()).getAsInt());
      // Use the AccountCache rather than depending on any stored fields in the
      // document (of which there shouldn't be any). The most expensive part to
      // compute anyway is the effective group IDs, and we don't have a good way
      // to reindex when those change.
      return accountCache.get().get(id);
    }
  }
}
