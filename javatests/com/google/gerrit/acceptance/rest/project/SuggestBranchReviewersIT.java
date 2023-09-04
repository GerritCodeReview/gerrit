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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.extensions.api.changes.ReviewerInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class SuggestBranchReviewersIT extends AbstractDaemonTest {

  @Inject private RequestScopeOperations requestScopeOperations;

  @Inject private ProjectOperations projectOperations;

  @Inject private GroupOperations groupOperations;

  private TestAccount user1;

  private TestAccount user2;
  private TestAccount user3;

  private AccountGroup.UUID group1;

  private TestAccount user(String name, String fullName, String emailName) throws Exception {
    return accountCreator.create(name(name), name(emailName) + "@example.com", fullName, null);
  }

  private TestAccount user(String name, String fullName) throws Exception {
    return user(name, fullName, name);
  }

  @Before
  public void setUp() throws Exception {
    gApi.projects().name(project.get()).branch("otherBranch").create(new BranchInput());
    user1 = user("user1", "First1 Last1");
    user2 = user("user2", "First2 Last2");
    user3 = user("user3", "First3 Last3");
    group1 =
        groupOperations.newGroup().name(name("users1")).members(user1.id(), user3.id()).create();
  }

  private static void assertReviewers(
      List<SuggestedReviewerInfo> actual,
      List<TestAccount> expectedUsers,
      List<AccountGroup.UUID> expectedGroups) {
    List<Integer> actualAccountIds =
        actual.stream()
            .filter(i -> i.account != null)
            .map(i -> i.account._accountId)
            .collect(toList());
    assertThat(actualAccountIds)
        .containsExactlyElementsIn(expectedUsers.stream().map(u -> u.id().get()).collect(toList()));

    List<String> actualGroupIds =
        actual.stream().filter(i -> i.group != null).map(i -> i.group.id).collect(toList());
    assertThat(actualGroupIds)
        .containsExactlyElementsIn(
            expectedGroups.stream().map(AccountGroup.UUID::get).collect(toList()))
        .inOrder();
  }

  private List<SuggestedReviewerInfo> suggestReviewers(String query) throws Exception {
    return gApi.projects().name(project.get()).branch("otherBranch").suggestReviewers(query).get();
  }

  private List<SuggestedReviewerInfo> suggestReviewers(String query, int n) throws Exception {
    return gApi.projects()
        .name(project.get())
        .branch("otherBranch")
        .suggestReviewers(query)
        .withLimit(n)
        .get();
  }

  private List<SuggestedReviewerInfo> suggestCcs(String query) throws Exception {
    return gApi.projects().name(project.get()).branch("otherBranch").suggestCcs(query).get();
  }

  @Test
  public void suggestReviewerAsCc() throws Exception {
    requestScopeOperations.setApiUser(user.id());

    String name = name("foo");
    TestAccount foo1 = accountCreator.create(name + "-1");

    TestAccount foo2 = accountCreator.create(name + "-2");

    assertReviewers(suggestCcs(name), ImmutableList.of(foo1, foo2), ImmutableList.of());

    ReviewerInput reviewerInput = new ReviewerInput();
    reviewerInput.reviewer = foo2.id().toString();
    reviewerInput.state = ReviewerState.REVIEWER;
    assertReviewers(suggestCcs(name), ImmutableList.of(foo1, foo2), ImmutableList.of());
  }

  @Test
  public void suggestReviewersWithoutLimitOptionSpecified() throws Exception {
    String query = user3.username();
    List<SuggestedReviewerInfo> suggestedReviewers = suggestReviewers(query);
    assertThat(suggestedReviewers).hasSize(1);
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void suggestReviewersViewAllAccounts() throws Exception {
    List<SuggestedReviewerInfo> reviewers;

    requestScopeOperations.setApiUser(user1.id());
    reviewers = suggestReviewers(user2.username(), 2);
    assertThat(reviewers).isEmpty();

    // Clear cached group info.
    requestScopeOperations.setApiUser(user1.id());
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.VIEW_ALL_ACCOUNTS).group(group1))
        .update();
    reviewers = suggestReviewers(user2.username(), 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name).isEqualTo(user2.fullName());
  }

  @Test
  public void suggestNoInactiveAccounts() throws Exception {
    requestScopeOperations.setApiUser(user.id());

    String name = name("foo");
    TestAccount foo1 = accountCreator.create(name + "-1");
    assertThat(gApi.accounts().id(foo1.username()).getActive()).isTrue();

    TestAccount foo2 = accountCreator.create(name + "-2");
    assertThat(gApi.accounts().id(foo2.username()).getActive()).isTrue();

    assertReviewers(suggestReviewers(name), ImmutableList.of(foo1, foo2), ImmutableList.of());

    requestScopeOperations.setApiUser(user.id());
    gApi.accounts().id(foo2.username()).setActive(false);
    assertThat(gApi.accounts().id(foo2.id().get()).getActive()).isFalse();
    assertReviewers(suggestReviewers(name), ImmutableList.of(foo1), ImmutableList.of());
  }
}
