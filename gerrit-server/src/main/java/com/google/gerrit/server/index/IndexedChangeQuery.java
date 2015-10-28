// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.index;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.primitives.Ints;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.Paginated;
import com.google.gerrit.server.query.change.QueryOptions;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Wrapper combining an {@link IndexPredicate} together with a
 * {@link ChangeDataSource} that returns matching results from the index.
 * <p>
 * Appropriate to return as the rootmost predicate that can be processed using
 * the secondary index; such predicates must also implement
 * {@link ChangeDataSource} to be chosen by the query processor.
 */
public class IndexedChangeQuery extends Predicate<ChangeData>
    implements ChangeDataSource, Paginated {
  @VisibleForTesting
  static QueryOptions convertOptions(QueryOptions opts) {
    // Increase the limit rather than skipping, since we don't know how many
    // skipped results would have been filtered out by the enclosing AndSource.
    int backendLimit = opts.config().maxLimit();
    int limit = Ints.saturatedCast((long) opts.limit() + opts.start());
    limit = Math.min(limit, backendLimit);
    return QueryOptions.create(opts.config(), 0, limit, opts.fields());
  }

  private final ChangeIndex index;

  private QueryOptions opts;
  private Predicate<ChangeData> pred;
  private ChangeDataSource source;

  public IndexedChangeQuery(ChangeIndex index, Predicate<ChangeData> pred,
      QueryOptions opts) throws QueryParseException {
    this.index = index;
    this.opts = convertOptions(opts);
    this.pred = pred;
    this.source = index.getSource(pred, this.opts);
  }

  @Override
  public int getChildCount() {
    return 1;
  }

  @Override
  public Predicate<ChangeData> getChild(int i) {
    if (i == 0) {
      return pred;
    }
    throw new ArrayIndexOutOfBoundsException(i);
  }

  @Override
  public List<Predicate<ChangeData>> getChildren() {
    return ImmutableList.of(pred);
  }

  @Override
  public QueryOptions getOptions() {
    return opts;
  }

  @Override
  public int getCardinality() {
    return source != null ? source.getCardinality() : opts.limit();
  }

  @Override
  public boolean hasChange() {
    return index.getSchema().hasField(ChangeField.CHANGE);
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    final ChangeDataSource currSource = source;
    final ResultSet<ChangeData> rs = currSource.read();

    return new ResultSet<ChangeData>() {
      @Override
      public Iterator<ChangeData> iterator() {
        return Iterables.transform(
            rs,
            new Function<ChangeData, ChangeData>() {
              @Override
              public
              ChangeData apply(ChangeData input) {
                input.cacheFromSource(currSource);
                return input;
              }
            }).iterator();
      }

      @Override
      public List<ChangeData> toList() {
        List<ChangeData> r = rs.toList();
        for (ChangeData cd : r) {
          cd.cacheFromSource(currSource);
        }
        return r;
      }

      @Override
      public void close() {
        rs.close();
      }
    };
  }

  @Override
  public ResultSet<ChangeData> restart(int start) throws OrmException {
    opts = opts.withStart(start);
    try {
      source = index.getSource(pred, opts);
    } catch (QueryParseException e) {
      // Don't need to show this exception to the user; the only thing that
      // changed about pred was its start, and any other QPEs that might happen
      // should have already thrown from the constructor.
      throw new OrmException(e);
    }
    // Don't convert start to a limit, since the caller of this method (see
    // AndSource) has calculated the actual number to skip.
    return read();
  }

  @Override
  public Predicate<ChangeData> copy(
      Collection<? extends Predicate<ChangeData>> children) {
    return this;
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    return (source != null && cd.isFromSource(source)) || pred.match(cd);
  }

  @Override
  public int getCost() {
    // Index queries are assumed to be cheaper than any other type of query, so
    // so try to make sure they get picked. Note that pred's cost may be higher
    // because it doesn't know whether it's being used in an index query or not.
    return 1;
  }

  @Override
  public int hashCode() {
    return pred.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    IndexedChangeQuery o = (IndexedChangeQuery) other;
    return pred.equals(o.pred)
        && opts.equals(o.opts);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("index")
        .add("p", pred)
        .add("opts", opts)
        .toString();
  }
}
