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
import static com.google.gerrit.server.StarredChangesUtil.IGNORE_LABEL;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.StarsInput;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.git.NotifyConfig;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.inject.Inject;
import java.util.EnumSet;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

@NoHttpd
public class ProjectWatchIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void newPatchSetsNotifyConfig() throws Exception {
    Address addr = new Address("Watcher", "watcher@example.com");
    NotifyConfig nc = new NotifyConfig();
    nc.addEmail(addr);
    nc.setName("new-patch-set");
    nc.setHeader(NotifyConfig.Header.CC);
    nc.setTypes(EnumSet.of(NotifyType.NEW_PATCHSETS));
    nc.setFilter("message:sekret");

    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().putNotifyConfig("watch", nc);
      u.save();
    }

    PushOneCommit.Result r =
        pushFactory
            .create(admin.newIdent(), testRepo, "original subject", "a", "a1")
            .to("refs/for/master");
    r.assertOkStatus();

    r =
        pushFactory
            .create(admin.newIdent(), testRepo, "super sekret subject", "a", "a2", r.getChangeId())
            .to("refs/for/master");
    r.assertOkStatus();

    r =
        pushFactory
            .create(admin.newIdent(), testRepo, "back to original subject", "a", "a3")
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
  public void noNotificationForPrivateChangesForWatchersInNotifyConfig() throws Exception {
    Address addr = new Address("Watcher", "watcher@example.com");
    NotifyConfig nc = new NotifyConfig();
    nc.addEmail(addr);
    nc.setName("team");
    nc.setHeader(NotifyConfig.Header.TO);
    nc.setTypes(EnumSet.of(NotifyType.NEW_CHANGES, NotifyType.ALL_COMMENTS));

    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().putNotifyConfig("team", nc);
      u.save();
    }

    sender.clear();
    PushOneCommit.Result r =
        pushFactory
            .create(admin.newIdent(), testRepo, "private change", "a", "a1")
            .to("refs/for/master%private");
    r.assertOkStatus();

    assertThat(sender.getMessages()).isEmpty();

    requestScopeOperations.setApiUser(admin.id());
    ReviewInput in = new ReviewInput();
    in.message = "comment";
    gApi.changes().id(r.getChangeId()).current().review(in);

    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void noNotificationForChangeThatIsTurnedPrivateForWatchersInNotifyConfig()
      throws Exception {
    Address addr = new Address("Watcher", "watcher@example.com");
    NotifyConfig nc = new NotifyConfig();
    nc.addEmail(addr);
    nc.setName("team");
    nc.setHeader(NotifyConfig.Header.TO);
    nc.setTypes(EnumSet.of(NotifyType.NEW_PATCHSETS));

    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().putNotifyConfig("team", nc);
      u.save();
    }

    PushOneCommit.Result r =
        pushFactory.create(admin.newIdent(), testRepo, "subject", "a", "a1").to("refs/for/master");
    r.assertOkStatus();

    sender.clear();

    r =
        pushFactory
            .create(admin.newIdent(), testRepo, "subject", "a", "a2", r.getChangeId())
            .to("refs/for/master%private");
    r.assertOkStatus();

    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void noNotificationForWipChangesForWatchersInNotifyConfig() throws Exception {
    Address addr = new Address("Watcher", "watcher@example.com");
    NotifyConfig nc = new NotifyConfig();
    nc.addEmail(addr);
    nc.setName("team");
    nc.setHeader(NotifyConfig.Header.TO);
    nc.setTypes(EnumSet.of(NotifyType.NEW_CHANGES, NotifyType.ALL_COMMENTS));

    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().putNotifyConfig("team", nc);
      u.save();
    }

    sender.clear();
    PushOneCommit.Result r =
        pushFactory
            .create(admin.newIdent(), testRepo, "wip change", "a", "a1")
            .to("refs/for/master%wip");
    r.assertOkStatus();

    assertThat(sender.getMessages()).isEmpty();

    requestScopeOperations.setApiUser(admin.id());
    ReviewInput in = new ReviewInput();
    in.message = "comment";
    gApi.changes().id(r.getChangeId()).current().review(in);

    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void noNotificationForChangeThatIsTurnedWipForWatchersInNotifyConfig() throws Exception {
    Address addr = new Address("Watcher", "watcher@example.com");
    NotifyConfig nc = new NotifyConfig();
    nc.addEmail(addr);
    nc.setName("team");
    nc.setHeader(NotifyConfig.Header.TO);
    nc.setTypes(EnumSet.of(NotifyType.NEW_PATCHSETS));

    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().putNotifyConfig("team", nc);
      u.save();
    }

    PushOneCommit.Result r =
        pushFactory.create(admin.newIdent(), testRepo, "subject", "a", "a1").to("refs/for/master");
    r.assertOkStatus();

    sender.clear();

    r =
        pushFactory
            .create(admin.newIdent(), testRepo, "subject", "a", "a2", r.getChangeId())
            .to("refs/for/master%wip");
    r.assertOkStatus();

    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void watchProject() throws Exception {
    // watch project
    String watchedProject = projectOperations.newProject().create().get();
    requestScopeOperations.setApiUser(user.id());
    watch(watchedProject);

    // push a change to watched project -> should trigger email notification
    requestScopeOperations.setApiUser(admin.id());
    TestRepository<InMemoryRepository> watchedRepo =
        cloneProject(Project.nameKey(watchedProject), admin);
    PushOneCommit.Result r =
        pushFactory
            .create(admin.newIdent(), watchedRepo, "TRIGGER", "a", "a1")
            .to("refs/for/master");
    r.assertOkStatus();

    // push a change to non-watched project -> should not trigger email
    // notification
    String notWatchedProject = projectOperations.newProject().create().get();
    TestRepository<InMemoryRepository> notWatchedRepo =
        cloneProject(Project.nameKey(notWatchedProject), admin);
    r =
        pushFactory
            .create(admin.newIdent(), notWatchedRepo, "DONT_TRIGGER", "a", "a1")
            .to("refs/for/master");
    r.assertOkStatus();

    // assert email notification
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.getEmailAddress());
    assertThat(m.body()).contains("Change subject: TRIGGER\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 1\n");
  }

  @Test
  public void watchFile() throws Exception {
    String watchedProject = projectOperations.newProject().create().get();
    String otherWatchedProject = projectOperations.newProject().create().get();
    requestScopeOperations.setApiUser(user.id());

    // watch file in project as user
    watch(watchedProject, "file:a.txt");

    // watch other project as user
    watch(otherWatchedProject);

    // push a change to watched file -> should trigger email notification for
    // user
    requestScopeOperations.setApiUser(admin.id());
    TestRepository<InMemoryRepository> watchedRepo =
        cloneProject(Project.nameKey(watchedProject), admin);
    PushOneCommit.Result r =
        pushFactory
            .create(admin.newIdent(), watchedRepo, "TRIGGER", "a.txt", "a1")
            .to("refs/for/master");
    r.assertOkStatus();

    // assert email notification for user
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.getEmailAddress());
    assertThat(m.body()).contains("Change subject: TRIGGER\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 1\n");
    sender.clear();

    // watch project as user2
    TestAccount user2 = accountCreator.create("user2", "user2@test.com", "User2");
    requestScopeOperations.setApiUser(user2.id());
    watch(watchedProject);

    // push a change to non-watched file -> should not trigger email
    // notification for user, only for user2
    r =
        pushFactory
            .create(admin.newIdent(), watchedRepo, "TRIGGER_USER2", "b.txt", "b1")
            .to("refs/for/master");
    r.assertOkStatus();

    // assert email notification
    messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user2.getEmailAddress());
    assertThat(m.body()).contains("Change subject: TRIGGER_USER2\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 1\n");
  }

  @Test
  public void watchKeyword() throws Exception {
    String watchedProject = projectOperations.newProject().create().get();
    requestScopeOperations.setApiUser(user.id());

    // watch keyword in project as user
    watch(watchedProject, "multimaster");

    // push a change with keyword -> should trigger email notification
    requestScopeOperations.setApiUser(admin.id());
    TestRepository<InMemoryRepository> watchedRepo =
        cloneProject(Project.nameKey(watchedProject), admin);
    PushOneCommit.Result r =
        pushFactory
            .create(admin.newIdent(), watchedRepo, "Document multimaster setup", "a.txt", "a1")
            .to("refs/for/master");
    r.assertOkStatus();

    // assert email notification for user
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.getEmailAddress());
    assertThat(m.body()).contains("Change subject: Document multimaster setup\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 1\n");
    sender.clear();

    // push a change without keyword -> should not trigger email notification
    r =
        pushFactory
            .create(admin.newIdent(), watchedRepo, "Cleanup cache implementation", "b.txt", "b1")
            .to("refs/for/master");
    r.assertOkStatus();

    // assert email notification
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void watchAllProjects() throws Exception {
    String anyProject = projectOperations.newProject().create().get();
    requestScopeOperations.setApiUser(user.id());

    // watch the All-Projects project to watch all projects
    watch(allProjects.get());

    // push a change to any project -> should trigger email notification
    requestScopeOperations.setApiUser(admin.id());
    TestRepository<InMemoryRepository> anyRepo = cloneProject(Project.nameKey(anyProject), admin);
    PushOneCommit.Result r =
        pushFactory.create(admin.newIdent(), anyRepo, "TRIGGER", "a", "a1").to("refs/for/master");
    r.assertOkStatus();

    // assert email notification
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.getEmailAddress());
    assertThat(m.body()).contains("Change subject: TRIGGER\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 1\n");
  }

  @Test
  public void watchFileAllProjects() throws Exception {
    String anyProject = projectOperations.newProject().create().get();
    requestScopeOperations.setApiUser(user.id());

    // watch file in All-Projects project as user to watch the file in all
    // projects
    watch(allProjects.get(), "file:a.txt");

    // push a change to watched file in any project -> should trigger email
    // notification for user
    requestScopeOperations.setApiUser(admin.id());
    TestRepository<InMemoryRepository> anyRepo = cloneProject(Project.nameKey(anyProject), admin);
    PushOneCommit.Result r =
        pushFactory
            .create(admin.newIdent(), anyRepo, "TRIGGER", "a.txt", "a1")
            .to("refs/for/master");
    r.assertOkStatus();

    // assert email notification for user
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.getEmailAddress());
    assertThat(m.body()).contains("Change subject: TRIGGER\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 1\n");
    sender.clear();

    // watch project as user2
    TestAccount user2 = accountCreator.create("user2", "user2@test.com", "User2");
    requestScopeOperations.setApiUser(user2.id());
    watch(anyProject);

    // push a change to non-watched file in any project -> should not trigger
    // email notification for user, only for user2
    r =
        pushFactory
            .create(admin.newIdent(), anyRepo, "TRIGGER_USER2", "b.txt", "b1")
            .to("refs/for/master");
    r.assertOkStatus();

    // assert email notification
    messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user2.getEmailAddress());
    assertThat(m.body()).contains("Change subject: TRIGGER_USER2\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 1\n");
  }

  @Test
  public void watchKeywordAllProjects() throws Exception {
    String anyProject = projectOperations.newProject().create().get();
    requestScopeOperations.setApiUser(user.id());

    // watch keyword in project as user
    watch(allProjects.get(), "multimaster");

    // push a change with keyword to any project -> should trigger email
    // notification
    requestScopeOperations.setApiUser(admin.id());
    TestRepository<InMemoryRepository> anyRepo = cloneProject(Project.nameKey(anyProject), admin);
    PushOneCommit.Result r =
        pushFactory
            .create(admin.newIdent(), anyRepo, "Document multimaster setup", "a.txt", "a1")
            .to("refs/for/master");
    r.assertOkStatus();

    // assert email notification for user
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.getEmailAddress());
    assertThat(m.body()).contains("Change subject: Document multimaster setup\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 1\n");
    sender.clear();

    // push a change without keyword to any project -> should not trigger email
    // notification
    r =
        pushFactory
            .create(admin.newIdent(), anyRepo, "Cleanup cache implementation", "b.txt", "b1")
            .to("refs/for/master");
    r.assertOkStatus();

    // assert email notification
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void watchProjectNoNotificationForIgnoredChange() throws Exception {
    // watch project
    String watchedProject = projectOperations.newProject().create().get();
    requestScopeOperations.setApiUser(user.id());
    watch(watchedProject);

    // push a change to watched project
    requestScopeOperations.setApiUser(admin.id());
    TestRepository<InMemoryRepository> watchedRepo =
        cloneProject(Project.nameKey(watchedProject), admin);
    PushOneCommit.Result r =
        pushFactory
            .create(admin.newIdent(), watchedRepo, "ignored change", "a", "a1")
            .to("refs/for/master");
    r.assertOkStatus();

    // ignore the change
    requestScopeOperations.setApiUser(user.id());
    gApi.accounts().self().setStars(r.getChangeId(), new StarsInput(ImmutableSet.of(IGNORE_LABEL)));

    sender.clear();

    // post a comment -> should not trigger email notification since user ignored the change
    requestScopeOperations.setApiUser(admin.id());
    ReviewInput in = new ReviewInput();
    in.message = "comment";
    gApi.changes().id(r.getChangeId()).current().review(in);

    // assert email notification
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void watchProjectNoNotificationForPrivateChange() throws Exception {
    // watch project
    String watchedProject = projectOperations.newProject().create().get();
    requestScopeOperations.setApiUser(user.id());
    watch(watchedProject);

    // push a private change to watched project -> should not trigger email notification
    requestScopeOperations.setApiUser(admin.id());
    TestRepository<InMemoryRepository> watchedRepo =
        cloneProject(Project.nameKey(watchedProject), admin);
    PushOneCommit.Result r =
        pushFactory
            .create(admin.newIdent(), watchedRepo, "private change", "a", "a1")
            .to("refs/for/master%private");
    r.assertOkStatus();

    // assert email notification
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void watchProjectNotifyOnPrivateChange() throws Exception {
    String watchedProject = projectOperations.newProject().create().get();

    // create group that can view all private changes
    GroupInfo groupThatCanViewPrivateChanges =
        gApi.groups().create("groupThatCanViewPrivateChanges").get();
    grant(
        Project.nameKey(watchedProject),
        "refs/*",
        Permission.VIEW_PRIVATE_CHANGES,
        false,
        AccountGroup.uuid(groupThatCanViewPrivateChanges.id));

    // watch project as user that can't view private changes
    requestScopeOperations.setApiUser(user.id());
    watch(watchedProject);

    // watch project as user that can view all private change
    TestAccount userThatCanViewPrivateChanges =
        accountCreator.create(
            "user2", "user2@test.com", "User2", groupThatCanViewPrivateChanges.name);
    requestScopeOperations.setApiUser(userThatCanViewPrivateChanges.id());
    watch(watchedProject);

    // push a private change to watched project -> should trigger email notification for
    // userThatCanViewPrivateChanges, but not for user
    requestScopeOperations.setApiUser(admin.id());
    TestRepository<InMemoryRepository> watchedRepo =
        cloneProject(Project.nameKey(watchedProject), admin);
    PushOneCommit.Result r =
        pushFactory
            .create(admin.newIdent(), watchedRepo, "TRIGGER", "a", "a1")
            .to("refs/for/master%private");
    r.assertOkStatus();

    // assert email notification
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(userThatCanViewPrivateChanges.getEmailAddress());
    assertThat(m.body()).contains("Change subject: TRIGGER\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 1\n");
  }
}
