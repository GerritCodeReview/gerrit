// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.index.query.Predicate;
import com.google.gerrit.index.query.QueryParseException;

/**
 * Rewrites a parsed change query before Gerrit plans its data sources and index query.
 *
 * <p>Invoked once per parsed query, before source planning and secondary-index rewriting. If
 * multiple implementations are registered, each sees the output of the previous one, in
 * registration order. Implementations must return a predicate equivalent to the input; they must
 * not return {@code null}.
 */
@ExtensionPoint
public interface ChangeQueryPredicateRewriter {
  /**
   * Returns a predicate equivalent to {@code in}.
   *
   * @throws QueryParseException if {@code in} cannot be rewritten, e.g. because it combines
   *     predicates in a way this rewriter does not support
   */
  Predicate<ChangeData> rewrite(Predicate<ChangeData> in) throws QueryParseException;
}
