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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import org.junit.Test;

public class AndSourceTest extends PredicateTest {
  @Test
  public void ensureLowerCostPredicateRunsFirst() {
    TestDataSourcePredicate p1 = new TestDataSourcePredicate("predicate1", "foo", 10, 10);
    TestDataSourcePredicate p2 = new TestDataSourcePredicate("predicate2", "foo", 1, 10);
    AndSource<String> andSource = new AndSource<>(Lists.newArrayList(p1, p2), null);
    andSource.match("bar");
    assertFalse(p1.ranMatch);
    assertTrue(p2.ranMatch);
  }
}
