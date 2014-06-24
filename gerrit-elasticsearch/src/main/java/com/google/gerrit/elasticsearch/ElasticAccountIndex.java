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

package com.google.gerrit.elasticsearch;

import static com.google.gerrit.lucene.LuceneChangeIndex.CUSTOM_CHAR_MAPPING;
import static com.google.gerrit.server.index.account.AccountField.ID;

import com.google.common.collect.Lists;
import com.google.gerrit.lucene.CustomMappingAnalyzer;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Account.Id;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.index.FieldDef.FillArgs;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gerrit.server.index.Schema;
import com.google.gerrit.server.index.account.AccountIndex;
import com.google.gerrit.server.query.DataSource;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.eclipse.jgit.lib.Config;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import io.searchbox.client.JestResult;
import io.searchbox.core.Bulk.Builder;
import io.searchbox.core.Search;

class ElasticAccountIndex extends AbstractElasticIndex<Account.Id, AccountState>
    implements AccountIndex {
  private static final String ACCOUNT = "account";
  private static final Logger log =
      LoggerFactory.getLogger(ElasticAccountIndex.class);

  private final AccountCache accountCache;
  private final ElasticQueryBuilder queryBuilder;

  @AssistedInject
  ElasticAccountIndex(
      @GerritServerConfig Config cfg,
      FillArgs fillArgs,
      SitePaths sitePaths,
      AccountCache accountCache,
      @Assisted Schema<AccountState> schema) {
    super(cfg, fillArgs, sitePaths, schema);
    this.accountCache = accountCache;

    queryBuilder = new ElasticQueryBuilder(
        new CustomMappingAnalyzer(new StandardAnalyzer(
            CharArraySet.EMPTY_SET), CUSTOM_CHAR_MAPPING));
  }

  @Override
  public void replace(AccountState obj) throws IOException {
  }

  @Override
  public DataSource<AccountState> getSource(Predicate<AccountState> p,
      QueryOptions opts) throws QueryParseException {
    return new QuerySource(p);
  }

  @Override
  protected Builder addActions(Builder builder, Id c) {
    return builder.addAction(delete(ACCOUNT, c));
  }

  @Override
  protected String getMappings() {
    return null;
  }

  @Override
  protected String getId(AccountState as) {
    return as.getAccount().getId().toString();
  }

  private class QuerySource implements DataSource<AccountState> {
    private final Search search;

    QuerySource(Predicate<AccountState> p) throws QueryParseException {
      QueryBuilder qb = queryBuilder.toQueryBuilder(p);
      search = new Search.Builder(
          new SearchSourceBuilder()
            .query(qb)
            .toString())
            .addType(ACCOUNT)
            .addIndex(indexName)
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
              results.add(toChangeData(json.get(i)));
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

    private AccountState toChangeData(JsonElement json) {
      JsonObject source = json.getAsJsonObject().get("_source").getAsJsonObject();
      return toAccountState(source);
    }

    private AccountState toAccountState(JsonObject element) {
      Account.Id id = new Account.Id(element.get(ID.getName()).getAsInt());
      // Use the AccountCache rather than depending on any stored fields in the
      // document (of which there shouldn't be any. The most expensive part to
      // compute anyway is the effective group IDs, and we don't have a good way
      // to reindex when those change.
      return accountCache.get(id);
    }
  }
}
