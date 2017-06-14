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

import static com.google.common.base.Preconditions.checkState;

import com.google.gwtorm.server.OrmException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Negates the result of another predicate. */
public class NotPredicate<T> extends Predicate<T> implements Matchable<T> {
  private final Predicate<T> that;

  protected NotPredicate(Predicate<T> that) {
    if (that instanceof NotPredicate) {
      throw new IllegalArgumentException("Double negation unsupported");
    }
    this.that = that;
  }

  @Override
  public final List<Predicate<T>> getChildren() {
    return Collections.singletonList(that);
  }

  @Override
  public final int getChildCount() {
    return 1;
  }

  @Override
  public final Predicate<T> getChild(int i) {
    if (i != 0) {
      throw new ArrayIndexOutOfBoundsException(i);
    }
    return that;
  }

  @Override
  public Predicate<T> copy(Collection<? extends Predicate<T>> children) {
    if (children.size() != 1) {
      throw new IllegalArgumentException("Expected exactly one child");
    }
    return new NotPredicate<>(children.iterator().next());
  }

  @Override
  public boolean isMatchable() {
    return that.isMatchable();
  }

  @Override
  public boolean match(T object) throws OrmException {
    checkState(
        that.isMatchable(),
        "match invoked, but child predicate %s doesn't implement %s",
        that,
        Matchable.class.getName());
    return !that.asMatchable().match(object);
  }

  @Override
  public int getCost() {
    return that.estimateCost();
  }

  @Override
  public int hashCode() {
    return ~that.hashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    }
    return getClass() == other.getClass()
        && getChildren().equals(((Predicate<?>) other).getChildren());
  }

  @Override
  public final String toString() {
    return "-" + that.toString();
  }
}
