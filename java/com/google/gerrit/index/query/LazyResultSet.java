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

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import java.util.Iterator;
import java.util.function.Supplier;

/**
 * Result set that allows for asynchronous execution of the actual query. Callers should dispatch
 * the query and call the constructor of this class with a supplier that fetches the result and
 * blocks on it if necessary.
 *
 * <p>If the execution is synchronous or the results are known a priori, consider using {@link
 * ListResultSet}.
 */
public class LazyResultSet<T> implements ResultSet<T> {
  private final Supplier<ImmutableList<T>> resultsCallback;

  private boolean resultsReturned = false;

  public LazyResultSet(Supplier<ImmutableList<T>> r) {
    resultsCallback = requireNonNull(r, "results can't be null");
  }

  @Override
  public Iterator<T> iterator() {
    return toList().iterator();
  }

  @Override
  public ImmutableList<T> toList() {
    if (resultsReturned) {
      throw new IllegalStateException("Results already obtained");
    }
    resultsReturned = true;
    return resultsCallback.get();
  }

  @Override
  public void close() {}

  @Override
  public Object searchAfter() {
    return null;
  }
}
