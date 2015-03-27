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

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GerritConfigs;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.common.SuggestedReviewerInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.CreateGroupArgs;
import com.google.gerrit.server.account.PerformCreateGroup;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SuggestReviewersIT extends AbstractDaemonTest {
  @Inject
  private PerformCreateGroup.Factory createGroupFactory;

  private AccountGroup group1;
  private AccountGroup group2;
  private AccountGroup group3;

  private TestAccount user1;
  private TestAccount user2;
  private TestAccount user3;

  @Before
  public void setUp() throws Exception {
    group1 = group("users1");
    group2 = group("users2");
    group3 = group("users3");

    user1 = user("user1", group1);
    user2 = user("user2", group2);
    user3 = user("user3", group1, group2);
  }

  @Test
  @GerritConfig(name = "suggest.accounts", value = "false")
  public void suggestReviewersNoResult1() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers =
        suggestReviewers(changeId, name("u"), 6);
    assertThat(reviewers).isEmpty();
  }

  @Test
  @GerritConfigs(
      {@GerritConfig(name = "suggest.accounts", value = "true"),
       @GerritConfig(name = "suggest.from", value = "1"),
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

    reviewers = suggestReviewers(changeId, user2.fullName, 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name)
        .isEqualTo(user2.fullName);

    reviewers = suggestReviewers(new RestSession(server, user1),
        changeId, user2.fullName, 2);
    assertThat(reviewers).isEmpty();

    reviewers = suggestReviewers(new RestSession(server, user2),
        changeId, user2.fullName, 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name)
        .isEqualTo(user2.fullName);

    reviewers = suggestReviewers(new RestSession(server, user3),
        changeId, user2.fullName, 2);
    assertThat(reviewers).hasSize(1);
    assertThat(Iterables.getOnlyElement(reviewers).account.name)
        .isEqualTo(user2.fullName);
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void suggestReviewersViewAllAccounts() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;

    reviewers = suggestReviewers(new RestSession(server, user1),
        changeId, user2.fullName, 2);
    assertThat(reviewers).isEmpty();

    grantCapability(GlobalCapability.VIEW_ALL_ACCOUNTS, group1);
    reviewers = suggestReviewers(new RestSession(server, user1),
        changeId, user2.fullName, 2);
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
  @GerritConfig(name = "suggest.fullTextSearch", value = "true")
  public void suggestReviewersFullTextSearch() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers =
        suggestReviewers(changeId, "ser", 5);
    assertThat(reviewers).hasSize(4);
  }

  @Test
  @GerritConfigs(
      {@GerritConfig(name = "suggest.fulltextsearch", value = "true"),
       @GerritConfig(name = "suggest.fullTextSearchMaxMatches", value = "2")
  })
  public void suggestReviewersFullTextSearchLimitMaxMatches() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers =
        suggestReviewers(changeId, "ser", 3);
    assertThat(reviewers).hasSize(2);
  }

  @Test
  public void suggestReviewersWithoutLimitOptionSpecified() throws Exception {
    String changeId = createChange().getChangeId();
    String query = user3.fullName;
    List<SuggestedReviewerInfo> suggestedReviewerInfos = newGson().fromJson(
        adminSession.get("/changes/"
            + changeId
            + "/suggest_reviewers?q="
            + query)
            .getReader(),
        new TypeToken<List<SuggestedReviewerInfo>>() {}
        .getType());
    assertThat(suggestedReviewerInfos).hasSize(1);
  }

  private List<SuggestedReviewerInfo> suggestReviewers(RestSession session,
      String changeId, String query, int n) throws IOException {
    return newGson().fromJson(
        session.get("/changes/"
            + changeId
            + "/suggest_reviewers?q="
            + query
            + "&n="
            + n)
        .getReader(),
        new TypeToken<List<SuggestedReviewerInfo>>() {}
        .getType());
  }

  private List<SuggestedReviewerInfo> suggestReviewers(String changeId,
      String query, int n) throws IOException {
    return suggestReviewers(adminSession, changeId, query, n);
  }

  private AccountGroup group(String name) throws Exception {
    CreateGroupArgs args = new CreateGroupArgs();
    args.setGroupName(name(name));
    args.initialMembers = Collections.singleton(admin.getId());
    return createGroupFactory.create(args).createGroup();
  }

  private void grantCapability(String name, AccountGroup group)
      throws Exception {
    MetaDataUpdate md = metaDataUpdateFactory.create(allProjects);
    ProjectConfig config = ProjectConfig.read(md);
    AccessSection s = config.getAccessSection(
        AccessSection.GLOBAL_CAPABILITIES);
    Permission p = s.getPermission(name, true);
    p.add(new PermissionRule(config.resolve(group)));
    config.commit(md);
    projectCache.evict(config.getProject());
  }

  private TestAccount user(String name, AccountGroup... groups) throws Exception {
    name = name(name);
    String[] groupNames = FluentIterable.from(Arrays.asList(groups))
        .transform(new Function<AccountGroup, String>() {
          @Override
          public String apply(AccountGroup in) {
            return in.getName();
          }
        }).toArray(String.class);
    return accounts.create(name, name + "@example.com", name, groupNames);
  }
}
