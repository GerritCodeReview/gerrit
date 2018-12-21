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

import com.google.common.collect.ImmutableList;
import java.util.Iterator;

/**
 * Result from any data store query function.
 *
 * @param <T> type of entity being returned by the query.
 */
public interface ResultSet<T> extends Iterable<T> {
  /**
   * Obtain an iterator to loop through the results.
   *
   * <p>The iterator can be obtained only once. When the iterator completes ( <code>hasNext()</code>
   * returns false) {@link #close()} will be automatically called.
   */
  @Override
  Iterator<T> iterator();

  /**
   * Materialize all results as a single list.
   *
   * <p>Prior to returning {@link #close()} is invoked. This method must not be combined with {@link
   * #iterator()} on the same instance.
   *
   * @return immutable list of the complete results.
   */
  ImmutableList<T> toList();

  /**
   * Close the result, discarding any further results.
   *
   * <p>This method may be invoked more than once. Its main use is to stop obtaining results before
   * the iterator has finished.
   */
  void close();
}
