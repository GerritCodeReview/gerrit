// Copyright 2008 Google Inc.
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

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.List;

/**
 * Result set for queries that run synchronously or for cases where the result is already known and
 * we just need to pipe it back through our interfaces.
 *
 * <p>If your implementation benefits from asynchronous execution (i.e. dispatching a query and
 * awaiting results only when {@link ResultSet#toList()} is called, consider using {@link
 * LazyResultSet}.
 */
public class ListResultSet<T> implements ResultSet<T> {
  private ImmutableList<T> results;

  public ListResultSet(List<T> r) {
    results = ImmutableList.copyOf(requireNonNull(r, "results can't be null"));
  }

  @Override
  public Iterator<T> iterator() {
    return toList().iterator();
  }

  @Override
  public ImmutableList<T> toList() {
    if (results == null) {
      throw new IllegalStateException("Results already obtained");
    }
    ImmutableList<T> r = results;
    results = null;
    return r;
  }

  @Override
  public void close() {
    results = null;
  }

  @Override
  public Object searchAfter() {
    return null;
  }
}
