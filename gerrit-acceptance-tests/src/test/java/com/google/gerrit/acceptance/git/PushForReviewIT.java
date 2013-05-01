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

package com.google.gerrit.acceptance.git;

import static com.google.gerrit.acceptance.git.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.git.GitUtil.createProject;
import static com.google.gerrit.acceptance.git.GitUtil.initSsh;

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

import com.jcraft.jsch.JSchException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class PushForReviewIT extends AbstractDaemonTest {
  private enum Protocol {
    SSH, HTTP
  }

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  private TestAccount admin;
  private Project.NameKey project;
  private Git git;
  private ReviewDb db;
  private String sshUrl;

  @Before
  public void setUp() throws Exception {
    admin =
        accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");

    project = new Project.NameKey("p");
    initSsh(admin);
    SshSession sshSession = new SshSession(admin);
    createProject(sshSession, project.get());
    sshUrl = sshSession.getUrl();
    sshSession.close();

    db = reviewDbProvider.open();
  }

  private void selectProtocol(Protocol p) throws GitAPIException, IOException {
    String url;
    switch (p) {
      case SSH:
        url = sshUrl;
        break;
      case HTTP:
        url = admin.getHttpUrl();
        break;
      default:
        throw new IllegalArgumentException("unexpected protocol: " + p);
    }
    git = cloneProject(url + "/" + project.get());
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void testPushForMaster_HTTP() throws GitAPIException, OrmException,
      IOException {
    testPushForMaster(Protocol.HTTP);
  }

  @Test
  public void testPushForMaster_SSH() throws GitAPIException, OrmException,
      IOException {
    testPushForMaster(Protocol.SSH);
  }

  private void testPushForMaster(Protocol p) throws GitAPIException,
      OrmException, IOException {
    selectProtocol(p);
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);
  }

  @Test
  public void testPushForMasterWithTopic_HTTP()
      throws GitAPIException, OrmException, IOException {
    testPushForMasterWithTopic(Protocol.HTTP);
  }

  @Test
  public void testPushForMasterWithTopic_SSH()
      throws GitAPIException, OrmException, IOException {
    testPushForMasterWithTopic(Protocol.SSH);
  }

  private void testPushForMasterWithTopic(Protocol p) throws GitAPIException,
      OrmException, IOException {
    selectProtocol(p);
    // specify topic in ref
    String topic = "my/topic";
    PushOneCommit.Result r = pushTo("refs/for/master/" + topic);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, topic);

    // specify topic as option
    r = pushTo("refs/for/master%topic=" + topic);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, topic);
  }

  @Test
  public void testPushForMasterWithCc_HTTP() throws GitAPIException,
      OrmException, IOException, JSchException {
    testPushForMasterWithCc(Protocol.HTTP);
  }

  @Test
  public void testPushForMasterWithCc_SSH() throws GitAPIException,
      OrmException, IOException, JSchException {
    testPushForMasterWithCc(Protocol.SSH);
  }

  private void testPushForMasterWithCc(Protocol p) throws GitAPIException,
      OrmException, IOException, JSchException {
    selectProtocol(p);
    // cc one user
    TestAccount user = accounts.create("user", "user@example.com", "User");
    String topic = "my/topic";
    PushOneCommit.Result r = pushTo("refs/for/master/" + topic + "%cc=" + user.email);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, topic);

    // cc several users
    TestAccount user2 =
        accounts.create("another-user", "another.user@example.com", "Another User");
    r = pushTo("refs/for/master/" + topic + "%cc=" + admin.email + ",cc="
        + user.email + ",cc=" + user2.email);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, topic);

    // cc non-existing user
    String nonExistingEmail = "non.existing@example.com";
    r = pushTo("refs/for/master/" + topic + "%cc=" + admin.email + ",cc="
        + nonExistingEmail + ",cc=" + user.email);
    r.assertErrorStatus("user \"" + nonExistingEmail + "\" not found");
  }

  @Test
  public void testPushForMasterWithReviewer_HTTP() throws GitAPIException,
      OrmException, IOException, JSchException {
    testPushForMasterWithReviewer(Protocol.HTTP);
  }

  @Test
  public void testPushForMasterWithReviewer_SSH() throws GitAPIException,
      OrmException, IOException, JSchException {
    testPushForMasterWithReviewer(Protocol.SSH);
  }

  private void testPushForMasterWithReviewer(Protocol p)
      throws GitAPIException, OrmException, IOException, JSchException {
    selectProtocol(p);
    // add one reviewer
    TestAccount user = accounts.create("user", "user@example.com", "User");
    String topic = "my/topic";
    PushOneCommit.Result r = pushTo("refs/for/master/" + topic + "%r=" + user.email);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, topic, user);

    // add several reviewers
    TestAccount user2 =
        accounts.create("another-user", "another.user@example.com", "Another User");
    r = pushTo("refs/for/master/" + topic + "%r=" + admin.email + ",r=" + user.email
        + ",r=" + user2.email);
    r.assertOkStatus();
    // admin is the owner of the change and should not appear as reviewer
    r.assertChange(Change.Status.NEW, topic, user, user2);

    // add non-existing user as reviewer
    String nonExistingEmail = "non.existing@example.com";
    r = pushTo("refs/for/master/" + topic + "%r=" + admin.email + ",r="
        + nonExistingEmail + ",r=" + user.email);
    r.assertErrorStatus("user \"" + nonExistingEmail + "\" not found");
  }

  @Test
  public void testPushForMasterAsDraft_HTTP() throws GitAPIException,
      OrmException, IOException {
    testPushForMasterAsDraft(Protocol.HTTP);
  }

  @Test
  public void testPushForMasterAsDraft_SSH() throws GitAPIException,
      OrmException, IOException {
    testPushForMasterAsDraft(Protocol.SSH);
  }

  private void testPushForMasterAsDraft(Protocol p) throws GitAPIException,
      OrmException, IOException {
    selectProtocol(p);
    // create draft by pushing to 'refs/drafts/'
    PushOneCommit.Result r = pushTo("refs/drafts/master");
    r.assertOkStatus();
    r.assertChange(Change.Status.DRAFT, null);

    // create draft by using 'draft' option
    r = pushTo("refs/for/master%draft");
    r.assertOkStatus();
    r.assertChange(Change.Status.DRAFT, null);
  }

  @Test
  public void testPushForNonExistingBranch_HTTP() throws GitAPIException,
      OrmException, IOException {
    testPushForNonExistingBranch(Protocol.HTTP);
  }

  @Test
  public void testPushForNonExistingBranch_SSH() throws GitAPIException,
      OrmException, IOException {
    testPushForNonExistingBranch(Protocol.SSH);
  }

  private void testPushForNonExistingBranch(Protocol p) throws GitAPIException,
      OrmException, IOException {
    selectProtocol(p);
    String branchName = "non-existing";
    PushOneCommit.Result r = pushTo("refs/for/" + branchName);
    r.assertErrorStatus("branch " + branchName + " not found");
  }

  private PushOneCommit.Result pushTo(String ref) throws GitAPIException,
      IOException {
    PushOneCommit push = new PushOneCommit(db, admin.getIdent());
    return push.to(git, ref);
  }
}
