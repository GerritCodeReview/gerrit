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

/**
 * Predicate only for use in rewrite rule patterns.
 * <p>
 * May <b>only</b> be used when nested immediately within a
 * {@link VariablePredicate}. Within the QueryRewriter this predicate matches
 * any other operator whose name matches this predicate's operator name.
 *
 * @see QueryRewriter
 */
public final class WildPatternPredicate<T> extends OperatorPredicate<T> {
  public WildPatternPredicate(final String name) {
    super(name, "*");
  }

  @Override
  public boolean match(final T object) {
    throw new UnsupportedOperationException("Cannot match " + toString());
  }

  @Override
  public int getCost() {
    return 0;
  }

  @Override
  public int hashCode() {
    return getOperator().hashCode() * 31;
  }

  @Override
  public boolean equals(final Object other) {
    if (other == null)
      return false;
    if (getClass() == other.getClass()) {
      final WildPatternPredicate<?> p = (WildPatternPredicate<?>) other;
      return getOperator().equals(p.getOperator());
    }
    return false;
  }

  @Override
  public String toString() {
    return getOperator() + ":" + getValue();
  }
}
