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

import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.PaginationType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AndSource<T> extends AndPredicate<T> implements DataSource<T> {
  protected final DataSource<T> source;

  private final int start;
  private final int cardinality;
  private final IndexConfig indexConfig;

  public AndSource(Collection<? extends Predicate<T>> that, IndexConfig indexConfig) {
    this(that, 0, indexConfig);
  }

  public AndSource(Collection<? extends Predicate<T>> that, int start, IndexConfig indexConfig) {
    super(that);
    checkArgument(start >= 0, "negative start: %s", start);
    this.start = start;
    this.indexConfig = indexConfig;

    int c = Integer.MAX_VALUE;
    Predicate<T> selectedSource = null;
    int minCardinality = Integer.MAX_VALUE;
    for (Predicate<T> p : getChildren()) {
      if (p instanceof DataSource) {
        DataSource<?> source = (DataSource<?>) p;
        int cardinality = source.getCardinality();
        c = Math.min(c, source.getCardinality());

        if (selectedSource == null
            || cardinality < minCardinality
            || (cardinality == minCardinality
                && p.estimateCost() < selectedSource.estimateCost())) {
          selectedSource = p;
          minCardinality = cardinality;
        }
      }
    }
    if (selectedSource == null) {
      throw new IllegalArgumentException("No DataSource Found");
    }
    this.source = toDataSource(selectedSource);
    this.cardinality = c;
  }

  @Override
  public ResultSet<T> read() {
    if (source == null) {
      throw new StorageException("No DataSource: " + this);
    } else if (source instanceof PaginatingSource) {
      return source.read();
    }
    return readAndFilter();
  }

  private ResultSet<T> readAndFilter() {
    // ResultSets are lazy. Calling #read here first and then dealing with ResultSets only when
    // requested allows the index to run asynchronous queries.
    ResultSet<T> resultSet = source.read();
    return new LazyResultSet<>(
        () -> {
          List<T> r = new ArrayList<>();
          for (T data : buffer(resultSet)) {
            if (!isMatchable() || match(data)) {
              r.add(data);
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
    return source.readRaw();
  }

  @Override
  public boolean match(T object) {
    if (super.isMatchable() && !super.match(object)) {
      return false;
    }

    return true;
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
  private DataSource<T> toDataSource(Predicate<T> pred) {
    if (indexConfig.paginationType().equals(PaginationType.NONE)) {
      return (DataSource<T>) pred;
    }
    return toPaginatingSource(pred);
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
