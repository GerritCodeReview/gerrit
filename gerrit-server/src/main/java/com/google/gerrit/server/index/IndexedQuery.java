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

package com.google.gerrit.server.index;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.query.DataSource;
import com.google.gerrit.server.query.Paginated;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import java.util.Collection;
import java.util.List;

/**
 * Wrapper combining an {@link IndexPredicate} together with a {@link DataSource} that returns
 * matching results from the index.
 *
 * <p>Appropriate to return as the rootmost predicate that can be processed using the secondary
 * index; such predicates must also implement {@link DataSource} to be chosen by the query
 * processor.
 *
 * @param <I> The type of the IDs by which the entities are stored in the index.
 * @param <T> The type of the entities that are stored in the index.
 */
public class IndexedQuery<I, T> extends Predicate<T> implements DataSource<T>, Paginated<T> {
  protected final Index<I, T> index;

  private QueryOptions opts;
  private final Predicate<T> pred;
  protected DataSource<T> source;

  public IndexedQuery(Index<I, T> index, Predicate<T> pred, QueryOptions opts)
      throws QueryParseException {
    this.index = index;
    this.opts = opts;
    this.pred = pred;
    this.source = index.getSource(pred, this.opts);
  }

  @Override
  public int getChildCount() {
    return 1;
  }

  @Override
  public Predicate<T> getChild(int i) {
    if (i == 0) {
      return pred;
    }
    throw new ArrayIndexOutOfBoundsException(i);
  }

  @Override
  public List<Predicate<T>> getChildren() {
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
  public ResultSet<T> read() throws OrmException {
    return source.read();
  }

  @Override
  public ResultSet<T> restart(int start) throws OrmException {
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
  public Predicate<T> copy(Collection<? extends Predicate<T>> children) {
    return this;
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
    IndexedQuery<?, ?> o = (IndexedQuery<?, ?>) other;
    return pred.equals(o.pred) && opts.equals(o.opts);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper("index").add("p", pred).add("opts", opts).toString();
  }
}
