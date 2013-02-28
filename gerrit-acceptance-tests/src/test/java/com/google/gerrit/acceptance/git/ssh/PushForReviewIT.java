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

package com.google.gerrit.acceptance.git.ssh;

import static com.google.gerrit.acceptance.git.ssh.GitUtil.add;
import static com.google.gerrit.acceptance.git.ssh.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.git.ssh.GitUtil.createCommit;
import static com.google.gerrit.acceptance.git.ssh.GitUtil.createProject;
import static com.google.gerrit.acceptance.git.ssh.GitUtil.initSsh;
import static com.google.gerrit.acceptance.git.ssh.GitUtil.pushHead;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class PushForReviewIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  private TestAccount admin;
  private SshSession sshSession;
  private Project.NameKey project;
  private Git git;
  private ReviewDb db;

  @Before
  public void setUp() throws Exception {
    admin =
        accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");
    sshSession = new SshSession(admin);

    project = new Project.NameKey("p");
    initSsh(admin);
    createProject(sshSession, project.get());
    git = cloneProject(sshSession, project.get());

    db = reviewDbProvider.open();
  }

  @After
  public void cleanup() {
    sshSession.close();
    db.close();
  }

  @Test
  public void testPushForMaster() throws GitAPIException, OrmException, IOException {
    PushOneCommit push = new PushOneCommit();
    String ref = "refs/for/master";
    PushResult r = push.to(ref);
    assertOkStatus(r, ref);
    assertChange(push.changeId, Change.Status.NEW, PushOneCommit.SUBJECT, null);
  }

  @Test
  public void testPushForMasterWithTopic() throws GitAPIException, OrmException, IOException {
    PushOneCommit push = new PushOneCommit();
    String topic = "my/topic";
    String ref = "refs/for/master/" + topic;
    PushResult r = push.to(ref);
    assertOkStatus(r, ref);
    assertChange(push.changeId, Change.Status.NEW, PushOneCommit.SUBJECT, topic);
  }

  @Test
  public void testPushForMasterWithTopicAndCc() throws GitAPIException, OrmException, IOException {
    PushOneCommit push = new PushOneCommit();
    String topic = "my/topic";
    String ref = "refs/for/master/" + topic + "%cc=" + admin.email;
    PushResult r = push.to(ref);
    assertOkStatus(r, ref);
    assertChange(push.changeId, Change.Status.NEW, PushOneCommit.SUBJECT, topic);
  }

  @Test
  public void testPushForMasterAsDraft() throws GitAPIException, OrmException, IOException {
    PushOneCommit push = new PushOneCommit();
    String ref = "refs/drafts/master";
    PushResult r = push.to(ref);
    assertOkStatus(r, ref);
    assertChange(push.changeId, Change.Status.DRAFT, PushOneCommit.SUBJECT, null);

    push = new PushOneCommit();
    ref = "refs/for/master%draft";
    r = push.to(ref);
    assertOkStatus(r, ref);
    assertChange(push.changeId, Change.Status.DRAFT, PushOneCommit.SUBJECT, null);
  }

  private void assertChange(String changeId, Change.Status expectedStatus,
      String expectedSubject, String expectedTopic) throws OrmException {
    Change c = Iterables.getOnlyElement(db.changes().byKey(new Change.Key(changeId)).toList());
    assertEquals(expectedSubject, c.getSubject());
    assertEquals(expectedStatus, c.getStatus());
    if (expectedTopic != null) {
      assertEquals(expectedTopic, c.getTopic());
    } else {
      assertNull(Strings.emptyToNull(c.getTopic()));
    }
  }

  private static void assertOkStatus(PushResult result, String ref) {
    assertStatus(Status.OK, result, ref);
  }

  private static void assertStatus(Status expected, PushResult result, String ref) {
    RemoteRefUpdate refUpdate = result.getRemoteUpdate(ref);
    assertEquals(
        refUpdate.getMessage() + "\nMessage from Gerrit:"
            + result.getMessages(), Status.OK, refUpdate.getStatus());
  }

  private class PushOneCommit {
    final static String FILE_NAME = "a.txt";
    final static String FILE_CONTENT = "some content";
    final static String SUBJECT = "test commit";
    String changeId;

    public PushResult to(String ref) throws GitAPIException, IOException {
      add(git, FILE_NAME, FILE_CONTENT);
      changeId = createCommit(sshSession, git, SUBJECT);
      return pushHead(git, ref);
    }
  }
}
