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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.GitUtil.assertPushOk;
import static com.google.gerrit.acceptance.GitUtil.assertPushRejected;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.value;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.project.Util;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import com.google.gerrit.testutil.TestTimeUtil;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractPushForReview extends AbstractDaemonTest {
  protected enum Protocol {
    // TODO(dborowitz): TEST.
    SSH, HTTP
  }

  private String sshUrl;
  private LabelType patchSetLock;

  @BeforeClass
  public static void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @AfterClass
  public static void restoreTime() {
    TestTimeUtil.useSystemTime();
  }

  @Before
  public void setUp() throws Exception {
    sshUrl = adminSshSession.getUrl();
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    patchSetLock = Util.patchSetLock();
    cfg.getLabelSections().put(patchSetLock.getName(), patchSetLock);
    AccountGroup.UUID anonymousUsers =
        SystemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    Util.allow(cfg, Permission.forLabel(patchSetLock.getName()), 0, 1, anonymousUsers,
        "refs/heads/*");
    saveProjectConfig(cfg);
    grant(Permission.LABEL + "Patch-Set-Lock", project, "refs/heads/*");
  }

  protected void selectProtocol(Protocol p) throws Exception {
    String url;
    switch (p) {
      case SSH:
        url = sshUrl;
        break;
      case HTTP:
        url = admin.getHttpUrl(server);
        break;
      default:
        throw new IllegalArgumentException("unexpected protocol: " + p);
    }
    testRepo = GitUtil.cloneProject(project, url + "/" + project.get());
  }

  @Test
  public void pushForMaster() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);
  }

  @Test
  public void output() throws Exception {
    String url = canonicalWebUrl.get();
    ObjectId initialHead = testRepo.getRepository().resolve("HEAD");
    PushOneCommit.Result r1 = pushTo("refs/for/master");
    Change.Id id1 = r1.getChange().getId();
    r1.assertOkStatus();
    r1.assertChange(Change.Status.NEW, null);
    r1.assertMessage(
        "New changes:\n"
        + "  " + url + id1 + " " + r1.getCommit().getShortMessage() + "\n");

    testRepo.reset(initialHead);
    String newMsg = r1.getCommit().getShortMessage() + " v2";
    testRepo.branch("HEAD").commit()
        .message(newMsg)
        .insertChangeId(r1.getChangeId().substring(1))
        .create();
    PushOneCommit.Result r2 = pushFactory.create(
            db, admin.getIdent(), testRepo, "another commit", "b.txt", "bbb")
        .to("refs/for/master");
    Change.Id id2 = r2.getChange().getId();
    r2.assertOkStatus();
    r2.assertChange(Change.Status.NEW, null);
    r2.assertMessage(
        "New changes:\n"
        + "  " + url + id2 + " another commit\n"
        + "\n"
        + "\n"
        + "Updated changes:\n"
        + "  " + url + id1 + " " + newMsg + "\n");
  }

  @Test
  public void pushForMasterWithTopic() throws Exception {
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
  public void pushForMasterWithNotify() throws Exception {
    TestAccount user2 = accounts.user2();
    String pushSpec = "refs/for/master"
        + "%reviewer=" + user.email
        + ",cc=" + user2.email;

    sender.clear();
    PushOneCommit.Result r =
        pushTo(pushSpec + ",notify=" + NotifyHandling.NONE);
    r.assertOkStatus();
    assertThat(sender.getMessages()).hasSize(0);

    sender.clear();
    r = pushTo(pushSpec + ",notify=" + NotifyHandling.OWNER);
    r.assertOkStatus();
    // no email notification about own changes
    assertThat(sender.getMessages()).hasSize(0);

    sender.clear();
    r = pushTo(pushSpec + ",notify=" + NotifyHandling.OWNER_REVIEWERS);
    r.assertOkStatus();
    assertThat(sender.getMessages()).hasSize(1);
    Message m = sender.getMessages().get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);

    sender.clear();
    r = pushTo(pushSpec + ",notify=" + NotifyHandling.ALL);
    r.assertOkStatus();
    assertThat(sender.getMessages()).hasSize(1);
    m = sender.getMessages().get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress, user2.emailAddress);
  }

  @Test
  public void pushForMasterWithCc() throws Exception {
    // cc one user
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
  public void pushForMasterWithReviewer() throws Exception {
    // add one reviewer
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
  public void pushForMasterAsDraft() throws Exception {
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
  public void publishDraftChangeByPushingNonDraftPatchSet() throws Exception {
    // create draft change
    PushOneCommit.Result r = pushTo("refs/drafts/master");
    r.assertOkStatus();
    r.assertChange(Change.Status.DRAFT, null);

    // publish draft change by pushing non-draft patch set
    r = amendChange(r.getChangeId(), "refs/for/master");
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);
  }

  @Test
  public void pushForMasterAsEdit() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    EditInfo edit = getEdit(r.getChangeId());
    assertThat(edit).isNull();

    // specify edit as option
    r = amendChange(r.getChangeId(), "refs/for/master%edit");
    r.assertOkStatus();
    edit = getEdit(r.getChangeId());
    assertThat(edit).isNotNull();
    r.assertMessage("Updated Changes:\n  "
        + canonicalWebUrl.get()
        + r.getChange().getId()
        + " " + edit.commit.subject + " [EDIT]\n");
  }

  @Test
  public void pushForMasterWithMessage() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master/%m=my_test_message");
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);
    ChangeInfo ci = get(r.getChangeId());
    Collection<ChangeMessageInfo> changeMessages = ci.messages;
    assertThat(changeMessages).hasSize(1);
    for (ChangeMessageInfo cm : changeMessages) {
      assertThat(cm.message).isEqualTo(
          "Uploaded patch set 1.\nmy test message");
    }
  }

  @Test
  public void pushForMasterWithApprovals() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master/%l=Code-Review");
    r.assertOkStatus();
    ChangeInfo ci = get(r.getChangeId());
    LabelInfo cr = ci.labels.get("Code-Review");
    assertThat(cr.all).hasSize(1);
    assertThat(cr.all.get(0).name).isEqualTo("Administrator");
    assertThat(cr.all.get(0).value).isEqualTo(1);
    assertThat(Iterables.getLast(ci.messages).message).isEqualTo(
        "Uploaded patch set 1: Code-Review+1.");

    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            "b.txt", "anotherContent", r.getChangeId());
    r = push.to("refs/for/master/%l=Code-Review+2");

    ci = get(r.getChangeId());
    cr = ci.labels.get("Code-Review");
    assertThat(Iterables.getLast(ci.messages).message).isEqualTo(
        "Uploaded patch set 2: Code-Review+2.");

    assertThat(cr.all).hasSize(1);
    assertThat(cr.all.get(0).name).isEqualTo("Administrator");
    assertThat(cr.all.get(0).value).isEqualTo(2);

    push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            "c.txt", "moreContent", r.getChangeId());
    r = push.to("refs/for/master/%l=Code-Review+2");
    ci = get(r.getChangeId());
    assertThat(Iterables.getLast(ci.messages).message).isEqualTo(
        "Uploaded patch set 3.");
  }

  /**
   * There was a bug that allowed a user with Forge Committer Identity access
   * right to upload a commit and put *votes on behalf of another user* on it.
   * This test checks that this is not possible, but that the votes that are
   * specified on push are applied only on behalf of the uploader.
   *
   * This particular bug only occurred when there was more than one label
   * defined. However to test that the votes that are specified on push are
   * applied on behalf of the uploader a single label is sufficient.
   */
  @Test
  public void pushForMasterWithApprovalsForgeCommitterButNoForgeVote()
      throws Exception {
    // Create a commit with "User" as author and committer
    RevCommit c = commitBuilder()
        .author(user.getIdent())
        .committer(user.getIdent())
        .add(PushOneCommit.FILE_NAME, PushOneCommit.FILE_CONTENT)
        .message(PushOneCommit.SUBJECT)
        .create();

    // Push this commit as "Administrator" (requires Forge Committer Identity)
    pushHead(testRepo, "refs/for/master/%l=Code-Review+1", false);

    // Expected Code-Review votes:
    // 1. 0 from User (committer):
    //    When the committer is forged, the committer is automatically added as
    //    reviewer, hence we expect a dummy 0 vote for the committer.
    // 2. +1 from Administrator (uploader):
    //    On push Code-Review+1 was specified, hence we expect a +1 vote from
    //    the uploader.
    ChangeInfo ci = get(GitUtil.getChangeId(testRepo, c).get());
    LabelInfo cr = ci.labels.get("Code-Review");
    assertThat(cr.all).hasSize(2);
    int indexAdmin = admin.fullName.equals(cr.all.get(0).name) ? 0 : 1;
    int indexUser = indexAdmin == 0 ? 1 : 0;
    assertThat(cr.all.get(indexAdmin).name).isEqualTo(admin.fullName);
    assertThat(cr.all.get(indexAdmin).value.intValue()).isEqualTo(1);
    assertThat(cr.all.get(indexUser).name).isEqualTo(user.fullName);
    assertThat(cr.all.get(indexUser).value.intValue()).isEqualTo(0);
    assertThat(Iterables.getLast(ci.messages).message).isEqualTo(
        "Uploaded patch set 1: Code-Review+1.");
  }

  @Test
  public void pushWithMultipleApprovals()
      throws Exception {
    LabelType Q = category("Custom-Label",
        value(1, "Positive"),
        value(0, "No score"),
        value(-1, "Negative"));
    ProjectConfig config = projectCache.checkedGet(project).getConfig();
    AccountGroup.UUID anon =
        SystemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    String heads = "refs/heads/*";
    Util.allow(config, Permission.forLabel("Custom-Label"), -1, 1, anon, heads);
    config.getLabelSections().put(Q.getName(), Q);
    saveProjectConfig(project, config);

    RevCommit c = commitBuilder()
        .author(admin.getIdent())
        .committer(admin.getIdent())
        .add(PushOneCommit.FILE_NAME, PushOneCommit.FILE_CONTENT)
        .message(PushOneCommit.SUBJECT)
        .create();

    pushHead(testRepo, "refs/for/master/%l=Code-Review+1,l=Custom-Label-1", false);

    ChangeInfo ci = get(GitUtil.getChangeId(testRepo, c).get());
    LabelInfo cr = ci.labels.get("Code-Review");
    assertThat(cr.all).hasSize(1);
    cr = ci.labels.get("Custom-Label");
    assertThat(cr.all).hasSize(1);
  }

  @Test
  public void pushNewPatchsetToRefsChanges() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            "b.txt", "anotherContent", r.getChangeId());
    r = push.to("refs/changes/" + r.getChange().change().getId().get());
    r.assertOkStatus();
  }

  @Test
  public void pushNewPatchsetToPatchSetLockedChange() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, "b.txt", "anotherContent", r.getChangeId());
    revision(r).review(new ReviewInput().label("Patch-Set-Lock", 1));
    r = push.to("refs/for/master");
    r.assertErrorStatus("cannot add patch set to "
        + r.getChange().change().getChangeId()
        + ". Change is patch set locked.");
  }

  @Test
  public void pushForMasterWithApprovals_MissingLabel() throws Exception {
      PushOneCommit.Result r = pushTo("refs/for/master/%l=Verify");
      r.assertErrorStatus("label \"Verify\" is not a configured label");
  }

  @Test
  public void pushForMasterWithApprovals_ValueOutOfRange() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master/%l=Code-Review-3");
    r.assertErrorStatus("label \"Code-Review\": -3 is not a valid value");
  }

  @Test
  public void pushForNonExistingBranch() throws Exception {
    String branchName = "non-existing";
    PushOneCommit.Result r = pushTo("refs/for/" + branchName);
    r.assertErrorStatus("branch " + branchName + " not found");
  }

  @Test
  public void pushForMasterWithHashtags() throws Exception {
    // Hashtags only work when reading from NoteDB is enabled
    assume().that(notesMigration.readChanges()).isTrue();

    // specify a single hashtag as option
    String hashtag1 = "tag1";
    Set<String> expected = ImmutableSet.of(hashtag1);
    PushOneCommit.Result r = pushTo("refs/for/master%hashtag=#" + hashtag1);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);

    Set<String> hashtags = gApi.changes().id(r.getChangeId()).getHashtags();
    assertThat(hashtags).containsExactlyElementsIn(expected);

    // specify a single hashtag as option in new patch set
    String hashtag2 = "tag2";
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            "b.txt", "anotherContent", r.getChangeId());
    r = push.to("refs/for/master/%hashtag=" + hashtag2);
    r.assertOkStatus();
    expected = ImmutableSet.of(hashtag1, hashtag2);
    hashtags = gApi.changes().id(r.getChangeId()).getHashtags();
    assertThat(hashtags).containsExactlyElementsIn(expected);
  }

  @Test
  public void pushForMasterWithMultipleHashtags() throws Exception {
    // Hashtags only work when reading from NoteDB is enabled
    assume().that(notesMigration.readChanges()).isTrue();

    // specify multiple hashtags as options
    String hashtag1 = "tag1";
    String hashtag2 = "tag2";
    Set<String> expected = ImmutableSet.of(hashtag1, hashtag2);
    PushOneCommit.Result r = pushTo("refs/for/master%hashtag=#" + hashtag1
        + ",hashtag=##" + hashtag2);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);

    Set<String> hashtags = gApi.changes().id(r.getChangeId()).getHashtags();
    assertThat(hashtags).containsExactlyElementsIn(expected);

    // specify multiple hashtags as options in new patch set
    String hashtag3 = "tag3";
    String hashtag4 = "tag4";
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            "b.txt", "anotherContent", r.getChangeId());
    r = push.to("refs/for/master%hashtag=" + hashtag3 + ",hashtag=" + hashtag4);
    r.assertOkStatus();
    expected = ImmutableSet.of(hashtag1, hashtag2, hashtag3, hashtag4);
    hashtags = gApi.changes().id(r.getChangeId()).getHashtags();
    assertThat(hashtags).containsExactlyElementsIn(expected);
  }

  @Test
  public void pushForMasterWithHashtagsNoteDbDisabled() throws Exception {
    // Push with hashtags should fail when reading from NoteDb is disabled.
    assume().that(notesMigration.readChanges()).isFalse();
    PushOneCommit.Result r = pushTo("refs/for/master%hashtag=tag1");
    r.assertErrorStatus("cannot add hashtags; noteDb is disabled");
  }

  @Test
  public void pushCommitUsingSignedOffBy() throws Exception {
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            "b.txt", "anotherContent");
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();

    setUseSignedOffBy(InheritableBoolean.TRUE);
    blockForgeCommitter(project, "refs/heads/master");

    push = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT + String.format(
            "\n\nSigned-off-by: %s <%s>", admin.fullName, admin.email),
        "b.txt", "anotherContent");
    r = push.to("refs/for/master");
    r.assertOkStatus();

    push = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, "b.txt", "anotherContent");
    r = push.to("refs/for/master");
    r.assertErrorStatus(
        "not Signed-off-by author/committer/uploader in commit message footer");
  }

  @Test
  public void createNewChangeForAllNotInTarget() throws Exception {
    enableCreateNewChangeForAllNotInTarget();

    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            "a.txt", "content");
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();

    push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            "b.txt", "anotherContent");
    r = push.to("refs/for/master");
    r.assertOkStatus();

    gApi.projects()
        .name(project.get())
        .branch("otherBranch")
        .create(new BranchInput());

    PushOneCommit.Result r2 = push.to("refs/for/otherBranch");
    r2.assertOkStatus();
    assertTwoChangesWithSameRevision(r);
  }

  @Test
  public void pushSameCommitTwiceUsingMagicBranchBaseOption()
      throws Exception {
    grant(Permission.PUSH, project, "refs/heads/master");
    PushOneCommit.Result rBase = pushTo("refs/heads/master");
    rBase.assertOkStatus();

    gApi.projects()
        .name(project.get())
        .branch("foo")
        .create(new BranchInput());

    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            "b.txt", "anotherContent");

    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();

    PushResult pr = GitUtil.pushHead(
        testRepo, "refs/for/foo%base=" + rBase.getCommitId().name(), false, false);
    assertThat(pr.getMessages()).containsMatch("changes: .*new: 1.*done");

    assertTwoChangesWithSameRevision(r);
  }

  private void assertTwoChangesWithSameRevision(PushOneCommit.Result result)
      throws Exception {
    List<ChangeInfo> changes = query(result.getCommit().name());
    assertThat(changes).hasSize(2);
    ChangeInfo c1 = get(changes.get(0).id);
    ChangeInfo c2 = get(changes.get(1).id);
    assertThat(c1.project).isEqualTo(c2.project);
    assertThat(c1.branch).isNotEqualTo(c2.branch);
    assertThat(c1.changeId).isEqualTo(c2.changeId);
    assertThat(c1.currentRevision).isEqualTo(c2.currentRevision);
  }

  @Test
  public void pushAFewChanges() throws Exception {
    int n = 10;
    String r = "refs/for/master";
    ObjectId initialHead = testRepo.getRepository().resolve("HEAD");
    List<RevCommit> commits = createChanges(n, r);

    // Check that a change was created for each.
    for (RevCommit c : commits) {
      assertThat(byCommit(c).change().getSubject())
          .named("change for " + c.name())
          .isEqualTo(c.getShortMessage());
    }

    List<RevCommit> commits2 = amendChanges(initialHead, commits, r);

    // Check that there are correct patch sets.
    for (int i = 0; i < n; i++) {
      RevCommit c = commits.get(i);
      RevCommit c2 = commits2.get(i);
      String name = "change for " + c2.name();
      ChangeData cd = byCommit(c);
      assertThat(cd.change().getSubject())
          .named(name)
          .isEqualTo(c2.getShortMessage());
      assertThat(getPatchSetRevisions(cd)).named(name).containsExactlyEntriesIn(
          ImmutableMap.of(1, c.name(), 2, c2.name()));
    }

    // Pushing again results in "no new changes".
    assertPushRejected(pushHead(testRepo, r, false), r, "no new changes");
  }

  @Test
  public void pushWithoutChangeId() throws Exception {
    testPushWithoutChangeId();
  }

  @Test
  public void pushWithoutChangeIdWithCreateNewChangeForAllNotInTarget()
      throws Exception {
    enableCreateNewChangeForAllNotInTarget();
    testPushWithoutChangeId();
  }

  private void testPushWithoutChangeId() throws Exception {
    RevCommit c = createCommit(testRepo, "Message without Change-Id");
    assertThat(GitUtil.getChangeId(testRepo, c).isPresent()).isFalse();
    pushForReviewRejected(testRepo,
        "missing Change-Id in commit message footer");

    setRequireChangeId(InheritableBoolean.FALSE);
    pushForReviewOk(testRepo);
  }

  @Test
  public void pushWithMultipleChangeIds() throws Exception {
    testPushWithMultipleChangeIds();
  }

  @Test
  public void pushWithMultipleChangeIdsWithCreateNewChangeForAllNotInTarget()
      throws Exception {
    enableCreateNewChangeForAllNotInTarget();
    testPushWithMultipleChangeIds();
  }

  private void testPushWithMultipleChangeIds() throws Exception {
    createCommit(testRepo,
        "Message with multiple Change-Id\n"
            + "\n"
            + "Change-Id: I10f98c2ef76e52e23aa23be5afeb71e40b350e86\n"
            + "Change-Id: Ie9a132e107def33bdd513b7854b50de911edba0a\n");
    pushForReviewRejected(testRepo,
        "multiple Change-Id lines in commit message footer");

    setRequireChangeId(InheritableBoolean.FALSE);
    pushForReviewRejected(testRepo,
        "multiple Change-Id lines in commit message footer");
  }

  @Test
  public void pushWithInvalidChangeId() throws Exception {
    testpushWithInvalidChangeId();
  }

  @Test
  public void pushWithInvalidChangeIdWithCreateNewChangeForAllNotInTarget()
      throws Exception {
    enableCreateNewChangeForAllNotInTarget();
    testpushWithInvalidChangeId();
  }

  private void testpushWithInvalidChangeId() throws Exception {
    createCommit(testRepo, "Message with invalid Change-Id\n"
        + "\n"
        + "Change-Id: X\n");
    pushForReviewRejected(testRepo,
        "invalid Change-Id line format in commit message footer");

    setRequireChangeId(InheritableBoolean.FALSE);
    pushForReviewRejected(testRepo,
        "invalid Change-Id line format in commit message footer");
  }

  @Test
  public void pushWithInvalidChangeIdFromEgit() throws Exception {
    testPushWithInvalidChangeIdFromEgit();
  }

  @Test
  public void pushWithInvalidChangeIdFromEgitWithCreateNewChangeForAllNotInTarget()
      throws Exception {
    enableCreateNewChangeForAllNotInTarget();
    testPushWithInvalidChangeIdFromEgit();
  }

  private void testPushWithInvalidChangeIdFromEgit() throws Exception {
    createCommit(testRepo, "Message with invalid Change-Id\n"
        + "\n"
        + "Change-Id: I0000000000000000000000000000000000000000\n");
    pushForReviewRejected(testRepo,
        "invalid Change-Id line format in commit message footer");

    setRequireChangeId(InheritableBoolean.FALSE);
    pushForReviewRejected(testRepo,
        "invalid Change-Id line format in commit message footer");
  }

  @Test
  public void pushWithChangeIdInSubjectLine() throws Exception {
    createCommit(testRepo, "Change-Id: I1234000000000000000000000000000000000000");
    pushForReviewRejected(testRepo,
        "missing subject; Change-Id must be in commit message footer");

    setRequireChangeId(InheritableBoolean.FALSE);
    pushForReviewRejected(testRepo,
        "missing subject; Change-Id must be in commit message footer");
  }

  private static RevCommit createCommit(TestRepository<?> testRepo,
      String message) throws Exception {
    return testRepo.branch("HEAD").commit().message(message)
        .add("a.txt", "content").create();
  }

  @Test
  public void cantAutoCloseChangeAlreadyMergedToBranch() throws Exception {
    PushOneCommit.Result r1 = createChange();
    Change.Id id1 = r1.getChange().getId();
    PushOneCommit.Result r2 = createChange();
    Change.Id id2 = r2.getChange().getId();

    // Merge change 1 behind Gerrit's back.
    try (Repository repo = repoManager.openRepository(project)) {
      TestRepository<?> tr = new TestRepository<>(repo);
      tr.branch("refs/heads/master").update(r1.getCommit());
    }

    assertThat(gApi.changes().id(id1.get()).info().status)
        .isEqualTo(ChangeStatus.NEW);
    assertThat(gApi.changes().id(id2.get()).info().status)
        .isEqualTo(ChangeStatus.NEW);
    r2 = amendChange(r2.getChangeId());
    r2.assertOkStatus();

    // Change 1 is still new despite being merged into the branch, because
    // ReceiveCommits only considers commits between the branch tip (which is
    // now the merged change 1) and the push tip (new patch set of change 2).
    assertThat(gApi.changes().id(id1.get()).info().status)
        .isEqualTo(ChangeStatus.NEW);
    assertThat(gApi.changes().id(id2.get()).info().status)
        .isEqualTo(ChangeStatus.NEW);
  }

  @Test
  public void accidentallyPushNewPatchSetDirectlyToBranchAndRecoverByPushingToRefsChanges()
      throws Exception {
    Change.Id id = accidentallyPushNewPatchSetDirectlyToBranch();
    ChangeData cd = byChangeId(id);
    String ps1Rev =
        Iterables.getOnlyElement(cd.patchSets()).getRevision().get();

    String r = "refs/changes/" + id;
    assertPushOk(pushHead(testRepo, r, false), r);

    // Added a new patch set and auto-closed the change.
    cd = byChangeId(id);
    assertThat(cd.change().getStatus()).isEqualTo(Change.Status.MERGED);
    assertThat(getPatchSetRevisions(cd)).containsExactlyEntriesIn(
        ImmutableMap.of(
            1, ps1Rev,
            2, testRepo.getRepository().resolve("HEAD").name()));
  }

  @Test
  public void accidentallyPushNewPatchSetDirectlyToBranchAndCantRecoverByPushingToRefsFor()
      throws Exception {
    Change.Id id = accidentallyPushNewPatchSetDirectlyToBranch();
    ChangeData cd = byChangeId(id);
    String ps1Rev =
        Iterables.getOnlyElement(cd.patchSets()).getRevision().get();

    String r = "refs/for/master";
    assertPushRejected(pushHead(testRepo, r, false), r, "no new changes");

    // Change not updated.
    cd = byChangeId(id);
    assertThat(cd.change().getStatus()).isEqualTo(Change.Status.NEW);
    assertThat(getPatchSetRevisions(cd)).containsExactlyEntriesIn(
        ImmutableMap.of(1, ps1Rev));
  }

  private Change.Id accidentallyPushNewPatchSetDirectlyToBranch()
      throws Exception {
    PushOneCommit.Result r = createChange();
    RevCommit ps1Commit = r.getCommit();
    Change c = r.getChange().change();

    RevCommit ps2Commit;
    try (Repository repo = repoManager.openRepository(project)) {
      // Create a new patch set of the change directly in Gerrit's repository,
      // without pushing it. In reality it's more likely that the client would
      // create and push this behind Gerrit's back (e.g. an admin accidentally
      // using direct ssh access to the repo), but that's harder to do in tests.
      TestRepository<?> tr = new TestRepository<>(repo);
      ps2Commit = tr.branch("refs/heads/master").commit()
          .message(ps1Commit.getShortMessage() + " v2")
          .insertChangeId(r.getChangeId().substring(1))
          .create();
    }

    testRepo.git().fetch()
        .setRefSpecs(new RefSpec("refs/heads/master")).call();
    testRepo.reset(ps2Commit);

    ChangeData cd = byCommit(ps1Commit);
    assertThat(cd.change().getStatus()).isEqualTo(Change.Status.NEW);
    assertThat(getPatchSetRevisions(cd)).containsExactlyEntriesIn(
        ImmutableMap.of(1, ps1Commit.name()));
    return c.getId();
  }

  @Test
  public void pushWithEmailInFooter() throws Exception {
    pushWithReviewerInFooter(user.emailAddress.toString(), user);
  }

  @Test
  public void pushWithNameInFooter() throws Exception {
    pushWithReviewerInFooter(user.fullName, user);
  }

  @Test
  public void pushWithEmailInFooterNotFound() throws Exception {
    pushWithReviewerInFooter(
        new Address("No Body", "notarealuser@example.com").toString(),
        null);
  }

  @Test
  public void pushWithNameInFooterNotFound() throws Exception {
    pushWithReviewerInFooter("Notauser", null);
  }

  @Test
  // TODO(dborowitz): This is to exercise a specific case in the database search
  // path. Once the account index becomes obligatory this method can be removed.
  @GerritConfig(name = "index.testDisable", value = "accounts")
  public void pushWithNameInFooterNotFoundWithDbSearch() throws Exception {
    pushWithReviewerInFooter("Notauser", null);
  }

  private void pushWithReviewerInFooter(String nameEmail,
      TestAccount expectedReviewer) throws Exception {
    int n = 5;
    String r = "refs/for/master";
    ObjectId initialHead = testRepo.getRepository().resolve("HEAD");
    List<RevCommit> commits =
        createChanges(n, r, ImmutableList.of("Acked-By: " + nameEmail));
    for (int i = 0; i < n; i++) {
      RevCommit c = commits.get(i);
      ChangeData cd = byCommit(c);
      String name = "reviewers for " + (i + 1);
      if (expectedReviewer != null) {
        assertThat(cd.reviewers().all()).named(name)
            .containsExactly(expectedReviewer.getId());
        gApi.changes()
            .id(cd.getId().get())
            .reviewer(expectedReviewer.getId().toString())
            .remove();
      }
      assertThat(byCommit(c).reviewers().all()).named(name).isEmpty();
    }

    List<RevCommit> commits2 = amendChanges(initialHead, commits, r);
    for (int i = 0; i < n; i++) {
      RevCommit c = commits2.get(i);
      ChangeData cd = byCommit(c);
      String name = "reviewers for " + (i + 1);
      if (expectedReviewer != null) {
        assertThat(cd.reviewers().all()).named(name)
            .containsExactly(expectedReviewer.getId());
      } else {
        assertThat(byCommit(c).reviewers().all()).named(name).isEmpty();
      }
    }
  }

  private List<RevCommit> createChanges(int n, String refsFor)
      throws Exception {
    return createChanges(n, refsFor, ImmutableList.<String>of());
  }

  private List<RevCommit> createChanges(int n, String refsFor,
      List<String> footerLines) throws Exception {
    List<RevCommit> commits = new ArrayList<>(n);
    for (int i = 1; i <= n; i++) {
      String msg = "Change " + i;
      if (!footerLines.isEmpty()) {
        StringBuilder sb = new StringBuilder(msg).append("\n\n");
        for (String line : footerLines) {
          sb.append(line).append('\n');
        }
        msg = sb.toString();
      }
      TestRepository<?>.CommitBuilder cb = testRepo.branch("HEAD").commit()
          .message(msg).insertChangeId();
      if (!commits.isEmpty()) {
        cb.parent(commits.get(commits.size() - 1));
      }
      RevCommit c = cb.create();
      testRepo.getRevWalk().parseBody(c);
      commits.add(c);
    }
    assertPushOk(pushHead(testRepo, refsFor, false), refsFor);
    return commits;
  }

  private List<RevCommit> amendChanges(ObjectId initialHead,
      List<RevCommit> origCommits, String refsFor) throws Exception {
    testRepo.reset(initialHead);
    List<RevCommit> newCommits = new ArrayList<>(origCommits.size());
    for (RevCommit c : origCommits) {
      String msg = c.getShortMessage() + "v2";
      if (!c.getShortMessage().equals(c.getFullMessage())) {
        msg = msg + c.getFullMessage().substring(c.getShortMessage().length());
      }
      TestRepository<?>.CommitBuilder cb = testRepo.branch("HEAD").commit()
          .message(msg);
      if (!newCommits.isEmpty()) {
        cb.parent(origCommits.get(newCommits.size() - 1));
      }
      RevCommit c2 = cb.create();
      testRepo.getRevWalk().parseBody(c2);
      newCommits.add(c2);
    }
    assertPushOk(pushHead(testRepo, refsFor, false), refsFor);
    return newCommits;
  }

  private static Map<Integer, String> getPatchSetRevisions(ChangeData cd)
      throws Exception {
    Map<Integer, String> revisions = new HashMap<>();
    for (PatchSet ps : cd.patchSets()) {
      revisions.put(ps.getPatchSetId(), ps.getRevision().get());
    }
    return revisions;
  }

  private ChangeData byCommit(ObjectId id) throws Exception {
    List<ChangeData> cds = queryProvider.get().byCommit(id);
    assertThat(cds).named("change for " + id.name()).hasSize(1);
    return cds.get(0);
  }

  private ChangeData byChangeId(Change.Id id) throws Exception {
    List<ChangeData> cds = queryProvider.get().byLegacyChangeId(id);
    assertThat(cds).named("change " + id).hasSize(1);
    return cds.get(0);
  }

  private static void pushForReviewOk(TestRepository<?> testRepo)
      throws GitAPIException {
    pushForReview(testRepo, RemoteRefUpdate.Status.OK, null);
  }

  private static void pushForReviewRejected(TestRepository<?> testRepo,
      String expectedMessage) throws GitAPIException {
    pushForReview(testRepo, RemoteRefUpdate.Status.REJECTED_OTHER_REASON,
        expectedMessage);
  }

  private static void pushForReview(TestRepository<?> testRepo,
      RemoteRefUpdate.Status expectedStatus, String expectedMessage)
          throws GitAPIException {
    String ref = "refs/for/master";
    PushResult r = pushHead(testRepo, ref);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(ref);
    assertThat(refUpdate.getStatus()).isEqualTo(expectedStatus);
    if (expectedMessage != null) {
      assertThat(refUpdate.getMessage()).contains(expectedMessage);
    }
  }

  private void enableCreateNewChangeForAllNotInTarget() throws Exception {
    ProjectConfig config = projectCache.checkedGet(project).getConfig();
    config.getProject()
        .setCreateNewChangeForAllNotInTarget(InheritableBoolean.TRUE);
    saveProjectConfig(project, config);
  }
}
