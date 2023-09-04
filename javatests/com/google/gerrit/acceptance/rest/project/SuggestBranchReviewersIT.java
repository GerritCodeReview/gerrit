// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class SuggestBranchReviewersIT extends AbstractDaemonTest {

  @Inject private GroupOperations groupOperations;

  private TestAccount user(String name, String fullName, String emailName) throws Exception {
    return accountCreator.create(name(name), name(emailName) + "@example.com", fullName, null);
  }

  private TestAccount user(String name, String fullName) throws Exception {
    return user(name, fullName, name);
  }

  @Before
  public void setUp() throws Exception {
    user1 = user("user1", "First1 Last1");
    user2 = user("user2", "First2 Last2");
    user3 = user("user3", "First3 Last3");
    user4 = user("jdoe", "John Doe", "JDOE");

    group1 =
        groupOperations.newGroup().name(name("users1")).members(user1.id(), user3.id()).create();
    group2 =
        groupOperations.newGroup().name(name("users2")).members(user2.id(), user3.id()).create();
    group3 = groupOperations.newGroup().name(name("users3")).members(user1.id()).create();
  }

  private List<SuggestedReviewerInfo> suggestReviewers(String changeId, String query)
      throws Exception {
    return gApi.changes().id(changeId).suggestReviewers(query).get();
  }

  private List<SuggestedReviewerInfo> suggestReviewers(String changeId, String query, int n)
      throws Exception {
    return gApi.changes().id(changeId).suggestReviewers(query).withLimit(n).get();
  }

  @Test
  public void suggestReviewersWithoutLimitOptionSpecified() throws Exception {
    String query = user3.username();
    List<SuggestedReviewerInfo> suggestedReviewerInfos =
        gApi.projects()
            .name(project.get())
            .branch("refs/heads/visible")
            .create(new BranchInput())
            .suggestReviewers(query)
            .get();
    assertThat(suggestedReviewerInfos).hasSize(1);
  }
}
