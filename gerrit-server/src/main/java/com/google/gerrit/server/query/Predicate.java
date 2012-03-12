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

import com.google.gwtorm.server.OrmException;

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
 * @type <C> child type of C (Example: PatchSet is a child of Change)
 */
public abstract class Predicate<T, C> {
  /** Combine the passed predicates into a single AND node. */
  public static <T, C> Predicate<T, C> and(final Predicate<T, C>... that) {
    return new AndPredicate<T, C>(that);
  }

  /** Combine the passed predicates into a single AND node. */
  public static <T, C> Predicate<T, C> and(
      final Collection<? extends Predicate<T, C>> that) {
    return new AndPredicate<T, C>(that);
  }

  /** Combine the passed predicates into a single OR node. */
  public static <T, C> Predicate<T, C> or(final Predicate<T, C>... that) {
    return new OrPredicate<T, C>(that);
  }

  /** Combine the passed predicates into a single OR node. */
  public static <T, C> Predicate<T, C> or(
      final Collection<? extends Predicate<T, C>> that) {
    return new OrPredicate<T, C>(that);
  }

  /** Invert the passed node. */
  public static <T, C> Predicate<T, C> not(final Predicate<T, C> that) {
    if (that instanceof NotPredicate) {
      // Negate of a negate is the original predicate.
      //
      return that.getChild(0);
    }
    return new NotPredicate<T, C>(that);
  }

  /** Get the children of this predicate, if any. */
  public List<Predicate<T, C>> getChildren() {
    return Collections.emptyList();
  }

  /** Same as {@code getChildren().size()} */
  public int getChildCount() {
    return getChildren().size();
  }

  /** Same as {@code getChildren().get(i)} */
  public Predicate<T, C> getChild(final int i) {
    return getChildren().get(i);
  }

  /** Create a copy of this predicate, with new children. */
  public abstract Predicate<T, C> copy(Collection<? extends Predicate<T, C>> children);

  /**
   * Does this predicate match this object?
   *
   * @throws OrmException
   */
  public final boolean match(final T object) throws OrmException {
    return match(object, null);
  }

  /**
   * Does this predicate match this object and subobject?
   *
   * @throws OrmException
   */
  public abstract boolean match(final T object, final C subobject)
      throws OrmException;

  /** @return a cost estimate to run this predicate, higher figures cost more. */
  public abstract int getCost();

  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(Object other);
}
