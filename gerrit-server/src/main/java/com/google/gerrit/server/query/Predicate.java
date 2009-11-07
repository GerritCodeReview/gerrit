// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.query;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * An abstract predicate tree for any form of query.
 * <p>
 * Implementations should be immutable, and therefore also be thread-safe. They
 * also should ensure their immutable promise by defensively copying any
 * structures which might be modified externally, but were passed into the
 * object's constructor.
 * <p>
 * Predicates should support deep inspection whenever possible, so that generic
 * algorithms can be written to operate against them. Predicates which contain
 * other predicates should override {@link #getChildren()} to return the list of
 * children nested within the predicate.
 */
public abstract class Predicate {
  /** Combine the passed predicates into a single AND node. */
  public static Predicate and(final Predicate... that) {
    return new AndPredicate(that);
  }

  /** Combine the passed predicates into a single AND node. */
  public static Predicate and(final Collection<Predicate> that) {
    return new AndPredicate(that);
  }

  /** Combine the passed predicates into a single OR node. */
  public static Predicate or(final Predicate... that) {
    return new OrPredicate(that);
  }

  /** Combine the passed predicates into a single OR node. */
  public static Predicate or(final Collection<Predicate> that) {
    return new OrPredicate(that);
  }

  /** Invert the passed node; same as {@code that.not()}. */
  public static Predicate not(final Predicate that) {
    return that.not();
  }

  /** Get the children of this predicate, if any. */
  public List<Predicate> getChildren() {
    return Collections.emptyList();
  }

  /** Same as {@code getChildren().size()} */
  public int getChildCount() {
    return getChildren().size();
  }

  /** Same as {@code getChildren().get(i)} */
  public Predicate getChild(final int i) {
    return getChildren().get(i);
  }

  /** Obtain the inverse of this predicate. */
  public Predicate not() {
    return new NotPredicate(this);
  }

  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object other);
}
