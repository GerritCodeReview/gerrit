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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Requires all predicates to be true. */
public class AndPredicate<T> extends Predicate<T> implements Matchable<T> {
  private final List<Predicate<T>> children;
  private final int cost;

  @SafeVarargs
  protected AndPredicate(final Predicate<T>... that) {
    this(Arrays.asList(that));
  }

  protected AndPredicate(final Collection<? extends Predicate<T>> that) {
    List<Predicate<T>> t = new ArrayList<>(that.size());
    int c = 0;
    for (Predicate<T> p : that) {
      if (getClass() == p.getClass()) {
        for (Predicate<T> gp : p.getChildren()) {
          t.add(gp);
          c += gp.estimateCost();
        }
      } else {
        t.add(p);
        c += p.estimateCost();
      }
    }
    children = t;
    cost = c;
  }

  @Override
  public final List<Predicate<T>> getChildren() {
    return Collections.unmodifiableList(children);
  }

  @Override
  public final int getChildCount() {
    return children.size();
  }

  @Override
  public final Predicate<T> getChild(final int i) {
    return children.get(i);
  }

  @Override
  public Predicate<T> copy(final Collection<? extends Predicate<T>> children) {
    return new AndPredicate<>(children);
  }

  @Override
  public boolean isMatchable() {
    for (Predicate<T> c : children) {
      if (!c.isMatchable()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public boolean match(final T object) throws OrmException {
    for (Predicate<T> c : children) {
      checkState(
          c.isMatchable(),
          "match invoked, but child predicate %s " + "doesn't implement %s",
          c,
          Matchable.class.getName());
      if (!c.asMatchable().match(object)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int getCost() {
    return cost;
  }

  @Override
  public int hashCode() {
    return getChild(0).hashCode() * 31 + getChild(1).hashCode();
  }

  @Override
  public boolean equals(final Object other) {
    if (other == null) {
      return false;
    }
    return getClass() == other.getClass()
        && getChildren().equals(((Predicate<?>) other).getChildren());
  }

  @Override
  public String toString() {
    final StringBuilder r = new StringBuilder();
    r.append("(");
    for (int i = 0; i < getChildCount(); i++) {
      if (i != 0) {
        r.append(" ");
      }
      r.append(getChild(i));
    }
    r.append(")");
    return r.toString();
  }
}
