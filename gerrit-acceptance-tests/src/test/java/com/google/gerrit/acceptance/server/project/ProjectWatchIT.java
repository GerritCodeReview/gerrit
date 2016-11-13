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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.NotifyConfig;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

@NoHttpd
public class ProjectWatchIT extends AbstractDaemonTest {
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

    PushOneCommit.Result r =
        pushFactory
            .create(db, admin.getIdent(), testRepo, "original subject", "a", "a1")
            .to("refs/for/master");
    r.assertOkStatus();

    r =
        pushFactory
            .create(
                db, admin.getIdent(), testRepo, "super sekret subject", "a", "a2", r.getChangeId())
            .to("refs/for/master");
    r.assertOkStatus();

    r =
        pushFactory
            .create(db, admin.getIdent(), testRepo, "back to original subject", "a", "a3")
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
    PushOneCommit.Result r =
        pushFactory
            .create(db, admin.getIdent(), watchedRepo, "TRIGGER", "a", "a1")
            .to("refs/for/master");
    r.assertOkStatus();

    // push a change to non-watched project -> should not trigger email
    // notification
    String notWatchedProject = createProject("otherProject").get();
    TestRepository<InMemoryRepository> notWatchedRepo =
        cloneProject(new Project.NameKey(notWatchedProject), admin);
    r =
        pushFactory
            .create(db, admin.getIdent(), notWatchedRepo, "DONT_TRIGGER", "a", "a1")
            .to("refs/for/master");
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
    // watch file in project
    String watchedProject = createProject("watchedProject").get();
    setApiUser(user);
    watch(watchedProject, "file:a.txt");

    // push a change to watched file -> should trigger email notification
    setApiUser(admin);
    TestRepository<InMemoryRepository> watchedRepo =
        cloneProject(new Project.NameKey(watchedProject), admin);
    PushOneCommit.Result r =
        pushFactory
            .create(db, admin.getIdent(), watchedRepo, "TRIGGER", "a.txt", "a1")
            .to("refs/for/master");
    r.assertOkStatus();

    // push a change to non-watched file -> should not trigger email
    // notification
    r =
        pushFactory
            .create(db, admin.getIdent(), testRepo, "DONT_TRIGGER", "b.txt", "b1")
            .to("refs/for/master");
    r.assertOkStatus();

    // assert email notification
    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);
    assertThat(m.body()).contains("Change subject: TRIGGER\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 1\n");
  }

  private void watch(String project, String filter) throws RestApiException {
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
}
