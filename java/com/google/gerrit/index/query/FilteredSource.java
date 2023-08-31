// Copyright (C) 2023 The Android Open Source Project
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
import java.util.ArrayList;
import java.util.List;

public class FilteredSource<T> implements DataSource<T> {

  protected final DataSource<T> source;
  protected final int start;
  protected final int cardinality;
  protected final IndexConfig indexConfig;

  public FilteredSource(DataSource<T> source, int start, IndexConfig indexConfig) {
    checkArgument(start >= 0, "negative start: %s", start);
    this.source = source;
    this.start = start;
    this.cardinality = source.getCardinality();
    this.indexConfig = indexConfig;
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

  protected Iterable<T> buffer(ResultSet<T> scanner) {
    int PARTITION_SIZE = 50;
    return FluentIterable.from(Iterables.partition(scanner, PARTITION_SIZE))
        .transformAndConcat(this::transformBuffer);
  }

  protected List<T> transformBuffer(List<T> buffer) {
    return buffer;
  }

  @Override
  public int getCardinality() {
    return cardinality;
  }

  protected boolean match(T object) {
    return true;
  }

  protected boolean isMatchable() {
    return true;
  }
}
