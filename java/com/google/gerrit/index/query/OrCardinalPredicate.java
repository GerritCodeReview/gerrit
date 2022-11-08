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
import java.util.Optional;

public class OrCardinalPredicate<T> extends OrPredicate<T> implements HasCardinality {
  private final int cardinality;

  public OrCardinalPredicate(Collection<? extends Predicate<T>> that) {
    super(that);
    Optional<Predicate<T>> nonHasCardinality =
        getChildren().stream().filter(p -> !(p instanceof HasCardinality)).findAny();
    if (nonHasCardinality.isPresent()) {
      throw new IllegalArgumentException("No HasCardinality: " + nonHasCardinality.get());
    }
    int aggregateCardinality = 0;
    for (Predicate<T> p : getChildren()) {
      if (p instanceof HasCardinality) {
        aggregateCardinality += ((HasCardinality) p).getCardinality();
      }
    }
    cardinality = aggregateCardinality;
  }

  @Override
  public Predicate<T> copy(Collection<? extends Predicate<T>> children) {
    return new OrCardinalPredicate<>(children);
  }

  @Override
  public int getCardinality() {
    return cardinality;
  }
}
