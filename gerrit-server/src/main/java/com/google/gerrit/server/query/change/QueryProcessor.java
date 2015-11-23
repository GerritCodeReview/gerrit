// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.query.change.ChangeStatusPredicate.open;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.index.ChangeIndex;
import com.google.gerrit.server.index.IndexCollection;
import com.google.gerrit.server.index.IndexConfig;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.IndexRewriter;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class QueryProcessor {
  private final Provider<ReviewDb> db;
  private final Provider<CurrentUser> userProvider;
  private final ChangeControl.GenericFactory changeControlFactory;
  private final IndexCollection indexes;
  private final IndexRewriter rewriter;
  private final IndexConfig indexConfig;
  private final Metrics metrics;

  private int limitFromCaller;
  private int start;
  private boolean enforceVisibility = true;
  private Set<String> requestedFields;

  @Inject
  QueryProcessor(Provider<ReviewDb> db,
      Provider<CurrentUser> userProvider,
      ChangeControl.GenericFactory changeControlFactory,
      IndexCollection indexes,
      IndexRewriter rewriter,
      IndexConfig indexConfig,
      Metrics metrics) {
    this.db = db;
    this.userProvider = userProvider;
    this.changeControlFactory = changeControlFactory;
    this.indexes = indexes;
    this.rewriter = rewriter;
    this.indexConfig = indexConfig;
    this.metrics = metrics;
  }

  public QueryProcessor enforceVisibility(boolean enforce) {
    enforceVisibility = enforce;
    return this;
  }

  public QueryProcessor setLimit(int n) {
    limitFromCaller = n;
    return this;
  }

  public QueryProcessor setStart(int n) {
    start = n;
    return this;
  }

  public QueryProcessor setRequestedFields(Set<String> fields) {
    requestedFields = fields;
    return this;
  }

  /**
   * Query for changes that match a structured query.
   *
   * @see #queryChanges(List)
   * @param query the query.
   * @return results of the query.
   */
  public QueryResult queryChanges(Predicate<ChangeData> query)
      throws OrmException, QueryParseException {
    return queryChanges(ImmutableList.of(query)).get(0);
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
  public List<QueryResult> queryChanges(List<Predicate<ChangeData>> queries)
      throws OrmException, QueryParseException {
    return queryChanges(null, queries);
  }

  static {
    // In addition to this assumption, this queryChanges assumes the basic
    // rewrites do not touch visibleto predicates either.
    checkState(
        !IsVisibleToPredicate.class.isAssignableFrom(IndexPredicate.class),
        "QueryProcessor assumes visibleto is not used by the index rewriter.");
  }

  private List<QueryResult> queryChanges(List<String> queryStrings,
      List<Predicate<ChangeData>> queries)
      throws OrmException, QueryParseException {
    @SuppressWarnings("resource")
    Timer0.Context context = metrics.executionTime.start();

    Predicate<ChangeData> visibleToMe = enforceVisibility
        ? new IsVisibleToPredicate(db, changeControlFactory, userProvider.get())
        : null;
    int cnt = queries.size();

    // Parse and rewrite all queries.
    List<Integer> limits = new ArrayList<>(cnt);
    List<Predicate<ChangeData>> predicates = new ArrayList<>(cnt);
    List<ChangeDataSource> sources = new ArrayList<>(cnt);
    for (Predicate<ChangeData> q : queries) {
      int limit = getEffectiveLimit(q);
      limits.add(limit);

      // Always bump limit by 1, even if this results in exceeding the permitted
      // max for this user. The only way to see if there are more changes is to
      // ask for one more result from the query.
      if (limit == getBackendSupportedLimit()) {
        limit--;
      }

      int page = (start / limit) + 1;
      if (page > indexConfig.maxPages()) {
        throw new QueryParseException(
            "Cannot go beyond page " + indexConfig.maxPages() + "of results");
      }

      QueryOptions opts = QueryOptions.create(
          indexConfig, start, limit + 1, getRequestedFields());
      Predicate<ChangeData> s = rewriter.rewrite(q, opts);
      if (!(s instanceof ChangeDataSource)) {
        q = Predicate.and(open(), q);
        s = rewriter.rewrite(q, opts);
      }
      if (!(s instanceof ChangeDataSource)) {
        throw new QueryParseException("invalid query: " + s);
      }
      if (enforceVisibility) {
        s = new AndSource(ImmutableList.of(s, visibleToMe), start);
      }
      predicates.add(s);
      sources.add((ChangeDataSource) s);
    }

    // Run each query asynchronously, if supported.
    List<ResultSet<ChangeData>> matches = new ArrayList<>(cnt);
    for (ChangeDataSource s : sources) {
      matches.add(s.read());
    }

    List<QueryResult> out = new ArrayList<>(cnt);
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

  private Set<String> getRequestedFields() {
    if (requestedFields != null) {
      return requestedFields;
    }
    ChangeIndex index = indexes.getSearchIndex();
    return index != null
        ? index.getSchema().getStoredFields().keySet()
        : ImmutableSet.<String> of();
  }

  boolean isDisabled() {
    return getPermittedLimit() <= 0;
  }

  private int getPermittedLimit() {
    if (enforceVisibility) {
      return userProvider.get().getCapabilities()
        .getRange(GlobalCapability.QUERY_LIMIT)
        .getMax();
    }
    return Integer.MAX_VALUE;
  }

  private int getBackendSupportedLimit() {
    return indexConfig.maxLimit();
  }

  private int getEffectiveLimit(Predicate<ChangeData> p) {
    List<Integer> possibleLimits = new ArrayList<>(4);
    possibleLimits.add(getBackendSupportedLimit());
    possibleLimits.add(getPermittedLimit());
    if (limitFromCaller > 0) {
      possibleLimits.add(limitFromCaller);
    }
    Integer limitFromPredicate = LimitPredicate.getLimit(p);
    if (limitFromPredicate != null) {
      possibleLimits.add(limitFromPredicate);
    }
    return Ordering.natural().min(possibleLimits);
  }

  @Singleton
  static class Metrics {
    final Timer0 executionTime;

    @Inject
    Metrics(MetricMaker metricMaker) {
      executionTime = metricMaker.newTimer(
          "change/query/query_latency",
          new Description("Successful change query latency,"
              + " accumulated over the life of the process")
            .setCumulative()
            .setUnit(Description.Units.MILLISECONDS));
    }
  }
}
