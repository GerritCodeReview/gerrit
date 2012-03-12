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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Requires one predicate to be true. */
public class OrPredicate<T, C> extends Predicate<T, C> {
  private final List<Predicate<T, C>> children;
  private final int cost;

  protected OrPredicate(final Predicate<T, C>... that) {
    this(Arrays.asList(that));
  }

  protected OrPredicate(final Collection<? extends Predicate<T, C>> that) {
    final ArrayList<Predicate<T, C>> t = new ArrayList<Predicate<T, C>>(that.size());
    int c = 0;
    for (Predicate<T, C> p : that) {
      if (getClass() == p.getClass()) {
        for (Predicate<T, C> gp : p.getChildren()) {
          t.add(gp);
          c += gp.getCost();
        }
      } else {
        t.add(p);
        c += p.getCost();
      }
    }
    if (t.size() < 2) {
      throw new IllegalArgumentException("Need at least two predicates");
    }
    children = t;
    cost = c;
  }

  @Override
  public final List<Predicate<T, C>> getChildren() {
    return Collections.unmodifiableList(children);
  }

  @Override
  public final int getChildCount() {
    return children.size();
  }

  @Override
  public final Predicate<T, C> getChild(final int i) {
    return children.get(i);
  }

  @Override
  public Predicate<T, C> copy(final Collection<? extends Predicate<T, C>> children) {
    return new OrPredicate<T, C>(children);
  }

  @Override
  public boolean match(final T object, final C subobject) throws OrmException {
    for (final Predicate<T, C> c : children) {
      if (c.match(object, subobject)) {
        return true;
      }
    }
    return false;
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
    if (other == null)
      return false;
    return getClass() == other.getClass()
        && getChildren().equals(((Predicate<?, ?>) other).getChildren());
  }

  @Override
  public final String toString() {
    final StringBuilder r = new StringBuilder();
    r.append("(");
    for (int i = 0; i < getChildCount(); i++) {
      if (i != 0) {
        r.append(" OR ");
      }
      r.append(getChild(i));
    }
    r.append(")");
    return r.toString();
  }
}
