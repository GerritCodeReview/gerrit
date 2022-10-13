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

package com.google.gerrit.index.query;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.exceptions.StorageException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
  public ResultSet<T> read() {
    if (source == null) {
      throw new StorageException("No DataSource: " + this);
    }

    // ResultSets are lazy. Calling #read here first and then dealing with ResultSets only when
    // requested allows the index to run asynchronous queries.
    ResultSet<T> resultSet = source.read();
    return new LazyResultSet<>(
        () -> {
          List<T> r = new ArrayList<>();

          List<T> resultList = new ArrayList<>();
          buffer(resultSet).forEach(t -> resultList.add(t));
          T last = resultList.isEmpty() ? null : Iterables.getLast(resultList);
          int nextStart = resultList.size();
          List<T> matched = matchAll(resultList);
          matched.forEach(m -> r.add(m));
          boolean skipped = matched.size() != resultList.size();

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

              List<T> rl = new ArrayList<>();
              buffer(next).forEach(e -> rl.add(e));
              nextStart += rl.size();
              matched = matchAll(rl);
              matched.forEach(m -> r.add(m));
            }
          }

          if (start >= r.size()) {
            return ImmutableList.of();
          } else if (start > 0) {
            return ImmutableList.copyOf(r.subList(start, r.size()));
          }
          return ImmutableList.copyOf(r);
        });
  }

  @Override
  public ResultSet<FieldBundle> readRaw() {
    // TOOD(hiesel): Implement
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public boolean isMatchable() {
    return isVisibleToPredicate != null || super.isMatchable();
  }

  @Override
  public boolean match(T object) {
    if (isVisibleToPredicate != null && !isVisibleToPredicate.match(object)) {
      return false;
    }

    if (super.isMatchable() && !super.match(object)) {
      return false;
    }

    return true;
  }

  @Override
  public List<T> matchAll(List<T> in) {
    List<T> filtered = in;
    if (isVisibleToPredicate != null) {
      filtered =
          in.stream().filter(data -> isVisibleToPredicate.match(data)).collect(Collectors.toList());
    }
    return super.isMatchable() ? super.matchAll(filtered) : filtered;
  }

  private Iterable<T> buffer(ResultSet<T> scanner) {
    return FluentIterable.from(Iterables.partition(scanner, 50))
        .transformAndConcat(this::transformBuffer);
  }

  protected List<T> transformBuffer(List<T> buffer) {
    return buffer;
  }

  @Override
  public int getCardinality() {
    return cardinality;
  }

  @SuppressWarnings("unchecked")
  private PaginatingSource<T> toPaginatingSource(Predicate<T> pred) {
    return new PaginatingSource<>((DataSource<T>) pred, start, indexConfig) {
      @Override
      protected boolean match(T object) {
        return AndSource.this.match(object);
      }

      @Override
      protected boolean isMatchable() {
        return AndSource.this.isMatchable();
      }
    };
  }
}
