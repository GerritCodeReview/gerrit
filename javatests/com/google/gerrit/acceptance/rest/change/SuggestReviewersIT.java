// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.group.GroupOperations;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;

public class SuggestReviewersIT extends AbstractDaemonTest {
  @Inject private AccountOperations accountOperations;
  @Inject private GroupOperations groupOperations;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  private AccountGroup.UUID group1;
  private AccountGroup.UUID group2;
  private AccountGroup.UUID group3;

  private TestAccount user1;
  private TestAccount user2;
  private TestAccount user3;
  private TestAccount user4;

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

  @Test
  @GerritConfig(name = "accounts.visibility", value = "NONE")
  public void suggestReviewersNoResult1() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, name("u"), 6);
    assertThat(reviewers).isEmpty();
  }

  @Test
  @GerritConfig(name = "suggest.from", value = "1")
  @GerritConfig(name = "accounts.visibility", value = "NONE")
  public void suggestReviewersNoResult2() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, name("u"), 6);
    assertThat(reviewers).isEmpty();
  }

  @Test
  public void suggestReviewersChange() throws Exception {
    String changeId = createChange().getChangeId();
    testSuggestReviewersChange(changeId);
  }

  @Test
  public void suggestReviewersPrivateChange() throws Exception {
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).setPrivate(true, null);
    testSuggestReviewersChange(changeId);
  }

  public void testSuggestReviewersChange(String changeId) throws Exception {
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, name("u"), 6);
    assertReviewers(
        reviewers, ImmutableList.of(user1, user2, user3), ImmutableList.of(group1, group2, group3));

    reviewers = suggestReviewers(changeId, name("u"), 5);
    assertReviewers(
        reviewers, ImmutableList.of(user1, user2, user3), ImmutableList.of(group1, group2));

    String group3Name = groupOperations.group(group3).get().name();
    reviewers = suggestReviewers(changeId, group3Name, 10);
    assertReviewers(reviewers, ImmutableList.of(), ImmutableList.of(group3));

    // Suggested accounts are ordered by activity. All users have no activity,
    // hence we don't know which of the matching accounts we get when the query
    // is limited to 1.
    reviewers = suggestReviewers(changeId, name("u"), 1);
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.get(0).account).isNotNull();
    assertThat(ImmutableList.of(reviewers.get(0).account._accountId))
        .containsAnyIn(
            ImmutableList.of(user1, user2, user3).stream()
                .map(u -> u.id().get())
                .collect(toList()));
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void suggestReviewersSameGroupVisibility() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;

    reviewers = suggestReviewers(changeId, user2.username(), 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name).isEqualTo(user2.fullName());

    requestScopeOperations.setApiUser(user1.id());
    reviewers = suggestReviewers(changeId, user2.fullName(), 2);
    assertThat(reviewers).isEmpty();

    requestScopeOperations.setApiUser(user2.id());
    reviewers = suggestReviewers(changeId, user2.username(), 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name).isEqualTo(user2.fullName());

    requestScopeOperations.setApiUser(user3.id());
    reviewers = suggestReviewers(changeId, user2.username(), 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name).isEqualTo(user2.fullName());
  }

  @Test
  public void suggestReviewersPrivateProjectVisibility() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;

    requestScopeOperations.setApiUser(user3.id());
    block("refs/*", "read", ANONYMOUS_USERS);
    allow("refs/*", "read", group1);
    reviewers = suggestReviewers(changeId, user2.username(), 2);
    assertThat(reviewers).isEmpty();
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void suggestReviewersViewAllAccounts() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;

    requestScopeOperations.setApiUser(user1.id());
    reviewers = suggestReviewers(changeId, user2.username(), 2);
    assertThat(reviewers).isEmpty();

    // Clear cached group info.
    requestScopeOperations.setApiUser(user1.id());
    allowGlobalCapabilities(group1, GlobalCapability.VIEW_ALL_ACCOUNTS);
    reviewers = suggestReviewers(changeId, user2.username(), 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name).isEqualTo(user2.fullName());
  }

  @Test
  @GerritConfig(name = "suggest.maxSuggestedReviewers", value = "2")
  public void suggestReviewersMaxNbrSuggestions() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, name("user"), 5);
    assertThat(reviewers).hasSize(2);
  }

  @Test
  public void suggestReviewersFullTextSearch() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;

    reviewers = suggestReviewers(changeId, "first");
    assertThat(reviewers).hasSize(3);

    reviewers = suggestReviewers(changeId, "first1");
    assertThat(reviewers).hasSize(1);

    reviewers = suggestReviewers(changeId, "last");
    assertThat(reviewers).hasSize(3);

    reviewers = suggestReviewers(changeId, "last1");
    assertThat(reviewers).hasSize(1);

    reviewers = suggestReviewers(changeId, "fi la");
    assertThat(reviewers).hasSize(3);

    reviewers = suggestReviewers(changeId, "la fi");
    assertThat(reviewers).hasSize(3);

    reviewers = suggestReviewers(changeId, "first1 la");
    assertThat(reviewers).hasSize(1);

    reviewers = suggestReviewers(changeId, "fi last1");
    assertThat(reviewers).hasSize(1);

    reviewers = suggestReviewers(changeId, "first1 last2");
    assertThat(reviewers).isEmpty();

    reviewers = suggestReviewers(changeId, name("user"));
    assertThat(reviewers).hasSize(6);

    reviewers = suggestReviewers(changeId, user1.username());
    assertThat(reviewers).hasSize(1);

    reviewers = suggestReviewers(changeId, "example.com");
    assertThat(reviewers).hasSize(5);

    reviewers = suggestReviewers(changeId, user1.email());
    assertThat(reviewers).hasSize(1);

    reviewers = suggestReviewers(changeId, user1.username() + " example");
    assertThat(reviewers).hasSize(1);

    reviewers = suggestReviewers(changeId, user4.email().toLowerCase());
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.get(0).account.email).isEqualTo(user4.email());
  }

  @Test
  public void suggestReviewersWithoutLimitOptionSpecified() throws Exception {
    String changeId = createChange().getChangeId();
    String query = user3.username();
    List<SuggestedReviewerInfo> suggestedReviewerInfos =
        gApi.changes().id(changeId).suggestReviewers(query).get();
    assertThat(suggestedReviewerInfos).hasSize(1);
  }

  @Test
  @GerritConfig(name = "addreviewer.maxAllowed", value = "1")
  @GerritConfig(name = "addreviewer.maxWithoutConfirmation", value = "1")
  public void confirmationIsNeverRequestedForAccounts() throws Exception {
    user("individual 0", "Test0 Last0");
    user("individual 1", "Test1 Last1");

    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;
    SuggestedReviewerInfo reviewer;

    // Individual account suggestions have count of 1 and no confirm.
    reviewers = suggestReviewers(changeId, "test", 10);
    assertThat(reviewers).hasSize(2);
    reviewer = reviewers.get(0);
    assertThat(reviewer.count).isEqualTo(1);
    assertThat(reviewer.confirm).isNull();
  }

  @Test
  @GerritConfig(name = "addreviewer.maxAllowed", value = "2")
  @GerritConfig(name = "addreviewer.maxWithoutConfirmation", value = "1")
  public void suggestReviewersGroupSizeConsiderations() throws Exception {
    AccountGroup.UUID largeGroup = createGroupWithArbitraryMembers(3);
    String largeGroupName = groupOperations.group(largeGroup).get().name();
    AccountGroup.UUID mediumGroup = createGroupWithArbitraryMembers(2);
    String mediumGroupName = groupOperations.group(mediumGroup).get().name();

    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;
    SuggestedReviewerInfo reviewer;

    // Large group should never be suggested.
    reviewers = suggestReviewers(changeId, largeGroupName, 10);
    assertThat(reviewers).isEmpty();

    // Medium group should be suggested with appropriate count and confirm.
    reviewers = suggestReviewers(changeId, mediumGroupName, 10);
    assertThat(reviewers).hasSize(1);
    reviewer = reviewers.get(0);
    assertThat(reviewer.group.id).isEqualTo(mediumGroup.get());
    assertThat(reviewer.count).isEqualTo(2);
    assertThat(reviewer.confirm).isTrue();
  }

  @Test
  @GerritConfig(name = "addreviewer.maxAllowed", value = "20")
  @GerritConfig(name = "addreviewer.maxWithoutConfirmation", value = "0")
  public void confirmationIsNotNecessaryForLargeGroupWhenLimitIsRemoved() throws Exception {
    String changeId = createChange().getChangeId();
    int numMembers = 15;
    AccountGroup.UUID largeGroup = createGroupWithArbitraryMembers(numMembers);
    String groupName = groupOperations.group(largeGroup).get().name();

    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, groupName, 10);

    assertThat(reviewers).hasSize(1);
    SuggestedReviewerInfo reviewer = Iterables.getOnlyElement(reviewers);
    assertThat(reviewer.group.id).isEqualTo(largeGroup.get());
    // Confirmation should not be necessary.
    assertThat(reviewer.confirm).isNull();
  }

  @Test
  @GerritConfig(name = "addreviewer.maxAllowed", value = "0")
  public void largeGroupIsSuggestedWhenLimitIsRemoved() throws Exception {
    String changeId = createChange().getChangeId();
    int numMembers = 30;
    AccountGroup.UUID largeGroup = createGroupWithArbitraryMembers(numMembers);
    String groupName = groupOperations.group(largeGroup).get().name();

    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, groupName, 10);

    assertThat(reviewers).hasSize(1);
    SuggestedReviewerInfo reviewer = Iterables.getOnlyElement(reviewers);
    assertThat(reviewer.group.id).isEqualTo(largeGroup.get());
  }

  @Test
  public void defaultReviewerSuggestion() throws Exception {
    TestAccount user1 = user("customuser1", "User1");
    TestAccount reviewer1 = user("customuser2", "User2");
    TestAccount reviewer2 = user("customuser3", "User3");

    requestScopeOperations.setApiUser(user1.id());
    String changeId1 = createChangeFromApi();

    requestScopeOperations.setApiUser(reviewer1.id());
    reviewChange(changeId1);

    requestScopeOperations.setApiUser(user1.id());
    String changeId2 = createChangeFromApi();

    requestScopeOperations.setApiUser(reviewer1.id());
    reviewChange(changeId2);

    requestScopeOperations.setApiUser(reviewer2.id());
    reviewChange(changeId2);

    requestScopeOperations.setApiUser(user1.id());
    String changeId3 = createChangeFromApi();
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId3, null, 4);
    assertThat(reviewers.stream().map(r -> r.account._accountId).collect(toList()))
        .containsExactly(reviewer1.id().get(), reviewer2.id().get())
        .inOrder();

    // check that existing reviewers are filtered out
    gApi.changes().id(changeId3).addReviewer(reviewer1.email());
    reviewers = suggestReviewers(changeId3, null, 4);
    assertThat(reviewers.stream().map(r -> r.account._accountId).collect(toList()))
        .containsExactly(reviewer2.id().get())
        .inOrder();
  }

  @Test
  public void defaultReviewerSuggestionOnFirstChange() throws Exception {
    TestAccount user1 = user("customuser1", "User1");
    requestScopeOperations.setApiUser(user1.id());
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(createChange().getChangeId(), "", 4);
    assertThat(reviewers).isEmpty();
  }

  @Test
  @GerritConfig(name = "suggest.maxSuggestedReviewers", value = "10")
  public void reviewerRanking() throws Exception {
    // Assert that user are ranked by the number of times they have applied a
    // a label to a change (highest), added comments (medium) or owned a
    // change (low).
    String fullName = "Primum Finalis";
    TestAccount userWhoOwns = user("customuser1", fullName);
    TestAccount reviewer1 = user("customuser2", fullName);
    TestAccount reviewer2 = user("customuser3", fullName);
    TestAccount userWhoComments = user("customuser4", fullName);
    TestAccount userWhoLooksForSuggestions = user("customuser5", fullName);

    // Create a change as userWhoOwns and add some reviews
    requestScopeOperations.setApiUser(userWhoOwns.id());
    String changeId1 = createChangeFromApi();

    requestScopeOperations.setApiUser(reviewer1.id());
    reviewChange(changeId1);

    requestScopeOperations.setApiUser(user1.id());
    String changeId2 = createChangeFromApi();

    requestScopeOperations.setApiUser(reviewer1.id());
    reviewChange(changeId2);

    requestScopeOperations.setApiUser(reviewer2.id());
    reviewChange(changeId2);

    // Create a comment as a different user
    requestScopeOperations.setApiUser(userWhoComments.id());
    ReviewInput ri = new ReviewInput();
    ri.message = "Test";
    gApi.changes().id(changeId1).revision(1).review(ri);

    // Create a change as a new user to assert that we receive the correct
    // ranking

    requestScopeOperations.setApiUser(userWhoLooksForSuggestions.id());
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(createChangeFromApi(), "Pri", 4);
    assertThat(reviewers.stream().map(r -> r.account._accountId).collect(toList()))
        .containsExactly(
            reviewer1.id().get(),
            reviewer2.id().get(),
            userWhoOwns.id().get(),
            userWhoComments.id().get())
        .inOrder();
  }

  @Test
  public void reviewerRankingProjectIsolation() throws Exception {
    // Create new project
    Project.NameKey newProject = projectOperations.newProject().create();

    // Create users who review changes in both the default and the new project
    String fullName = "Primum Finalis";
    TestAccount userWhoOwns = user("customuser1", fullName);
    TestAccount reviewer1 = user("customuser2", fullName);
    TestAccount reviewer2 = user("customuser3", fullName);

    requestScopeOperations.setApiUser(userWhoOwns.id());
    String changeId1 = createChangeFromApi();

    requestScopeOperations.setApiUser(reviewer1.id());
    reviewChange(changeId1);

    requestScopeOperations.setApiUser(userWhoOwns.id());
    String changeId2 = createChangeFromApi(newProject);

    requestScopeOperations.setApiUser(reviewer2.id());
    reviewChange(changeId2);

    requestScopeOperations.setApiUser(userWhoOwns.id());
    String changeId3 = createChangeFromApi(newProject);

    requestScopeOperations.setApiUser(reviewer2.id());
    reviewChange(changeId3);

    requestScopeOperations.setApiUser(userWhoOwns.id());
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(createChangeFromApi(), "Prim", 4);

    // Assert that reviewer1 is on top, even though reviewer2 has more reviews
    // in other projects
    assertThat(reviewers.stream().map(r -> r.account._accountId).collect(toList()))
        .containsExactly(reviewer1.id().get(), reviewer2.id().get())
        .inOrder();
  }

  @Test
  public void suggestNoInactiveAccounts() throws Exception {
    String name = name("foo");
    TestAccount foo1 = accountCreator.create(name + "-1");
    assertThat(gApi.accounts().id(foo1.username()).getActive()).isTrue();

    TestAccount foo2 = accountCreator.create(name + "-2");
    assertThat(gApi.accounts().id(foo2.username()).getActive()).isTrue();

    String changeId = createChange().getChangeId();
    assertReviewers(
        suggestReviewers(changeId, name), ImmutableList.of(foo1, foo2), ImmutableList.of());

    gApi.accounts().id(foo2.username()).setActive(false);
    assertThat(gApi.accounts().id(foo2.id().get()).getActive()).isFalse();
    assertReviewers(suggestReviewers(changeId, name), ImmutableList.of(foo1), ImmutableList.of());
  }

  @Test
  public void suggestBySecondaryEmailWithModifyAccount() throws Exception {
    String secondaryEmail = "foo.secondary@example.com";
    TestAccount foo = createAccountWithSecondaryEmail("foo", secondaryEmail);

    List<SuggestedReviewerInfo> reviewers =
        suggestReviewers(createChange().getChangeId(), secondaryEmail, 4);
    assertReviewers(reviewers, ImmutableList.of(foo), ImmutableList.of());

    reviewers = suggestReviewers(createChange().getChangeId(), "secondary", 4);
    assertReviewers(reviewers, ImmutableList.of(foo), ImmutableList.of());
  }

  @Test
  public void cannotSuggestBySecondaryEmailWithoutModifyAccount() throws Exception {
    String secondaryEmail = "foo.secondary@example.com";
    createAccountWithSecondaryEmail("foo", secondaryEmail);

    requestScopeOperations.setApiUser(user.id());
    List<SuggestedReviewerInfo> reviewers =
        suggestReviewers(createChange().getChangeId(), secondaryEmail, 4);
    assertThat(reviewers).isEmpty();

    reviewers = suggestReviewers(createChange().getChangeId(), "secondary2", 4);
    assertThat(reviewers).isEmpty();
  }

  @Test
  public void secondaryEmailsInSuggestions() throws Exception {
    String secondaryEmail = "foo.secondary@example.com";
    TestAccount foo = createAccountWithSecondaryEmail("foo", secondaryEmail);

    List<SuggestedReviewerInfo> reviewers =
        suggestReviewers(createChange().getChangeId(), "foo", 4);
    assertReviewers(reviewers, ImmutableList.of(foo), ImmutableList.of());
    assertThat(Iterables.getOnlyElement(reviewers).account.secondaryEmails)
        .containsExactly(secondaryEmail);

    requestScopeOperations.setApiUser(user.id());
    reviewers = suggestReviewers(createChange().getChangeId(), "foo", 4);
    assertReviewers(reviewers, ImmutableList.of(foo), ImmutableList.of());
    assertThat(Iterables.getOnlyElement(reviewers).account.secondaryEmails).isNull();
  }

  private TestAccount createAccountWithSecondaryEmail(String name, String secondaryEmail)
      throws Exception {
    TestAccount foo = accountCreator.create(name(name), "foo.primary@example.com", "Foo");
    EmailInput input = new EmailInput();
    input.email = secondaryEmail;
    input.noConfirmation = true;
    gApi.accounts().id(foo.id().get()).addEmail(input);
    return foo;
  }

  private List<SuggestedReviewerInfo> suggestReviewers(String changeId, String query)
      throws Exception {
    return gApi.changes().id(changeId).suggestReviewers(query).get();
  }

  private List<SuggestedReviewerInfo> suggestReviewers(String changeId, String query, int n)
      throws Exception {
    return gApi.changes().id(changeId).suggestReviewers(query).withLimit(n).get();
  }

  private AccountGroup.UUID createGroupWithArbitraryMembers(int numMembers) {
    Set<Account.Id> members =
        IntStream.rangeClosed(1, numMembers)
            .mapToObj(i -> accountOperations.newAccount().create())
            .collect(toImmutableSet());
    return groupOperations.newGroup().members(members).create();
  }

  private TestAccount user(String name, String fullName, String emailName) throws Exception {
    return accountCreator.create(name(name), name(emailName) + "@example.com", fullName);
  }

  private TestAccount user(String name, String fullName) throws Exception {
    return user(name, fullName, name);
  }

  private void reviewChange(String changeId) throws RestApiException {
    ReviewInput ri = new ReviewInput();
    ri.label("Code-Review", 1);
    gApi.changes().id(changeId).current().review(ri);
  }

  private String createChangeFromApi() throws RestApiException {
    return createChangeFromApi(project);
  }

  private String createChangeFromApi(Project.NameKey project) throws RestApiException {
    ChangeInput ci = new ChangeInput();
    ci.project = project.get();
    ci.subject = "Test change at" + System.nanoTime();
    ci.branch = "master";
    return gApi.changes().create(ci).get().changeId;
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
}
