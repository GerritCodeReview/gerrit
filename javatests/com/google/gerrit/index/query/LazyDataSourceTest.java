// Copyright (C) 2019 The Android Open Source Project
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

import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeDataSource;
import com.google.gerrit.server.query.change.OrSource;
import java.util.Collection;
import java.util.Iterator;
import org.junit.Test;

/**
 * Tests that boolean data sources are lazy in that they don't call {@link ResultSet#toList()} or
 * {@link ResultSet#toList()}. This is necessary because it allows Gerrit to send multiple queries
 * to the index in parallel, have the results come in asynchronously and wait for them only when we
 * call aforementioned methods on the {@link ResultSet}.
 */
public class LazyDataSourceTest {

  /** Helper to avoid a mock which would be hard to create because of the type inference. */
  static class LazyPredicate extends Predicate<ChangeData> implements ChangeDataSource {
    @Override
    public int getCardinality() {
      return 1;
    }

    @Override
    public ResultSet<ChangeData> read() {
      return new FailingResultSet<>();
    }

    @Override
    public ResultSet<FieldBundle> readRaw() {
      return new FailingResultSet<>();
    }

    @Override
    public Predicate<ChangeData> copy(Collection<? extends Predicate<ChangeData>> children) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public int hashCode() {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean equals(Object other) {
      throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean hasChange() {
      throw new UnsupportedOperationException("not implemented");
    }
  }

  /** Implementation that throws {@link AssertionError} when accessing results. */
  static class FailingResultSet<T> implements ResultSet<T> {
    @Override
    public Iterator<T> iterator() {
      throw new AssertionError(
          "called iterator() on the result set, but shouldn't have because the data source must be lazy");
    }

    @Override
    public ImmutableList<T> toList() {
      throw new AssertionError(
          "called toList() on the result set, but shouldn't have because the data source must be lazy");
    }

    @Override
    public void close() {
      // No-op
    }
  }

  @Test
  public void andSourceIsLazy() {
    AndSource<ChangeData> and = new AndSource<>(ImmutableList.of(new LazyPredicate()));
    ResultSet<ChangeData> resultSet = and.read();
    assertThrows(AssertionError.class, () -> resultSet.toList());
  }

  @Test
  public void orSourceIsLazy() {
    OrSource or = new OrSource(ImmutableList.of(new LazyPredicate()));
    ResultSet<ChangeData> resultSet = or.read();
    assertThrows(AssertionError.class, () -> resultSet.toList());
  }
}
