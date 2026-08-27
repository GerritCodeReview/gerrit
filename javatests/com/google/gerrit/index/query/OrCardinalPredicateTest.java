// Copyright (C) 2026 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.Lists;
import org.junit.Test;

public class OrCardinalPredicateTest extends PredicateTest {
  @Test
  public void ensureAllChildrenHaveCardinality() {
    TestDataSourcePredicate p1 = new TestDataSourcePredicate("predicate1", "foo", 1, 10);
    TestMatchablePredicate<String> p2 = new TestMatchablePredicate<>("predicate2", "bar", 1);

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> new OrCardinalPredicate<>(Lists.newArrayList(p1, p2)));
    assertThat(thrown).hasMessageThat().contains("No HasCardinality: " + p2);
  }

  @Test
  public void computesAggregateCardinality() {
    TestDataSourcePredicate p1 = new TestDataSourcePredicate("predicate1", "foo", 1, 10);
    TestDataSourcePredicate p2 = new TestDataSourcePredicate("predicate2", "bar", 1, 5);

    OrCardinalPredicate<String> pred = new OrCardinalPredicate<>(Lists.newArrayList(p1, p2));
    assertThat(pred.getCardinality()).isEqualTo(15);
  }
}
