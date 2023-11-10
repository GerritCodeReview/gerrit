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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.Lists;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.ConfigSuite;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(ConfigSuite.class)
public class AndSourceTest extends PredicateTest {

  @ConfigSuite.Parameter public Config config;

  @Test
  public void ensureLowerCostPredicateRunsFirst() {
    TestDataSourcePredicate p1 = new TestDataSourcePredicate("predicate1", "foo", 10, 10);
    TestDataSourcePredicate p2 = new TestDataSourcePredicate("predicate2", "foo", 1, 10);
    AndSource<String> andSource =
        new AndSource<>(Lists.newArrayList(p1, p2), IndexConfig.fromConfig(config).build());
    assertFalse(andSource.match("bar"));
    assertFalse(p1.ranMatch);
    assertTrue(p2.ranMatch);
  }

  @Test
  public void ensureAtLeastOneChildIsADataSource() {
    TestMatchablePredicate<ChangeData> p1 = new TestMatchablePredicate<>("predicate1", "foo", 1);
    TestMatchablePredicate<ChangeData> p2 = new TestMatchablePredicate<>("predicate2", "foo", 1);

    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () -> new AndSource<>(Lists.newArrayList(p1, p2), null));
    assertThat(thrown).hasMessageThat().contains("No DataSource Found");
  }
}
