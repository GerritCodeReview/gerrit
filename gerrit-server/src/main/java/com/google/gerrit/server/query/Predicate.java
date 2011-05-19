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

import com.google.gwtorm.client.OrmException;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * An abstract predicate tree for any form of query.
 * <p>
 * Implementations should be immutable, such that the meaning of a predicate
 * never changes once constructed. They should ensure their immutable promise by
 * defensively copying any structures which might be modified externally, but
 * was passed into the object's constructor.
 * <p>
 * However, implementations <i>may</i> retain non-thread-safe caches internally,
 * to speed up evaluation operations within the context of one thread's
 * evaluation of the predicate. As a result, callers should assume predicates
 * are not thread-safe, but that two predicate graphs produce the same results
 * given the same inputs if they are {@link #equals(Object)}.
 * <p>
 * Predicates should support deep inspection whenever possible, so that generic
 * algorithms can be written to operate against them. Predicates which contain
 * other predicates should override {@link #getChildren()} to return the list of
 * children nested within the predicate.
 *
 * @type <T> type of object the predicate can evaluate in memory.
 */
public abstract class Predicate<T> {
  /** Combine the passed predicates into a single AND node. */
  public static <T> Predicate<T> and(final Predicate<T>... that) {
    return new AndPredicate<T>(that);
  }

  /** Combine the passed predicates into a single AND node. */
  public static <T> Predicate<T> and(
      final Collection<? extends Predicate<T>> that) {
    return new AndPredicate<T>(that);
  }

  /** Combine the passed predicates into a single OR node. */
  public static <T> Predicate<T> or(final Predicate<T>... that) {
    return new OrPredicate<T>(that);
  }

  /** Combine the passed predicates into a single OR node. */
  public static <T> Predicate<T> or(
      final Collection<? extends Predicate<T>> that) {
    return new OrPredicate<T>(that);
  }

  /** Invert the passed node. */
  public static <T> Predicate<T> not(final Predicate<T> that) {
    if (that instanceof NotPredicate) {
      // Negate of a negate is the original predicate.
      //
      return that.getChild(0);
    }
    return new NotPredicate<T>(that);
  }

  /** Get the children of this predicate, if any. */
  public List<Predicate<T>> getChildren() {
    return Collections.emptyList();
  }

  /** Same as {@code getChildren().size()} */
  public int getChildCount() {
    return getChildren().size();
  }

  /** Same as {@code getChildren().get(i)} */
  public Predicate<T> getChild(final int i) {
    return getChildren().get(i);
  }

  /** Create a copy of this predicate, with new children. */
  public abstract Predicate<T> copy(Collection<? extends Predicate<T>> children);

  /**
   * Does this predicate match this object?
   *
   * @throws OrmException
   */
  public abstract boolean match(T object) throws OrmException;

  /** @return a cost estimate to run this predicate, higher figures cost more. */
  public abstract int getCost();

  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object other);
}
