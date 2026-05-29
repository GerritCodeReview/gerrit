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

package com.google.gerrit.index;

import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;

/**
 * Rewriter to sanitize queries before they are sent to the index. The idea here is that the
 * rewriter swaps out predicates so that the query can be processed by the index.
 */
public interface IndexRewriter<T> {

  /**
   * Returns a sanitized version of the provided predicate. Uses {@link QueryOptions} to enforce
   * index-specific limits such as {@code maxTerms}.
   */
  Predicate<T> rewrite(Predicate<T> in, QueryOptions opts) throws QueryParseException;
}
