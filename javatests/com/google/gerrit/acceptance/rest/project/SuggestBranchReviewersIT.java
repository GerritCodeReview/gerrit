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
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class SuggestBranchReviewersIT extends AbstractDaemonTest {

  private TestAccount user3;

  private TestAccount user(String name, String fullName, String emailName) throws Exception {
    return accountCreator.create(name(name), name(emailName) + "@example.com", fullName, null);
  }

  private TestAccount user(String name, String fullName) throws Exception {
    return user(name, fullName, name);
  }

  @Before
  public void setUp() throws Exception {
    gApi.projects().name(project.get()).branch("otherBranch").create(new BranchInput());
    user3 = user("user3", "First3 Last3");
  }

  private List<SuggestedReviewerInfo> suggestReviewers(String query) throws Exception {
    return gApi.projects().name(project.get()).branch("otherBranch").suggestReviewers(query).get();
  }

  // private List<SuggestedReviewerInfo> suggestReviewers(String query, int n) throws Exception {
  //   return gApi.projects()
  //       .name(project.get())
  //       .branch("refs/heads/visible")
  //       .suggestReviewers(query)
  //       .withLimit(n)
  //       .get();
  // }

  @Test
  public void suggestReviewersWithoutLimitOptionSpecified() throws Exception {
    String query = user3.username();
    List<SuggestedReviewerInfo> suggestedReviewers = suggestReviewers(query);
    assertThat(suggestedReviewers).hasSize(1);
  }
}
