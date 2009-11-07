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

import java.util.Collections;
import java.util.List;

/** Negates the result of another predicate. */
public final class NotPredicate extends Predicate {
  private final Predicate that;

  public NotPredicate(final Predicate that) {
    this.that = that;
  }

  @Override
  public Predicate not() {
    return that;
  }

  @Override
  public List<Predicate> getChildren() {
    return Collections.singletonList(that);
  }

  @Override
  public int hashCode() {
    return ~that.hashCode();
  }

  @Override
  public boolean equals(final Object other) {
    return other instanceof NotPredicate
        && getChildren().equals(((Predicate) other).getChildren());
  }

  @Override
  public String toString() {
    return "-" + that.toString();
  }
}
