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

import com.google.common.collect.Lists;
import com.google.gerrit.elasticsearch.ElasticMapping.MappingProperties;
import com.google.gerrit.elasticsearch.builders.QueryBuilder;
import com.google.gerrit.elasticsearch.builders.SearchSourceBuilder;
import com.google.gerrit.elasticsearch.bulk.BulkRequest;
import com.google.gerrit.elasticsearch.bulk.IndexRequest;
import com.google.gerrit.elasticsearch.bulk.UpdateRequest;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
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
import com.google.gson.JsonParser;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.elasticsearch.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticAccountIndex extends AbstractElasticIndex<Account.Id, AccountState>
    implements AccountIndex {
  private static final Logger log = LoggerFactory.getLogger(ElasticAccountIndex.class);

  static class AccountMapping {
    final MappingProperties accounts;

    AccountMapping(Schema<AccountState> schema, ElasticQueryAdapter adapter) {
      this.accounts = ElasticMapping.createMapping(schema, adapter);
    }
  }

  private static final String ACCOUNTS = "accounts";

  private final AccountMapping mapping;
  private final Provider<AccountCache> accountCache;
  private final Schema<AccountState> schema;

  @AssistedInject
  ElasticAccountIndex(
      ElasticConfiguration cfg,
      SitePaths sitePaths,
      Provider<AccountCache> accountCache,
      ElasticRestClientProvider client,
      @Assisted Schema<AccountState> schema) {
    super(cfg, sitePaths, schema, client, ACCOUNTS);
    this.accountCache = accountCache;
    this.mapping = new AccountMapping(schema, client.adapter());
    this.schema = schema;
  }

  @Override
  public void replace(AccountState as) throws IOException {
    BulkRequest bulk =
        new IndexRequest(getId(as), indexName, type, client.adapter())
            .add(new UpdateRequest<>(schema, as));

    String uri = getURI(type, BULK);
    Response response = postRequest(uri, bulk, getRefreshParam());
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode != HttpStatus.SC_OK) {
      throw new IOException(
          String.format(
              "Failed to replace account %s in index %s: %s",
              as.getAccount().getId(), indexName, statusCode));
    }
  }

  @Override
  public DataSource<AccountState> getSource(Predicate<AccountState> p, QueryOptions opts)
      throws QueryParseException {
    return new QuerySource(p, opts);
  }

  @Override
  protected String getDeleteActions(Account.Id a) {
    return delete(type, a);
  }

  @Override
  protected String getMappings() {
    return getMappingsForSingleType(ACCOUNTS, mapping.accounts);
  }

  @Override
  protected String getId(AccountState as) {
    return as.getAccount().getId().toString();
  }

  private class QuerySource implements DataSource<AccountState> {
    private final String search;
    private final Set<String> fields;

    QuerySource(Predicate<AccountState> p, QueryOptions opts) throws QueryParseException {
      QueryBuilder qb = queryBuilder.toQueryBuilder(p);
      fields = IndexUtils.accountFields(opts);
      SearchSourceBuilder searchSource =
          new SearchSourceBuilder(client.adapter())
              .query(qb)
              .from(opts.start())
              .size(opts.limit())
              .fields(Lists.newArrayList(fields));

      JsonArray sortArray = getSortArray(AccountField.ID.getName());
      search = getSearch(searchSource, sortArray);
    }

    @Override
    public int getCardinality() {
      return 10;
    }

    @Override
    public ResultSet<AccountState> read() throws OrmException {
      try {
        List<AccountState> results = Collections.emptyList();
        String uri = getURI(type, SEARCH);
        Response response = postRequest(uri, search);
        StatusLine statusLine = response.getStatusLine();
        if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
          String content = getContent(response);
          JsonObject obj =
              new JsonParser().parse(content).getAsJsonObject().getAsJsonObject("hits");
          if (obj.get("hits") != null) {
            JsonArray json = obj.getAsJsonArray("hits");
            results = Lists.newArrayListWithCapacity(json.size());
            for (int i = 0; i < json.size(); i++) {
              results.add(toAccountState(json.get(i)));
            }
          }
        } else {
          log.error(statusLine.getReasonPhrase());
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
