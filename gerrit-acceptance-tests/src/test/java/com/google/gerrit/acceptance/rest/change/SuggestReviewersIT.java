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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.CreateGroupArgs;
import com.google.gerrit.server.account.PerformCreateGroup;
import com.google.gerrit.server.change.SuggestReviewers.SuggestedReviewerInfo;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class SuggestReviewersIT extends AbstractDaemonTest {
  @Inject
  private PerformCreateGroup.Factory createGroupFactory;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private ProjectCache projectCache;

  private AccountGroup group1;
  private TestAccount user1;
  private TestAccount user2;
  private TestAccount user3;

  @Before
  public void setUp() throws Exception {
    group1 = group("users1");
    group("users2");
    group("users3");

    user1 = accounts.create("user1", "user1@example.com", "User1", "users1");
    user2 = accounts.create("user2", "user2@example.com", "User2", "users2");
    user3 = accounts.create("user3", "user3@example.com", "User3",
        "users1", "users2");
  }

  @Test
  @GerritConfig(name = "suggest.accounts", value = "false")
  public void suggestReviewersNoResult1() throws GitAPIException, IOException,
      Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, "u", 6);
    assertEquals(reviewers.size(), 0);
  }

  @Test
  @GerritConfigs(
      {@GerritConfig(name = "suggest.accounts", value = "true"),
       @GerritConfig(name = "suggest.from", value = "1"),
       @GerritConfig(name = "accounts.visibility", value = "NONE")
      })
  public void suggestReviewersNoResult2() throws GitAPIException, IOException,
      Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, "u", 6);
    assertEquals(reviewers.size(), 0);
  }

  @Test
  @GerritConfig(name = "suggest.from", value = "2")
  public void suggestReviewersNoResult3() throws GitAPIException, IOException,
      Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, "u", 6);
    assertEquals(reviewers.size(), 0);
  }

  @Test
  public void suggestReviewersChange() throws GitAPIException,
      IOException, Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, "u", 6);
    assertEquals(reviewers.size(), 6);
    reviewers = suggestReviewers(changeId, "u", 5);
    assertEquals(reviewers.size(), 5);
    reviewers = suggestReviewers(changeId, "users3", 10);
    assertEquals(reviewers.size(), 1);
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void suggestReviewersSameGroupVisibility() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;

    reviewers = suggestReviewers(changeId, "user2", 2);
    assertEquals("User2", Iterables.getOnlyElement(reviewers).account.name);

    reviewers = suggestReviewers(new RestSession(server, user1),
        changeId, "user2", 2);
    assertTrue(reviewers.isEmpty());

    reviewers = suggestReviewers(new RestSession(server, user2),
        changeId, "user2", 2);
    assertEquals("User2", Iterables.getOnlyElement(reviewers).account.name);

    reviewers = suggestReviewers(new RestSession(server, user3),
        changeId, "user2", 2);
    assertEquals("User2", Iterables.getOnlyElement(reviewers).account.name);
  }

  @Test
  @GerritConfig(name = "accounts.visibility", value = "SAME_GROUP")
  public void suggestReviewersViewAllAccounts() throws Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers;

    reviewers = suggestReviewers(new RestSession(server, user1),
        changeId, "user2", 2);
    assertTrue(reviewers.isEmpty());

    grantCapability(GlobalCapability.VIEW_ALL_ACCOUNTS, group1);
    reviewers = suggestReviewers(new RestSession(server, user1),
        changeId, "user2", 2);
    assertEquals("User2", Iterables.getOnlyElement(reviewers).account.name);
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
    args.setGroupName(name);
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
}
