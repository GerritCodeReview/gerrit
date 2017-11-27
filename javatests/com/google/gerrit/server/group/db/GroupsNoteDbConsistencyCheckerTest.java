// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.group.db;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo.warning;

import com.google.common.collect.ImmutableListMultimap;
import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.testing.GerritBaseTests;
import java.util.List;
import org.junit.Test;

public class GroupsNoteDbConsistencyCheckerTest extends GerritBaseTests {

  @Test
  public void duplicateUUIDs() throws Exception {
    ImmutableListMultimap<AccountGroup.UUID, String> byUUID =
        ImmutableListMultimap.of(
            new AccountGroup.UUID("uuid-1"),
            "g-1",
            new AccountGroup.UUID("uuid-2"),
            "g-2",
            new AccountGroup.UUID("uuid-1"),
            "g-3",
            new AccountGroup.UUID("uuid-2"),
            "g-4",
            new AccountGroup.UUID("uuid-1"),
            "g-5");

    ConsistencyProblemInfo exp1 =
        warning("shared group UUID 'uuid-1' between groups: g-1, g-3, g-5");
    ConsistencyProblemInfo exp2 = warning("shared group UUID 'uuid-2' between groups: g-2, g-4");

    List<ConsistencyProblemInfo> problems =
        GroupsNoteDbConsistencyChecker.checkDuplicateUUIDs(byUUID);
    assertThat(problems).containsExactly(exp1, exp2);
  }
}
