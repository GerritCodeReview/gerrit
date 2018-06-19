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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.api.accounts.EmailInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.restapi.group.CreateGroup;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class SuggestReviewersIT extends AbstractDaemonTest {
  @Inject private CreateGroup createGroup;

  private InternalGroup group1;
  private InternalGroup group2;
  private InternalGroup group3;

  private TestAccount user1;
  private TestAccount user2;
  private TestAccount user3;
  private TestAccount user4;

  @Before
  public void setUp() throws Exception {
    group1 = newGroup("users1");
    group2 = newGroup("users2");
    group3 = newGroup("users3");

    user1 = user("user1", "First1 Last1", group1);
    user2 = user("user2", "First2 Last2", group2);
    user3 = user("user3", "First3 Last3", group1, group2);
    user4 = user("jdoe", "John Doe", "JDOE");
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

    reviewers = suggestReviewers(changeId, group3.getName(), 10);
    assertReviewers(reviewers, ImmutableList.of(), ImmutableList.of(group3));

    // Suggested accounts are ordered by activity. All users have no activity,
    // hence we don't know which of the matching accounts we get when the query
    // is limited to 1.
    reviewers = suggestReviewers(changeId, name("u"), 1);
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.get(0).account).isNotNull();
    assertThat(ImmutableList.of(reviewers.get(0).account._accountId))
        .containsAnyIn(
            ImmutableList.of(user1, user2, user3).stream().map(u -> u.id.get()).collect(toList()));
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void suggestReviewersSameGroupVisibility() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;

    reviewers = suggestReviewers(changeId, user2.username, 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name).isEqualTo(user2.fullName);

    setApiUser(user1);
    reviewers = suggestReviewers(changeId, user2.fullName, 2);
    assertThat(reviewers).isEmpty();

    setApiUser(user2);
    reviewers = suggestReviewers(changeId, user2.username, 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name).isEqualTo(user2.fullName);

    setApiUser(user3);
    reviewers = suggestReviewers(changeId, user2.username, 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name).isEqualTo(user2.fullName);
  }

  @Test
  public void suggestReviewersPrivateProjectVisibility() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;

    setApiUser(user3);
    block("refs/*", "read", ANONYMOUS_USERS);
    allow("refs/*", "read", group1.getGroupUUID());
    reviewers = suggestReviewers(changeId, user2.username, 2);
    assertThat(reviewers).isEmpty();
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void suggestReviewersViewAllAccounts() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;

    setApiUser(user1);
    reviewers = suggestReviewers(changeId, user2.username, 2);
    assertThat(reviewers).isEmpty();

    setApiUser(user1); // Clear cached group info.
    allowGlobalCapabilities(group1.getGroupUUID(), GlobalCapability.VIEW_ALL_ACCOUNTS);
    reviewers = suggestReviewers(changeId, user2.username, 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name).isEqualTo(user2.fullName);
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

    reviewers = suggestReviewers(changeId, user1.username);
    assertThat(reviewers).hasSize(1);

    reviewers = suggestReviewers(changeId, "example.com");
    assertThat(reviewers).hasSize(5);

    reviewers = suggestReviewers(changeId, user1.email);
    assertThat(reviewers).hasSize(1);

    reviewers = suggestReviewers(changeId, user1.username + " example");
    assertThat(reviewers).hasSize(1);

    reviewers = suggestReviewers(changeId, user4.email.toLowerCase());
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.get(0).account.email).isEqualTo(user4.email);
  }

  @Test
  public void suggestReviewersWithoutLimitOptionSpecified() throws Exception {
    String changeId = createChange().getChangeId();
    String query = user3.username;
    List<SuggestedReviewerInfo> suggestedReviewerInfos =
        gApi.changes().id(changeId).suggestReviewers(query).get();
    assertThat(suggestedReviewerInfos).hasSize(1);
  }

  @Test
  @GerritConfig(name = "addreviewer.maxAllowed", value = "2")
  @GerritConfig(name = "addreviewer.maxWithoutConfirmation", value = "1")
  public void suggestReviewersGroupSizeConsiderations() throws Exception {
    InternalGroup largeGroup = newGroup("large");
    InternalGroup mediumGroup = newGroup("medium");

    // Both groups have Administrator as a member. Add two users to large
    // group to push it past maxAllowed, and one to medium group to push it
    // past maxWithoutConfirmation.
    user("individual 0", "Test0 Last0", largeGroup, mediumGroup);
    user("individual 1", "Test1 Last1", largeGroup);

    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;
    SuggestedReviewerInfo reviewer;

    // Individual account suggestions have count of 1 and no confirm.
    reviewers = suggestReviewers(changeId, "test", 10);
    assertThat(reviewers).hasSize(2);
    reviewer = reviewers.get(0);
    assertThat(reviewer.count).isEqualTo(1);
    assertThat(reviewer.confirm).isNull();

    // Large group should never be suggested.
    reviewers = suggestReviewers(changeId, largeGroup.getName(), 10);
    assertThat(reviewers).isEmpty();

    // Medium group should be suggested with appropriate count and confirm.
    reviewers = suggestReviewers(changeId, mediumGroup.getName(), 10);
    assertThat(reviewers).hasSize(1);
    reviewer = reviewers.get(0);
    assertThat(reviewer.group.name).isEqualTo(mediumGroup.getName());
    assertThat(reviewer.count).isEqualTo(2);
    assertThat(reviewer.confirm).isTrue();
  }

  @Test
  public void defaultReviewerSuggestion() throws Exception {
    TestAccount user1 = user("customuser1", "User1");
    TestAccount reviewer1 = user("customuser2", "User2");
    TestAccount reviewer2 = user("customuser3", "User3");

    setApiUser(user1);
    String changeId1 = createChangeFromApi();

    setApiUser(reviewer1);
    reviewChange(changeId1);

    setApiUser(user1);
    String changeId2 = createChangeFromApi();

    setApiUser(reviewer1);
    reviewChange(changeId2);

    setApiUser(reviewer2);
    reviewChange(changeId2);

    setApiUser(user1);
    String changeId3 = createChangeFromApi();
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId3, null, 4);
    assertThat(reviewers.stream().map(r -> r.account._accountId).collect(toList()))
        .containsExactly(reviewer1.id.get(), reviewer2.id.get())
        .inOrder();

    // check that existing reviewers are filtered out
    gApi.changes().id(changeId3).addReviewer(reviewer1.email);
    reviewers = suggestReviewers(changeId3, null, 4);
    assertThat(reviewers.stream().map(r -> r.account._accountId).collect(toList()))
        .containsExactly(reviewer2.id.get())
        .inOrder();
  }

  @Test
  public void defaultReviewerSuggestionOnFirstChange() throws Exception {
    TestAccount user1 = user("customuser1", "User1");
    setApiUser(user1);
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
    setApiUser(userWhoOwns);
    String changeId1 = createChangeFromApi();

    setApiUser(reviewer1);
    reviewChange(changeId1);

    setApiUser(user1);
    String changeId2 = createChangeFromApi();

    setApiUser(reviewer1);
    reviewChange(changeId2);

    setApiUser(reviewer2);
    reviewChange(changeId2);

    // Create a comment as a different user
    setApiUser(userWhoComments);
    ReviewInput ri = new ReviewInput();
    ri.message = "Test";
    gApi.changes().id(changeId1).revision(1).review(ri);

    // Create a change as a new user to assert that we receive the correct
    // ranking

    setApiUser(userWhoLooksForSuggestions);
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(createChangeFromApi(), "Pri", 4);
    assertThat(reviewers.stream().map(r -> r.account._accountId).collect(toList()))
        .containsExactly(
            reviewer1.id.get(), reviewer2.id.get(), userWhoOwns.id.get(), userWhoComments.id.get())
        .inOrder();
  }

  @Test
  public void reviewerRankingProjectIsolation() throws Exception {
    // Create new project
    Project.NameKey newProject = createProject("test");

    // Create users who review changes in both the default and the new project
    String fullName = "Primum Finalis";
    TestAccount userWhoOwns = user("customuser1", fullName);
    TestAccount reviewer1 = user("customuser2", fullName);
    TestAccount reviewer2 = user("customuser3", fullName);

    setApiUser(userWhoOwns);
    String changeId1 = createChangeFromApi();

    setApiUser(reviewer1);
    reviewChange(changeId1);

    setApiUser(userWhoOwns);
    String changeId2 = createChangeFromApi(newProject);

    setApiUser(reviewer2);
    reviewChange(changeId2);

    setApiUser(userWhoOwns);
    String changeId3 = createChangeFromApi(newProject);

    setApiUser(reviewer2);
    reviewChange(changeId3);

    setApiUser(userWhoOwns);
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(createChangeFromApi(), "Prim", 4);

    // Assert that reviewer1 is on top, even though reviewer2 has more reviews
    // in other projects
    assertThat(reviewers.stream().map(r -> r.account._accountId).collect(toList()))
        .containsExactly(reviewer1.id.get(), reviewer2.id.get())
        .inOrder();
  }

  @Test
  public void suggestNoInactiveAccounts() throws Exception {
    String name = name("foo");
    TestAccount foo1 = accountCreator.create(name + "-1");
    assertThat(gApi.accounts().id(foo1.username).getActive()).isTrue();

    TestAccount foo2 = accountCreator.create(name + "-2");
    assertThat(gApi.accounts().id(foo2.username).getActive()).isTrue();

    String changeId = createChange().getChangeId();
    assertReviewers(
        suggestReviewers(changeId, name), ImmutableList.of(foo1, foo2), ImmutableList.of());

    gApi.accounts().id(foo2.username).setActive(false);
    assertThat(gApi.accounts().id(foo2.username).getActive()).isFalse();
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

    setApiUser(user);
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

    setApiUser(user);
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
    gApi.accounts().id(foo.id.get()).addEmail(input);
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

  private InternalGroup newGroup(String name) throws Exception {
    GroupInfo group =
        createGroup.apply(TopLevelResource.INSTANCE, IdString.fromDecoded(name(name)), null);
    return group(new AccountGroup.UUID(group.id));
  }

  private TestAccount user(String name, String fullName, String emailName, InternalGroup... groups)
      throws Exception {
    String[] groupNames = Arrays.stream(groups).map(InternalGroup::getName).toArray(String[]::new);
    return accountCreator.create(
        name(name), name(emailName) + "@example.com", fullName, groupNames);
  }

  private TestAccount user(String name, String fullName, InternalGroup... groups) throws Exception {
    return user(name, fullName, name, groups);
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

  private void assertReviewers(
      List<SuggestedReviewerInfo> actual,
      List<TestAccount> expectedUsers,
      List<InternalGroup> expectedGroups) {
    List<Integer> actualAccountIds =
        actual
            .stream()
            .filter(i -> i.account != null)
            .map(i -> i.account._accountId)
            .collect(toList());
    assertThat(actualAccountIds)
        .containsExactlyElementsIn(expectedUsers.stream().map(u -> u.id.get()).collect(toList()));

    List<String> actualGroupIds =
        actual.stream().filter(i -> i.group != null).map(i -> i.group.id).collect(toList());
    assertThat(actualGroupIds)
        .containsExactlyElementsIn(
            expectedGroups.stream().map(g -> g.getGroupUUID().get()).collect(toList()))
        .inOrder();
  }
}
