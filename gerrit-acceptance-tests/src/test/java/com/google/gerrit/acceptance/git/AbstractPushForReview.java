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
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.gerrit.server.git.ProjectConfig;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Set;

public abstract class AbstractPushForReview extends AbstractDaemonTest {
  protected enum Protocol {
    // TODO(dborowitz): TEST.
    SSH, HTTP
  }

  private String sshUrl;

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
    sshUrl = sshSession.getUrl();
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
  public void testPushForMaster() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);
  }

  @Test
  public void testOutput() throws Exception {
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
  public void testPushForMasterWithTopic() throws Exception {
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
  public void testPushForMasterWithCc() throws Exception {
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
  public void testPushForMasterWithReviewer() throws Exception {
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
  public void testPushForMasterAsDraft() throws Exception {
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
  public void testPushForMasterAsEdit() throws Exception {
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
  public void testPushForMasterWithApprovals() throws Exception {
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
  public void testPushForMasterWithApprovalsForgeCommitterButNoForgeVote()
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
  public void testPushNewPatchsetToRefsChanges() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
            "b.txt", "anotherContent", r.getChangeId());
    r = push.to("refs/changes/" + r.getChange().change().getId().get());
    r.assertOkStatus();
  }

  @Test
  public void testPushForMasterWithApprovals_MissingLabel() throws Exception {
      PushOneCommit.Result r = pushTo("refs/for/master/%l=Verify");
      r.assertErrorStatus("label \"Verify\" is not a configured label");
  }

  @Test
  public void testPushForMasterWithApprovals_ValueOutOfRange() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master/%l=Code-Review-3");
    r.assertErrorStatus("label \"Code-Review\": -3 is not a valid value");
  }

  @Test
  public void testPushForNonExistingBranch() throws Exception {
    String branchName = "non-existing";
    PushOneCommit.Result r = pushTo("refs/for/" + branchName);
    r.assertErrorStatus("branch " + branchName + " not found");
  }

  @Test
  public void testPushForMasterWithHashtags() throws Exception {

    // Hashtags currently only work when noteDB is enabled
    assume().that(notesMigration.enabled()).isTrue();

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
  public void testPushForMasterWithMultipleHashtags() throws Exception {

    // Hashtags currently only work when noteDB is enabled
    assume().that(notesMigration.enabled()).isTrue();

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
  public void testPushForMasterWithHashtagsNoteDbDisabled() throws Exception {
    // push with hashtags should fail when noteDb is disabled
    assume().that(notesMigration.enabled()).isFalse();
    PushOneCommit.Result r = pushTo("refs/for/master%hashtag=tag1");
    r.assertErrorStatus("cannot add hashtags; noteDb is disabled");
  }

  @Test
  public void testPushCommitUsingSignedOffBy() throws Exception {
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
  public void testCreateNewChangeForAllNotInTarget() throws Exception {
    ProjectConfig config = projectCache.checkedGet(project).getConfig();
    config.getProject().setCreateNewChangeForAllNotInTarget(InheritableBoolean.TRUE);
    saveProjectConfig(project, config);

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
  public void testPushSameCommitTwiceUsingMagicBranchBaseOption()
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
    List<ChangeInfo> changes = query(result.getCommitId().name());
    assertThat(changes).hasSize(2);
    ChangeInfo c1 = get(changes.get(0).id);
    ChangeInfo c2 = get(changes.get(1).id);
    assertThat(c1.project).isEqualTo(c2.project);
    assertThat(c1.branch).isNotEqualTo(c2.branch);
    assertThat(c1.changeId).isEqualTo(c2.changeId);
    assertThat(c1.currentRevision).isEqualTo(c2.currentRevision);
  }
}
