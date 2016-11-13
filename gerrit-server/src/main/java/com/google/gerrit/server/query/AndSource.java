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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.OrmRuntimeException;
import com.google.gwtorm.server.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AndSource<T> extends AndPredicate<T>
    implements DataSource<T>, Comparator<Predicate<T>> {
  protected final DataSource<T> source;

  private final IsVisibleToPredicate<T> isVisibleToPredicate;
  private final int start;
  private final int cardinality;

  public AndSource(Collection<? extends Predicate<T>> that) {
    this(that, null, 0);
  }

  public AndSource(Predicate<T> that, IsVisibleToPredicate<T> isVisibleToPredicate) {
    this(that, isVisibleToPredicate, 0);
  }

  public AndSource(Predicate<T> that, IsVisibleToPredicate<T> isVisibleToPredicate, int start) {
    this(ImmutableList.of(that), isVisibleToPredicate, start);
  }

  public AndSource(
      Collection<? extends Predicate<T>> that,
      IsVisibleToPredicate<T> isVisibleToPredicate,
      int start) {
    super(that);
    checkArgument(start >= 0, "negative start: %s", start);
    this.isVisibleToPredicate = isVisibleToPredicate;
    this.start = start;

    int c = Integer.MAX_VALUE;
    DataSource<T> s = null;
    int minCost = Integer.MAX_VALUE;
    for (Predicate<T> p : sort(getChildren())) {
      if (p instanceof DataSource) {
        c = Math.min(c, ((DataSource<?>) p).getCardinality());

        int cost = p.estimateCost();
        if (cost < minCost) {
          s = toDataSource(p);
          minCost = cost;
        }
      }
    }
    this.source = s;
    this.cardinality = c;
  }

  @Override
  public ResultSet<T> read() throws OrmException {
    try {
      return readImpl();
    } catch (OrmRuntimeException err) {
      if (err.getCause() != null) {
        Throwables.throwIfInstanceOf(err.getCause(), OrmException.class);
      }
      throw new OrmException(err);
    }
  }

  private ResultSet<T> readImpl() throws OrmException {
    if (source == null) {
      throw new OrmException("No DataSource: " + this);
    }
    List<T> r = new ArrayList<>();
    T last = null;
    int nextStart = 0;
    boolean skipped = false;
    for (T data : buffer(source.read())) {
      if (!isMatchable() || match(data)) {
        r.add(data);
      } else {
        skipped = true;
      }
      last = data;
      nextStart++;
    }

    if (skipped && last != null && source instanceof Paginated) {
      // If our source is a paginated source and we skipped at
      // least one of its results, we may not have filled the full
      // limit the caller wants.  Restart the source and continue.
      //
      @SuppressWarnings("unchecked")
      Paginated<T> p = (Paginated<T>) source;
      while (skipped && r.size() < p.getOptions().limit() + start) {
        skipped = false;
        ResultSet<T> next = p.restart(nextStart);

        for (T data : buffer(next)) {
          if (match(data)) {
            r.add(data);
          } else {
            skipped = true;
          }
          nextStart++;
        }
      }
    }

    if (start >= r.size()) {
      r = ImmutableList.of();
    } else if (start > 0) {
      r = ImmutableList.copyOf(r.subList(start, r.size()));
    }
    return new ListResultSet<>(r);
  }

  @Override
  public boolean isMatchable() {
    return isVisibleToPredicate != null || super.isMatchable();
  }

  @Override
  public boolean match(T object) throws OrmException {
    if (isVisibleToPredicate != null && !isVisibleToPredicate.match(object)) {
      return false;
    }

    if (super.isMatchable() && !super.match(object)) {
      return false;
    }

    return true;
  }

  private Iterable<T> buffer(ResultSet<T> scanner) {
    return FluentIterable.from(Iterables.partition(scanner, 50))
        .transformAndConcat(this::transformBuffer);
  }

  protected List<T> transformBuffer(List<T> buffer) throws OrmRuntimeException {
    return buffer;
  }

  @Override
  public int getCardinality() {
    return cardinality;
  }

  private List<Predicate<T>> sort(Collection<? extends Predicate<T>> that) {
    List<Predicate<T>> r = new ArrayList<>(that);
    Collections.sort(r, this);
    return r;
  }

  @Override
  public int compare(Predicate<T> a, Predicate<T> b) {
    int ai = a instanceof DataSource ? 0 : 1;
    int bi = b instanceof DataSource ? 0 : 1;
    int cmp = ai - bi;

    if (cmp == 0) {
      cmp = a.estimateCost() - b.estimateCost();
    }

    if (cmp == 0 && a instanceof DataSource && b instanceof DataSource) {
      DataSource<?> as = (DataSource<?>) a;
      DataSource<?> bs = (DataSource<?>) b;
      cmp = as.getCardinality() - bs.getCardinality();
    }
    return cmp;
  }

  @SuppressWarnings("unchecked")
  private DataSource<T> toDataSource(Predicate<T> pred) {
    return (DataSource<T>) pred;
  }
}
