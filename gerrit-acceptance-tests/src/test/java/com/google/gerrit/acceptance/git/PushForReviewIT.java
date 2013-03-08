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

import static com.google.gerrit.acceptance.git.GitUtil.add;
import static com.google.gerrit.acceptance.git.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.git.GitUtil.createCommit;
import static com.google.gerrit.acceptance.git.GitUtil.createProject;
import static com.google.gerrit.acceptance.git.GitUtil.initSsh;
import static com.google.gerrit.acceptance.git.GitUtil.pushHead;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RunWith(Parameterized.class)
public class PushForReviewIT extends AbstractDaemonTest {

  private enum Protocol {
    SSH, HTTP
  }

  @Parameters(name="{0}")
  public static List<Object[]> getParam() {
    List<Object[]> params = Lists.newArrayList();
    for(Protocol p : Protocol.values()) {
      params.add(new Object[] {p});
    }
    return params;
  }

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  private TestAccount admin;
  private Project.NameKey project;
  private Git git;
  private ReviewDb db;
  private Protocol protocol;

  public PushForReviewIT(Protocol p) {
    this.protocol = p;
  }

  @Before
  public void setUp() throws Exception {
    admin =
        accounts.create("admin", "admin@example.com", "Administrator",
            "Administrators");

    project = new Project.NameKey("p");
    initSsh(admin);
    SshSession sshSession = new SshSession(admin);
    createProject(sshSession, project.get());
    String url;
    switch (protocol) {
      case SSH:
        url = sshSession.getUrl();
        break;
      case HTTP:
        url = admin.getHttpUrl();
        break;
      default:
        throw new IllegalStateException("unexpected protocol: " + protocol);
    }
    git = cloneProject(url + "/" + project.get());
    sshSession.close();

    db = reviewDbProvider.open();
  }

  @After
  public void cleanup() {
    db.close();
  }

  @Test
  public void testPushForMaster() throws GitAPIException, OrmException,
      IOException {
    PushOneCommit push = new PushOneCommit();
    String ref = "refs/for/master";
    PushResult r = push.to(ref);
    assertOkStatus(r, ref);
    assertChange(push.changeId, Change.Status.NEW, PushOneCommit.SUBJECT, null);
  }

  @Test
  public void testPushForMasterWithTopic() throws GitAPIException,
      OrmException, IOException {
    // specify topic in ref
    PushOneCommit push = new PushOneCommit();
    String topic = "my/topic";
    String ref = "refs/for/master/" + topic;
    PushResult r = push.to(ref);
    assertOkStatus(r, ref);
    assertChange(push.changeId, Change.Status.NEW, PushOneCommit.SUBJECT, topic);

    // specify topic as option
    push = new PushOneCommit();
    ref = "refs/for/master%topic=" + topic;
    r = push.to(ref);
    assertOkStatus(r, ref);
    assertChange(push.changeId, Change.Status.NEW, PushOneCommit.SUBJECT, topic);
  }

  @Test
  public void testPushForMasterWithCc() throws GitAPIException, OrmException,
      IOException, JSchException {
    // cc one user
    TestAccount user = accounts.create("user", "user@example.com", "User");
    PushOneCommit push = new PushOneCommit();
    String topic = "my/topic";
    String ref = "refs/for/master/" + topic + "%cc=" + user.email;
    PushResult r = push.to(ref);
    assertOkStatus(r, ref);
    assertChange(push.changeId, Change.Status.NEW, PushOneCommit.SUBJECT, topic);

    // cc several users
    TestAccount user2 =
        accounts.create("another-user", "another.user@example.com", "Another User");
    push = new PushOneCommit();
    ref = "refs/for/master/" + topic + "%cc=" + admin.email + ",cc=" + user.email
        + ",cc=" + user2.email;
    r = push.to(ref);
    assertOkStatus(r, ref);
    assertChange(push.changeId, Change.Status.NEW, PushOneCommit.SUBJECT, topic);

    // cc non-existing user
    String nonExistingEmail = "non.existing@example.com";
    push = new PushOneCommit();
    ref = "refs/for/master/" + topic + "%cc=" + admin.email + ",cc="
        + nonExistingEmail + ",cc=" + user.email;
    r = push.to(ref);
    assertErrorStatus(r, "user \"" + nonExistingEmail + "\" not found", ref);
  }

  @Test
  public void testPushForMasterWithReviewer() throws GitAPIException,
      OrmException, IOException, JSchException {
    // add one reviewer
    TestAccount user = accounts.create("user", "user@example.com", "User");
    PushOneCommit push = new PushOneCommit();
    String topic = "my/topic";
    String ref = "refs/for/master/" + topic + "%r=" + user.email;
    PushResult r = push.to(ref);
    assertOkStatus(r, ref);
    assertChange(push.changeId, Change.Status.NEW, PushOneCommit.SUBJECT,
        topic, user);

    // add several reviewers
    TestAccount user2 =
        accounts.create("another-user", "another.user@example.com", "Another User");
    push = new PushOneCommit();
    ref = "refs/for/master/" + topic + "%r=" + admin.email + ",r=" + user.email
        + ",r=" + user2.email;
    r = push.to(ref);
    assertOkStatus(r, ref);
    // admin is the owner of the change and should not appear as reviewer
    assertChange(push.changeId, Change.Status.NEW, PushOneCommit.SUBJECT,
        topic, user, user2);

    // add non-existing user as reviewer
    String nonExistingEmail = "non.existing@example.com";
    push = new PushOneCommit();
    ref = "refs/for/master/" + topic + "%r=" + admin.email + ",r="
        + nonExistingEmail + ",r=" + user.email;
    r = push.to(ref);
    assertErrorStatus(r, "user \"" + nonExistingEmail + "\" not found", ref);
  }

  @Test
  public void testPushForMasterAsDraft() throws GitAPIException, OrmException,
      IOException {
    // create draft by pushing to 'refs/drafts/'
    PushOneCommit push = new PushOneCommit();
    String ref = "refs/drafts/master";
    PushResult r = push.to(ref);
    assertOkStatus(r, ref);
    assertChange(push.changeId, Change.Status.DRAFT, PushOneCommit.SUBJECT, null);

    // create draft by using 'draft' option
    push = new PushOneCommit();
    ref = "refs/for/master%draft";
    r = push.to(ref);
    assertOkStatus(r, ref);
    assertChange(push.changeId, Change.Status.DRAFT, PushOneCommit.SUBJECT, null);
  }

  @Test
  public void testPushForNonExistingBranch() throws GitAPIException,
      OrmException, IOException {
    PushOneCommit push = new PushOneCommit();
    String branchName = "non-existing";
    String ref = "refs/for/" + branchName;
    PushResult r = push.to(ref);
    assertErrorStatus(r, "branch " + branchName + " not found", ref);
  }

  private void assertChange(String changeId, Change.Status expectedStatus,
      String expectedSubject, String expectedTopic,
      TestAccount... expectedReviewers) throws OrmException {
    Change c =
        Iterables.getOnlyElement(db.changes().byKey(new Change.Key(changeId)).toList());
    assertEquals(expectedSubject, c.getSubject());
    assertEquals(expectedStatus, c.getStatus());
    assertEquals(expectedTopic, Strings.emptyToNull(c.getTopic()));
    assertReviewers(c, expectedReviewers);
  }

  private void assertReviewers(Change c, TestAccount... expectedReviewers)
      throws OrmException {
    Set<Account.Id> expectedReviewerIds =
        Sets.newHashSet(Lists.transform(Arrays.asList(expectedReviewers),
            new Function<TestAccount, Account.Id>() {
              @Override
              public Account.Id apply(TestAccount a) {
                return a.id;
              }
            }));

    for (PatchSetApproval psa : db.patchSetApprovals().byPatchSet(
        c.currentPatchSetId())) {
      assertTrue("unexpected reviewer " + psa.getAccountId(),
          expectedReviewerIds.remove(psa.getAccountId()));
    }
    assertTrue("missing reviewers: " + expectedReviewerIds,
        expectedReviewerIds.isEmpty());
  }

  private static void assertOkStatus(PushResult result, String ref) {
    assertStatus(Status.OK, null, result, ref);
  }

  private static void assertErrorStatus(PushResult result,
      String expectedMessage, String ref) {
    assertStatus(Status.REJECTED_OTHER_REASON, expectedMessage, result, ref);
  }

  private static void assertStatus(Status expectedStatus,
      String expectedMessage, PushResult result, String ref) {
    RemoteRefUpdate refUpdate = result.getRemoteUpdate(ref);
    assertEquals(refUpdate.getMessage() + "\n" + result.getMessages(),
        expectedStatus, refUpdate.getStatus());
    assertEquals(expectedMessage, refUpdate.getMessage());
  }

  private class PushOneCommit {
    final static String FILE_NAME = "a.txt";
    final static String FILE_CONTENT = "some content";
    final static String SUBJECT = "test commit";
    String changeId;

    public PushResult to(String ref) throws GitAPIException, IOException {
      add(git, FILE_NAME, FILE_CONTENT);
      changeId = createCommit(git, admin.getIdent(), SUBJECT);
      return pushHead(git, ref);
    }
  }
}
