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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GerritConfigs;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupDescriptions;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.group.CreateGroup;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.inject.Inject;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SuggestReviewersIT extends AbstractDaemonTest {
  @Inject
  private CreateGroup.Factory createGroupFactory;

  @Inject
  private GroupsCollection groups;

  private AccountGroup group1;
  private AccountGroup group2;
  private AccountGroup group3;

  private TestAccount user1;
  private TestAccount user2;
  private TestAccount user3;
  private TestAccount user4;

  @Before
  public void setUp() throws Exception {
    group1 = group("users1");
    group2 = group("users2");
    group3 = group("users3");

    user1 = user("user1", "First1 Last1", group1);
    user2 = user("user2", "First2 Last2", group2);
    user3 = user("user3", "First3 Last3", group1, group2);
    user4 = user("jdoe", "John Doe", "JDOE");
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "NONE")
  public void suggestReviewersNoResult1() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers =
        suggestReviewers(changeId, name("u"), 6);
    assertThat(reviewers).isEmpty();
  }

  @Test
  @GerritConfigs(
      {@GerritConfig(name = "suggest.from", value = "1"),
       @GerritConfig(name = "accounts.visibility", value = "NONE")
      })
  public void suggestReviewersNoResult2() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers =
        suggestReviewers(changeId, name("u"), 6);
    assertThat(reviewers).isEmpty();
  }

  @Test
  @GerritConfig(name = "suggest.from", value = "2")
  public void suggestReviewersNoResult3() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers =
        suggestReviewers(changeId, name("").substring(0, 1), 6);
    assertThat(reviewers).isEmpty();
  }

  @Test
  public void suggestReviewersChange() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers =
        suggestReviewers(changeId, name("u"), 6);
    assertThat(reviewers).hasSize(6);
    reviewers = suggestReviewers(changeId, name("u"), 5);
    assertThat(reviewers).hasSize(5);
    reviewers = suggestReviewers(changeId, group3.getName(), 10);
    assertThat(reviewers).hasSize(1);
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void suggestReviewersSameGroupVisibility() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;

    reviewers = suggestReviewers(changeId, user2.username, 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name)
        .isEqualTo(user2.fullName);

    setApiUser(user1);
    reviewers = suggestReviewers(changeId, user2.fullName, 2);
    assertThat(reviewers).isEmpty();

    setApiUser(user2);
    reviewers = suggestReviewers(changeId, user2.username, 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name)
        .isEqualTo(user2.fullName);

    setApiUser(user3);
    reviewers = suggestReviewers(changeId, user2.username, 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name)
        .isEqualTo(user2.fullName);
  }

  @Test
  public void suggestReviewsPrivateProjectVisibility() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;

    setApiUser(user3);
    block("read", ANONYMOUS_USERS, "refs/*");
    allow("read", group1.getGroupUUID(), "refs/*");
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
    allowGlobalCapabilities(group1.getGroupUUID(),
        GlobalCapability.VIEW_ALL_ACCOUNTS);
    reviewers = suggestReviewers(changeId, user2.username, 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name)
        .isEqualTo(user2.fullName);
  }

  @Test
  @GerritConfig(name = "suggest.maxSuggestedReviewers", value = "2")
  public void suggestReviewersMaxNbrSuggestions() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers =
        suggestReviewers(changeId, name("user"), 5);
    assertThat(reviewers).hasSize(2);
  }

  @Test
  public void suggestReviewersFullTextSearch() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;

    reviewers = suggestReviewers(changeId, "first", 4);
    assertThat(reviewers).hasSize(4);

    reviewers = suggestReviewers(changeId, "first1", 2);
    assertThat(reviewers).hasSize(2);

    reviewers = suggestReviewers(changeId, "last", 4);
    assertThat(reviewers).hasSize(4);

    reviewers = suggestReviewers(changeId, "last1", 2);
    assertThat(reviewers).hasSize(2);

    reviewers = suggestReviewers(changeId, "fi la", 4);
    assertThat(reviewers).hasSize(4);

    reviewers = suggestReviewers(changeId, "la fi", 4);
    assertThat(reviewers).hasSize(4);

    reviewers = suggestReviewers(changeId, "first1 la", 2);
    assertThat(reviewers).hasSize(2);

    reviewers = suggestReviewers(changeId, "fi last1", 2);
    assertThat(reviewers).hasSize(2);

    reviewers = suggestReviewers(changeId, "first1 last2", 1);
    assertThat(reviewers).hasSize(0);

    reviewers = suggestReviewers(changeId, name("user"), 7);
    assertThat(reviewers).hasSize(6);

    reviewers = suggestReviewers(changeId, user1.username, 2);
    assertThat(reviewers).hasSize(1);

    reviewers = suggestReviewers(changeId, "example.com", 7);
    assertThat(reviewers).hasSize(7);

    reviewers = suggestReviewers(changeId, user1.email, 2);
    assertThat(reviewers).hasSize(1);

    reviewers = suggestReviewers(changeId, user1.username + " example", 2);
    assertThat(reviewers).hasSize(1);

    reviewers = suggestReviewers(changeId, user4.email.toLowerCase(), 2);
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.get(0).account.email).isEqualTo(user4.email);
  }

  @Test
  public void suggestReviewersWithoutLimitOptionSpecified() throws Exception {
    String changeId = createChange().getChangeId();
    String query = user3.username;
    List<SuggestedReviewerInfo> suggestedReviewerInfos = gApi.changes()
        .id(changeId)
        .suggestReviewers(query)
        .get();
    assertThat(suggestedReviewerInfos).hasSize(1);
  }

  @Test
  @GerritConfigs({
    @GerritConfig(name = "addreviewer.maxAllowed", value="2"),
    @GerritConfig(name = "addreviewer.maxWithoutConfirmation", value="1"),
  })
  public void suggestReviewersGroupSizeConsiderations() throws Exception {
    AccountGroup largeGroup = group("large");
    AccountGroup mediumGroup = group("medium");

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

  private List<SuggestedReviewerInfo> suggestReviewers(String changeId,
      String query, int n) throws Exception {
    return gApi.changes()
        .id(changeId)
        .suggestReviewers(query)
        .withLimit(n)
        .get();
  }

  private AccountGroup group(String name) throws Exception {
    GroupInfo group = createGroupFactory.create(name(name))
        .apply(TopLevelResource.INSTANCE, null);
    GroupDescription.Basic d = groups.parseInternal(Url.decode(group.id));
    return GroupDescriptions.toAccountGroup(d);
  }

  private TestAccount user(String name, String fullName, String emailName,
      AccountGroup... groups) throws Exception {
    String[] groupNames = FluentIterable.from(Arrays.asList(groups))
        .transform(new Function<AccountGroup, String>() {
          @Override
          public String apply(AccountGroup in) {
            return in.getName();
          }
        }).toArray(String.class);
    return accounts.create(name(name), name(emailName) + "@example.com",
        fullName, groupNames);
  }

  private TestAccount user(String name, String fullName, AccountGroup... groups)
      throws Exception {
    return user(name, fullName, name, groups);
  }
}
