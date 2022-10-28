// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.index.query;

import java.util.Collection;

public class AndCardinalPredicate<T> extends AndPredicate<T> implements HasCardinality {
  private final int cardinality;

  public AndCardinalPredicate(Collection<? extends Predicate<T>> that) {
    super(that);
    int minCardinality = Integer.MAX_VALUE;
    for (Predicate<T> child : getChildren()) {
      if (child instanceof HasCardinality) {
        minCardinality = Math.min(((HasCardinality) child).getCardinality(), minCardinality);
      }
    }
    cardinality = minCardinality;
  }

  @Override
  public Predicate<T> copy(Collection<? extends Predicate<T>> children) {
    return new AndCardinalPredicate<>(children);
  }

  @Override
  public int getCardinality() {
    return cardinality;
  }
}
