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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.gerrit.index.Index;
import com.google.gerrit.index.IndexCollection;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.IndexRewriter;
import com.google.gerrit.index.QueryOptions;
import com.google.gerrit.index.SchemaDefinitions;
import com.google.gerrit.index.query.DataSource;
import com.google.gerrit.index.query.LimitPredicate;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.index.query.QueryResult;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer1;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.gwtorm.server.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lower-level implementation for executing a single query over a secondary index.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
public abstract class QueryProcessor<T> {
  protected static class Metrics {
    final Timer1<String> executionTime;

    Metrics(MetricMaker metricMaker) {
      Field<String> index = Field.ofString("index", "index name");
      executionTime =
          metricMaker.newTimer(
              "query/query_latency",
              new Description("Successful query latency, accumulated over the life of the process")
                  .setCumulative()
                  .setUnit(Description.Units.MILLISECONDS),
              index);
    }
  }

  private final Metrics metrics;
  private final SchemaDefinitions<T> schemaDef;
  private final IndexConfig indexConfig;
  private final IndexCollection<?, T, ? extends Index<?, T>> indexes;
  private final IndexRewriter<T> rewriter;
  private final String limitField;

  // This class is not generally thread-safe, but programmer error may result in it being shared
  // across threads. At least ensure the bit for checking if it's been used is threadsafe.
  private final AtomicBoolean used;

  protected int start;

  private boolean enforceVisibility = true;
  private int userProvidedLimit;
  private int permittedLimit;
  private Set<String> requestedFields;

  protected QueryProcessor(
      MetricMaker metricMaker,
      SchemaDefinitions<T> schemaDef,
      IndexConfig indexConfig,
      IndexCollection<?, T, ? extends Index<?, T>> indexes,
      IndexRewriter<T> rewriter,
      String limitField) {
    this.metrics = new Metrics(metricMaker);
    this.schemaDef = schemaDef;
    this.indexConfig = indexConfig;
    this.indexes = indexes;
    this.rewriter = rewriter;
    this.limitField = limitField;
    this.used = new AtomicBoolean(false);
  }

  public QueryProcessor<T> setStart(int n) {
    start = n;
    return this;
  }

  public QueryProcessor<T> enforceVisibility(boolean enforce) {
    enforceVisibility = enforce;
    return this;
  }

  /**
   * Set an end-user-provided limit on the number of results returned.
   *
   * <p>Since this limit is provided by an end user, it may exceed the limit that they are
   * authorized to use (see {@link #setPermittedLimit(int)}). This is allowed; the processor will
   * take multiple possible limits into account and choose the one that makes the most sense.
   *
   * @param n limit; zero or negative means no limit.
   * @return this.
   */
  public QueryProcessor<T> setUserProvidedLimit(int n) {
    userProvidedLimit = n;
    return this;
  }

  /**
   * Set the maximum limit that the calling user is permitted to set.
   *
   * <p>This is the maximum allowable limit that a user can provide (see {@link
   * #setUserProvidedLimit(int)}), taking permissions into account. It is generally called
   * automatically by subclass constructors, and users of specific subclasses should not have to
   * call this method.
   *
   * <p>This value is completely ignored if {@link #enforceVisibility(boolean) visibility is not
   * enforced}.
   *
   * @param n limit; zero or negative means this user is not permitted to query at all, and as a
   *     result {@link #isDisabled()} will return true (if visibility is enforced).
   * @return this.
   */
  public QueryProcessor<T> setPermittedLimit(int n) {
    permittedLimit = n;
    return this;
  }

  public QueryProcessor<T> setRequestedFields(Set<String> fields) {
    requestedFields = fields;
    return this;
  }

  /**
   * Query for entities that match a structured query.
   *
   * <p>Callers must check {@link #isDisabled()} and not call if disabled.
   *
   * @see #query(List)
   * @param query the query.
   * @return results of the query.
   */
  public QueryResult<T> query(Predicate<T> query) throws OrmException, QueryParseException {
    return query(ImmutableList.of(query)).get(0);
  }

  /**
   * Perform multiple queries in parallel.
   *
   * <p>Callers must check {@link #isDisabled()} and not call if disabled.
   *
   * @param queries list of queries.
   * @return results of the queries, one QueryResult per input query, in the same order as the
   *     input.
   */
  public List<QueryResult<T>> query(List<Predicate<T>> queries)
      throws OrmException, QueryParseException {
    try {
      return query(null, queries);
    } catch (OrmRuntimeException e) {
      throw new OrmException(e.getMessage(), e);
    } catch (OrmException e) {
      if (e.getCause() != null) {
        Throwables.throwIfInstanceOf(e.getCause(), QueryParseException.class);
      }
      throw e;
    }
  }

  private List<QueryResult<T>> query(List<String> queryStrings, List<Predicate<T>> queries)
      throws OrmException, QueryParseException {
    checkState(!isDisabled(), "%s is disabled", getClass().getSimpleName());
    checkState(!used.getAndSet(true), "%s has already been used", getClass().getSimpleName());
    long startNanos = System.nanoTime();

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
            "Cannot go beyond page " + indexConfig.maxPages() + " of results");
      }

      // Always bump limit by 1, even if this results in exceeding the permitted
      // max for this user. The only way to see if there are more entities is to
      // ask for one more result from the query.
      QueryOptions opts = createOptions(indexConfig, start, limit + 1, getRequestedFields());
      Predicate<T> pred = rewriter.rewrite(q, opts);
      if (enforceVisibility) {
        pred = enforceVisibility(pred);
      }
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
      out.add(
          QueryResult.create(
              queryStrings != null ? queryStrings.get(i) : null,
              predicates.get(i),
              limits.get(i),
              matches.get(i).toList()));
    }

    // only measure successful queries
    metrics.executionTime.record(
        schemaDef.getName(), System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    return out;
  }

  protected QueryOptions createOptions(
      IndexConfig indexConfig, int start, int limit, Set<String> requestedFields) {
    return QueryOptions.create(indexConfig, start, limit, requestedFields);
  }

  /**
   * Invoked after the query was rewritten. Subclasses must overwrite this method to filter out
   * results that are not visible to the calling user.
   *
   * @param pred the query
   * @return the modified query
   */
  protected abstract Predicate<T> enforceVisibility(Predicate<T> pred);

  private Set<String> getRequestedFields() {
    if (requestedFields != null) {
      return requestedFields;
    }
    Index<?, T> index = indexes.getSearchIndex();
    return index != null ? index.getSchema().getStoredFields().keySet() : ImmutableSet.<String>of();
  }

  public boolean isDisabled() {
    return enforceVisibility && permittedLimit <= 0;
  }

  private int getBackendSupportedLimit() {
    return indexConfig.maxLimit();
  }

  private int getEffectiveLimit(Predicate<T> p) {
    List<Integer> possibleLimits = new ArrayList<>(4);

    possibleLimits.add(getBackendSupportedLimit());

    checkState(permittedLimit > 0, "user limit should have been positive");
    possibleLimits.add(permittedLimit);

    if (userProvidedLimit > 0) {
      possibleLimits.add(userProvidedLimit);
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
