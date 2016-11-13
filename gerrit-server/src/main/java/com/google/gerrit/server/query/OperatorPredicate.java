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

/** Predicate to filter a field by matching value. */
public abstract class OperatorPredicate<T> extends Predicate<T> {
  private final String name;
  private final String value;

  protected OperatorPredicate(final String name, final String value) {
    this.name = name;
    this.value = value;
  }

  public String getOperator() {
    return name;
  }

  public String getValue() {
    return value;
  }

  @Override
  public Predicate<T> copy(final Collection<? extends Predicate<T>> children) {
    if (!children.isEmpty()) {
      throw new IllegalArgumentException("Expected 0 children");
    }
    return this;
  }

  @Override
  public int hashCode() {
    return getOperator().hashCode() * 31 + getValue().hashCode();
  }

  @Override
  public boolean equals(final Object other) {
    if (other == null) {
      return false;
    }
    if (getClass() == other.getClass()) {
      final OperatorPredicate<?> p = (OperatorPredicate<?>) other;
      return getOperator().equals(p.getOperator()) && getValue().equals(p.getValue());
    }
    return false;
  }

  @Override
  public String toString() {
    final String val = getValue();
    if (QueryParser.isSingleWord(val)) {
      return getOperator() + ":" + val;
    }
    return getOperator() + ":\"" + val + "\"";
  }
}
