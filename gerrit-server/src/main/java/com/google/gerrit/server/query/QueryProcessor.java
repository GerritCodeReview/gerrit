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

package com.google.gerrit.server.query;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.index.Index;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexRewriter;
import com.google.gerrit.server.index.QueryOptions;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public abstract class QueryProcessor<T> {
  public static class Metrics {
    public interface Factory {
      Metrics create(String indexName);
    }

    private final Timer0 executionTime;

    @AssistedInject
    Metrics(MetricMaker metricMaker, @Assisted String indexName) {
      executionTime = metricMaker.newTimer(indexName + "/query/query_latency",
          new Description("Successful " + indexName + " query latency of,"
              + " accumulated over the life of the process").setCumulative()
                  .setUnit(Description.Units.MILLISECONDS));
    }

    public Timer0 getExecutionTime() {
      return executionTime;
    }
  }

  private final IndexCollection<?, T, ? extends Index<?, T>> indexes;
  private final Metrics.Factory metricsFactory;
  private final String indexName;
  private final IndexConfig indexConfig;
  private final IndexRewriter<T> rewriter;
  private final String limitField;

  protected int start;

  private int limitFromCaller;
  private Set<String> requestedFields;

  protected QueryProcessor(Metrics.Factory metricsFactory,
      String indexName,
      IndexConfig indexConfig,
      IndexCollection<?, T, ? extends Index<?, T>> indexes,
      IndexRewriter<T> rewriter,
      String limitField) {
    this.metricsFactory = metricsFactory;
    this.indexName = indexName;
    this.indexConfig = indexConfig;
    this.indexes = indexes;
    this.rewriter = rewriter;
    this.limitField = limitField;
  }

  public QueryProcessor<T> setStart(int n) {
    start = n;
    return this;
  }

  public QueryProcessor<T> setLimit(int n) {
    limitFromCaller = n;
    return this;
  }

  public QueryProcessor<T> setRequestedFields(Set<String> fields) {
    requestedFields = fields;
    return this;
  }

  /**
   * Query for entities that match a structured query.
   *
   * @see #query(List)
   * @param query the query.
   * @return results of the query.
   */
  public QueryResult<T> query(Predicate<T> query)
      throws OrmException, QueryParseException {
    return query(ImmutableList.of(query)).get(0);
  }

  /*
   * Perform multiple queries over a list of query strings.
   * <p>
   * If a limit was specified using {@link #setLimit(int)} this method may
   * return up to {@code limit + 1} results, allowing the caller to determine if
   * there are more than {@code limit} matches and suggest to its own caller
   * that the query could be retried with {@link #setStart(int)}.
   *
   * @param queries the queries.
   * @return results of the queries, one list per input query.
   */
  public List<QueryResult<T>> query(List<Predicate<T>> queries)
      throws OrmException, QueryParseException {
    try {
      return query(null, queries);
    } catch (OrmException e) {
      Throwables.propagateIfInstanceOf(e.getCause(), QueryParseException.class);
      throw e;
    }
  }

  private List<QueryResult<T>> query(List<String> queryStrings,
      List<Predicate<T>> queries)
      throws OrmException, QueryParseException {
    @SuppressWarnings("resource")
    Timer0.Context context =
        metricsFactory.create(indexName).executionTime.start();

    int cnt = queries.size();
    // Parse and rewrite all queries.
    List<Integer> limits = new ArrayList<>(cnt);
    List<Predicate<T>> predicates = new ArrayList<>(cnt);
    List<DataSource<T>> sources = new ArrayList<>(cnt);
    for (Predicate<T> q : queries) {
      int limit = getEffectiveLimit(q);
      limits.add(limit);

      if (limit == getBackendSupportedLimit()) {
        limit--;
      }

      int page = (start / limit) + 1;
      if (page > indexConfig.maxPages()) {
        throw new QueryParseException(
            "Cannot go beyond page " + indexConfig.maxPages() + "of results");
      }

      // Always bump limit by 1, even if this results in exceeding the permitted
      // max for this user. The only way to see if there are more entities is to
      // ask for one more result from the query.
      QueryOptions opts =
          createOptions(indexConfig, page, limit, getRequestedFields());
      Predicate<T> pred = postRewrite(rewriter.rewrite(q, opts));
      predicates.add(pred);

      @SuppressWarnings("unchecked")
      DataSource<T> s = (DataSource<T>) pred;
      sources.add(s);
    }

    // Run each query asynchronously, if supported.
    List<ResultSet<T>> matches = new ArrayList<>(cnt);
    for (DataSource<T> s : sources) {
      matches.add(s.read());
    }

    List<QueryResult<T>> out = new ArrayList<>(cnt);
    for (int i = 0; i < cnt; i++) {
      out.add(QueryResult.create(
          queryStrings != null ? queryStrings.get(i) : null,
          predicates.get(i),
          limits.get(i),
          matches.get(i).toList()));
    }
    context.close(); // only measure successful queries
    return out;
  }

  protected QueryOptions createOptions(IndexConfig indexConfig, int start,
      int limit, Set<String> requestedFields) {
    return QueryOptions.create(indexConfig, start, limit, requestedFields);
  }

  /**
   * Invoked after the query was rewritten. Subclasses may overwrite this method
   * to add additional predicates to the query before it is executed.
   *
   * @param pred the query
   * @return the modified query
   */
  protected Predicate<T> postRewrite(Predicate<T> pred) {
    return pred;
  }

  private Set<String> getRequestedFields() {
    if (requestedFields != null) {
      return requestedFields;
    }
    Index<?, T> index = indexes.getSearchIndex();
    return index != null
        ? index.getSchema().getStoredFields().keySet()
        : ImmutableSet.<String> of();
  }

  public boolean isDisabled() {
    return getPermittedLimit() <= 0;
  }

  protected int getPermittedLimit() {
    return Integer.MAX_VALUE;
  }

  private int getBackendSupportedLimit() {
    return indexConfig.maxLimit();
  }

  private int getEffectiveLimit(Predicate<T> p) {
    List<Integer> possibleLimits = new ArrayList<>(4);
    possibleLimits.add(getBackendSupportedLimit());
    possibleLimits.add(getPermittedLimit());
    if (limitFromCaller > 0) {
      possibleLimits.add(limitFromCaller);
    }
    if (limitField != null) {
      Integer limitFromPredicate = LimitPredicate.getLimit(limitField, p);
      if (limitFromPredicate != null) {
        possibleLimits.add(limitFromPredicate);
      }
    }
    return Ordering.natural().min(possibleLimits);
  }
}
