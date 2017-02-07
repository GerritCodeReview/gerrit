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

/** Predicate to filter a field by matching integer value. */
public abstract class IntPredicate<T> extends OperatorPredicate<T> {
  private final int value;

  public IntPredicate(final String name, final String value) {
    super(name, value);
    this.value = Integer.parseInt(value);
  }

  public IntPredicate(final String name, final int value) {
    super(name, String.valueOf(value));
    this.value = value;
  }

  public int intValue() {
    return value;
  }

  @Override
  public int hashCode() {
    return getOperator().hashCode() * 31 + value;
  }

  @Override
  public boolean equals(final Object other) {
    if (other == null) {
      return false;
    }
    if (getClass() == other.getClass()) {
      final IntPredicate<?> p = (IntPredicate<?>) other;
      return getOperator().equals(p.getOperator()) && intValue() == p.intValue();
    }
    return false;
  }

  @Override
  public String toString() {
    return getOperator() + ":" + getValue();
  }
}
