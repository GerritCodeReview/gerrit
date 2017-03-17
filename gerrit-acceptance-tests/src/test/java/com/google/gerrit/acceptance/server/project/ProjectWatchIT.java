// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.StarsInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.WatchConfig;
import com.google.gerrit.server.account.WatchConfig.ProjectWatchKey;
import com.google.gerrit.server.git.NotifyConfig;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.testutil.FakeEmailSender.Message;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

@NoHttpd
public class ProjectWatchIT extends AbstractDaemonTest {
  @Inject private WatchConfig.Accessor watchConfig;

  @Test
  public void newPatchSetsNotifyConfig() throws Exception {
    Address addr = new Address("Watcher", "watcher@example.com");
    NotifyConfig nc = new NotifyConfig();
    nc.addEmail(addr);
    nc.setName("new-patch-set");
    nc.setHeader(NotifyConfig.Header.CC);
    nc.setTypes(EnumSet.of(NotifyType.NEW_PATCHSETS));
    nc.setFilter("message:sekret");

    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.putNotifyConfig("watch", nc);
    saveProjectConfig(project, cfg);

    PushOneCommit.Result r = pushFactory.create(db, admin.getIdent(), testRepo,
          "original subject", "a", "a1")
        .to("refs/for/master");
    r.assertOkStatus();

    r = pushFactory.create(db, admin.getIdent(), testRepo,
          "super sekret subject", "a", "a2", r.getChangeId())
        .to("refs/for/master");
    r.assertOkStatus();

    r = pushFactory.create(db, admin.getIdent(), testRepo,
          "back to original subject", "a", "a3")
        .to("refs/for/master");
    r.assertOkStatus();

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(addr);
    assertThat(m.body()).contains("Change subject: super sekret subject\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 2\n");
  }

  @Test
  public void watchProject() throws Exception {
    // watch project
    String watchedProject = createProject("watchedProject").get();
    setApiUser(user);
    watch(watchedProject, null);

    // push a change to watched project -> should trigger email notification
    setApiUser(admin);
    TestRepository<InMemoryRepository> watchedRepo =
        cloneProject(new Project.NameKey(watchedProject), admin);
    PushOneCommit.Result r = pushFactory
        .create(db, admin.getIdent(), watchedRepo, "TRIGGER", "a", "a1")
        .to("refs/for/master");
    r.assertOkStatus();

    // push a change to non-watched project -> should not trigger email
    // notification
    String notWatchedProject = createProject("otherProject").get();
    TestRepository<InMemoryRepository> notWatchedRepo =
        cloneProject(new Project.NameKey(notWatchedProject), admin);
    r = pushFactory.create(db, admin.getIdent(), notWatchedRepo,
        "DONT_TRIGGER", "a", "a1").to("refs/for/master");
    r.assertOkStatus();

    // assert email notification
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);
    assertThat(m.body()).contains("Change subject: TRIGGER\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 1\n");
  }

  @Test
  public void watchFile() throws Exception {
    String watchedProject = createProject("watchedProject").get();
    String otherWatchedProject = createProject("otherWatchedProject").get();
    setApiUser(user);

    // watch file in project as user
    watch(watchedProject, "file:a.txt");

    // watch other project as user
    watch(otherWatchedProject, null);

    // push a change to watched file -> should trigger email notification for
    // user
    setApiUser(admin);
    TestRepository<InMemoryRepository> watchedRepo =
        cloneProject(new Project.NameKey(watchedProject), admin);
    PushOneCommit.Result r = pushFactory
        .create(db, admin.getIdent(), watchedRepo, "TRIGGER", "a.txt", "a1")
        .to("refs/for/master");
    r.assertOkStatus();

    // assert email notification for user
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);
    assertThat(m.body()).contains("Change subject: TRIGGER\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 1\n");
    sender.clear();

    // watch project as user2
    TestAccount user2 = accounts.create("user2", "user2@test.com", "User2");
    setApiUser(user2);
    watch(watchedProject, null);

    // push a change to non-watched file -> should not trigger email
    // notification for user, only for user2
    r = pushFactory.create(db, admin.getIdent(), watchedRepo,
        "TRIGGER_USER2", "b.txt", "b1").to("refs/for/master");
    r.assertOkStatus();

    // assert email notification
    messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user2.emailAddress);
    assertThat(m.body()).contains("Change subject: TRIGGER_USER2\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 1\n");
  }

  private void watch(String project, String filter)
      throws RestApiException {
    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();
    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = project;
    pwi.filter = filter;
    pwi.notifyAbandonedChanges = true;
    pwi.notifyNewChanges = true;
    pwi.notifyAllComments = true;
    projectsToWatch.add(pwi);
    gApi.accounts().self().setWatchedProjects(projectsToWatch);
  }

  @Test
  public void deleteAllProjectWatches() throws Exception {
    Map<ProjectWatchKey, Set<NotifyType>> watches = new HashMap<>();
    watches.put(ProjectWatchKey.create(project, "*"), ImmutableSet.of(NotifyType.ALL));
    watchConfig.upsertProjectWatches(admin.getId(), watches);
    assertThat(watchConfig.getProjectWatches(admin.getId())).isNotEmpty();

    Collection<WatchConfig.ProjectWatchKey> keys = new ArrayList<>();
    watchConfig.deleteAllProjectWatches(admin.getId(), keys);
    assertThat(watchConfig.getProjectWatches(admin.getId())).isEmpty();
  }

  @Test
  public void deleteAllProjectWatchesIfWatchConfigIsTheOnlyFileInUserBranch() throws Exception {
    // Create account that has no files in its refs/users/ branch.
    Account.Id id = new Account.Id(db.nextAccountId());
    Account a = new Account(id, TimeUtil.nowTs());
    db.accounts().insert(Collections.singleton(a));

    // Add a project watch so that a watch.config file in the refs/users/ branch is created.
    Map<ProjectWatchKey, Set<NotifyType>> watches = new HashMap<>();
    watches.put(ProjectWatchKey.create(project, "*"), ImmutableSet.of(NotifyType.ALL));
    watchConfig.upsertProjectWatches(id, watches);
    assertThat(watchConfig.getProjectWatches(id)).isNotEmpty();

    // Delete all project watches so that the watch.config file in the refs/users/ branch is
    // deleted.
    watchConfig.deleteAllProjectWatches(id);
    assertThat(watchConfig.getProjectWatches(id)).isEmpty();
  }
}
