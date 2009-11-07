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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Requires all predicates to be true. */
public final class AndPredicate extends Predicate {
  private final Predicate[] children;

  public AndPredicate(final Predicate... that) {
    this(Arrays.asList(that));
  }

  public AndPredicate(final Collection<Predicate> that) {
    final ArrayList<Predicate> tmp = new ArrayList<Predicate>(that.size());
    for (Predicate p : that) {
      if (p instanceof AndPredicate) {
        tmp.addAll(p.getChildren());
      } else {
        tmp.add(p);
      }
    }
    if (tmp.size() < 2) {
      throw new IllegalArgumentException("Need at least two predicates");
    }
    children = new Predicate[tmp.size()];
    tmp.toArray(children);
  }

  @Override
  public List<Predicate> getChildren() {
    return Collections.unmodifiableList(Arrays.asList(children));
  }

  @Override
  public int getChildCount() {
    return children.length;
  }

  @Override
  public Predicate getChild(final int i) {
    return children[i];
  }

  @Override
  public int hashCode() {
    return children[0].hashCode() * 31 + children[1].hashCode();
  }

  @Override
  public boolean equals(final Object other) {
    return other instanceof AndPredicate
        && getChildren().equals(((AndPredicate) other).getChildren());
  }

  @Override
  public String toString() {
    final StringBuilder r = new StringBuilder();
    r.append("(");
    for (int i = 0; i < children.length; i++) {
      if (i != 0) {
        r.append(" ");
      }
      r.append(children[i]);
    }
    r.append(")");
    return r.toString();
  }
}
