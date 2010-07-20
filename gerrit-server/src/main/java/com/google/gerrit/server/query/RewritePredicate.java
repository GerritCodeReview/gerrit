// Copyright (C) 2010 The Android Open Source Project
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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class RewritePredicate<T> extends Predicate<T> {
  private String name = getClass().getName();
  private List<Predicate<T>> children = Collections.emptyList();

  void init(String name, Predicate<T>[] args) {
    this.name = name;
    this.children = Arrays.asList(args);
  }

  @Override
  public Predicate<T> copy(Collection<? extends Predicate<T>> children) {
    return this;
  }

  @Override
  public boolean equals(Object other) {
    return getClass() == other.getClass()
        && children.equals(((RewritePredicate) other).children);
  }

  @Override
  public int hashCode() {
    int h = getClass().hashCode();
    if (!children.isEmpty()) {
      h *= 31;
      h += children.get(0).hashCode();
    }
    return h;
  }

  @Override
  public final String toString() {
    final StringBuilder r = new StringBuilder();
    r.append(name);
    if (!children.isEmpty()) {
      r.append("(");
      for (int i = 0; i < children.size(); i++) {
        if (i != 0) {
          r.append(" ");
        }
        r.append(children.get(i));
      }
      r.append(")");
    }
    return r.toString();
  }
}
