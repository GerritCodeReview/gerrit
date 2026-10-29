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

package com.google.gerrit.index.query;

/**
 * A predicate that can be evaluated with {@link #match(Object)} instead of being sent to the index.
 *
 * <p>Implementations promise that {@link #match(Object)} returns {@code true} for exactly the
 * objects the index would return for this predicate, and that it is cheap enough to run on every
 * candidate returned for the rest of the query (for example a set lookup, not a repository or
 * NoteDb read).
 *
 * <p>An index rewriter may use this to keep a query under the index term limit: rather than failing
 * the query, it may evaluate some of these predicates in memory after the index search. This keeps
 * results the same, although the query may be slower because the index returns more candidates.
 */
public interface PostFilterablePredicate<T> extends Matchable<T> {}
