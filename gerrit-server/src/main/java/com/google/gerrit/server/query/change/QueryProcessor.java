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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

public class QueryProcessor {
  private final ChangeQueryBuilder queryBuilder;
  private final ChangeQueryRewriter queryRewriter;
  private final int permittedLimit;

  private int limitFromCaller;
  private int start;

  @Inject
  QueryProcessor(ChangeQueryBuilder.Factory queryBuilder,
      CurrentUser currentUser,
      ChangeQueryRewriter queryRewriter) {
    this.queryBuilder = queryBuilder.create(currentUser);
    this.queryRewriter = queryRewriter;
    this.permittedLimit = currentUser.getCapabilities()
      .getRange(GlobalCapability.QUERY_LIMIT)
      .getMax();
  }

  void setLimit(int n) {
    limitFromCaller = n;
  }

  public void setStart(int n) {
    start = n;
  }

  /**
   * Query for changes that match the query string.
   *
   * @see #queryChanges(List)
   * @param queryString the query string to parse.
   * @return results of the query.
   */
  public QueryResult queryByString(String queryString)
      throws OrmException, QueryParseException {
    return queryByStrings(ImmutableList.of(queryString)).get(0);
  }

  /**
   * Perform multiple queries over a list of query strings.
   *
   * @see #queryChanges(List)
   * @param queryStrings the query strings to parse.
   * @return results of the queries, one list per input query.
   */
  public List<QueryResult> queryByStrings(List<String> queryStrings)
      throws OrmException, QueryParseException {
    List<Predicate<ChangeData>> queries = new ArrayList<>(queryStrings.size());
    for (String qs : queryStrings) {
      queries.add(queryBuilder.parse(qs));
    }
    return queryChanges(queries);
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

  private List<QueryResult> queryChanges(List<String> queryStrings,
      List<Predicate<ChangeData>> queries)
      throws OrmException, QueryParseException {
    Predicate<ChangeData> visibleToMe = queryBuilder.is_visible();
    int cnt = queries.size();

    // Parse and rewrite all queries.
    List<Integer> limits = new ArrayList<>(cnt);
    List<Predicate<ChangeData>> predicates = new ArrayList<>(cnt);
    List<ChangeDataSource> sources = new ArrayList<>(cnt);
    for (Predicate<ChangeData> q : queries) {
      q = Predicate.and(q, visibleToMe);
      int limit = getEffectiveLimit(q);
      limits.add(limit);

      // Always bump limit by 1, even if this results in exceeding the permitted
      // max for this user. The only way to see if there are more changes is to
      // ask for one more result from the query.
      Predicate<ChangeData> s = queryRewriter.rewrite(q, start, limit + 1);
      if (!(s instanceof ChangeDataSource)) {
        q = Predicate.and(queryBuilder.status_open(), q);
        s = queryRewriter.rewrite(q, start, limit);
      }
      if (!(s instanceof ChangeDataSource)) {
        throw new QueryParseException("invalid query: " + s);
      }
      predicates.add(s);

      // Don't trust QueryRewriter to have left the visible predicate.
      // TODO(dborowitz): Probably we can.
      AndSource a = new AndSource(ImmutableList.of(s, visibleToMe), start);
      sources.add(a);
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
    return out;
  }

  boolean isDisabled() {
    return permittedLimit <= 0;
  }

  private int getEffectiveLimit(Predicate<ChangeData> p) {
    List<Integer> possibleLimits = new ArrayList<>(3);
    possibleLimits.add(permittedLimit);
    if (limitFromCaller > 0) {
      possibleLimits.add(limitFromCaller);
    }
    Integer limitFromPredicate = LimitPredicate.getLimit(p);
    if (limitFromPredicate != null) {
      possibleLimits.add(limitFromPredicate);
    }
    return Ordering.natural().min(possibleLimits);
  }
}
