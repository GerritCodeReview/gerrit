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
import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createCommit;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.GitUtil.Commit;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.PushResult;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeUtils.MillisProvider;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractPushForReview extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config noteDbEnabled() {
    return NotesMigration.allEnabledConfig();
  }

  @Inject
  private NotesMigration notesMigration;

  protected enum Protocol {
    SSH, HTTP
  }

  private String sshUrl;

  @BeforeClass
  public static void setTimeForTesting() {
    final long clockStepMs = MILLISECONDS.convert(1, SECONDS);
    final AtomicLong clockMs = new AtomicLong(
        new DateTime(2009, 9, 30, 17, 0, 0).getMillis());
    DateTimeUtils.setCurrentMillisProvider(new MillisProvider() {
      @Override
      public long getMillis() {
        return clockMs.getAndAdd(clockStepMs);
      }
    });
  }

  @AfterClass
  public static void restoreTime() {
    DateTimeUtils.setCurrentMillisSystem();
  }

  @Before
  public void setUp() throws Exception {
    sshUrl = sshSession.getUrl();
  }

  protected void selectProtocol(Protocol p) throws GitAPIException, IOException {
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
    git = cloneProject(url + "/" + project.get());
  }

  @Test
  public void testPushForMaster() throws GitAPIException, OrmException,
      IOException {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);
  }

  @Test
  public void testPushForMasterWithTopic() throws GitAPIException,
      OrmException, IOException {
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
  public void testPushForMasterWithCc() throws GitAPIException, OrmException,
      IOException, JSchException {
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
  public void testPushForMasterWithReviewer() throws GitAPIException,
      OrmException, IOException, JSchException {
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
  public void testPushForMasterAsDraft() throws GitAPIException, OrmException,
      IOException {
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
  public void testPushForMasterAsEdit() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    EditInfo edit = getEdit(r.getChangeId());
    assertThat(edit).isNull();

    // specify edit as option
    r = amendChange(r.getChangeId(), "refs/for/master%edit");
    r.assertOkStatus();
    edit = getEdit(r.getChangeId());
    assertThat(edit).isNotNull();
  }

  @Test
  public void testPushForMasterWithApprovals() throws GitAPIException,
      IOException, RestApiException {
    PushOneCommit.Result r = pushTo("refs/for/master/%l=Code-Review");
    r.assertOkStatus();
    ChangeInfo ci = get(r.getChangeId());
    LabelInfo cr = ci.labels.get("Code-Review");
    assertThat(cr.all).hasSize(1);
    assertThat(cr.all.get(0).name).isEqualTo("Administrator");
    assertThat(cr.all.get(0).value.intValue()).is(1);
    assertThat(Iterables.getLast(ci.messages).message).isEqualTo(
        "Uploaded patch set 1: Code-Review+1.");

    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
            "b.txt", "anotherContent", r.getChangeId());
    r = push.to(git, "refs/for/master/%l=Code-Review+2");

    ci = get(r.getChangeId());
    cr = ci.labels.get("Code-Review");
    assertThat(Iterables.getLast(ci.messages).message).isEqualTo(
        "Uploaded patch set 2: Code-Review+2.");

    assertThat(cr.all).hasSize(1);
    assertThat(cr.all.get(0).name).isEqualTo("Administrator");
    assertThat(cr.all.get(0).value.intValue()).is(2);

    push =
        pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
            "c.txt", "moreContent", r.getChangeId());
    r = push.to(git, "refs/for/master/%l=Code-Review+2");
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
      throws GitAPIException, RestApiException {
    // Create a commit with "User" as author and committer
    Commit c = createCommit(git, user.getIdent(), PushOneCommit.SUBJECT);

    // Push this commit as "Administrator" (requires Forge Committer Identity)
    pushHead(git, "refs/for/master/%l=Code-Review+1", false);

    // Expected Code-Review votes:
    // 1. 0 from User (committer):
    //    When the committer is forged, the committer is automatically added as
    //    reviewer, hence we expect a dummy 0 vote for the committer.
    // 2. +1 from Administrator (uploader):
    //    On push Code-Review+1 was specified, hence we expect a +1 vote from
    //    the uploader.
    ChangeInfo ci = get(c.getChangeId());
    LabelInfo cr = ci.labels.get("Code-Review");
    assertThat(cr.all).hasSize(2);
    int indexAdmin = admin.fullName.equals(cr.all.get(0).name) ? 0 : 1;
    int indexUser = indexAdmin == 0 ? 1 : 0;
    assertThat(cr.all.get(indexAdmin).name).isEqualTo(admin.fullName);
    assertThat(cr.all.get(indexAdmin).value.intValue()).is(1);
    assertThat(cr.all.get(indexUser).name).isEqualTo(user.fullName);
    assertThat(cr.all.get(indexUser).value.intValue()).is(0);
    assertThat(Iterables.getLast(ci.messages).message).isEqualTo(
        "Uploaded patch set 1: Code-Review+1.");
  }

  @Test
  public void testPushNewPatchsetToRefsChanges() throws GitAPIException,
    IOException, OrmException {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
            "b.txt", "anotherContent", r.getChangeId());
    r = push.to(git, "refs/changes/" + r.getChange().change().getId().get());
    r.assertOkStatus();
  }

  @Test
  public void testPushForMasterWithApprovals_MissingLabel() throws GitAPIException,
      IOException {
      PushOneCommit.Result r = pushTo("refs/for/master/%l=Verify");
      r.assertErrorStatus("label \"Verify\" is not a configured label");
  }

  @Test
  public void testPushForMasterWithApprovals_ValueOutOfRange() throws GitAPIException,
      IOException {
    PushOneCommit.Result r = pushTo("refs/for/master/%l=Code-Review-3");
    r.assertErrorStatus("label \"Code-Review\": -3 is not a valid value");
  }

  @Test
  public void testPushForNonExistingBranch() throws GitAPIException,
      IOException {
    String branchName = "non-existing";
    PushOneCommit.Result r = pushTo("refs/for/" + branchName);
    r.assertErrorStatus("branch " + branchName + " not found");
  }

  @Test
  public void testPushForMasterWithHashtags() throws GitAPIException,
      OrmException, IOException, RestApiException {

    // Hashtags currently only work when noteDB is enabled
    assume().that(notesMigration.enabled()).isTrue();

    // specify a single hashtag as option
    String hashtag1 = "tag1";
    Set<String> expected = ImmutableSet.of(hashtag1);
    PushOneCommit.Result r = pushTo("refs/for/master%hashtag=#" + hashtag1);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);

    Set<String> hashtags = gApi.changes().id(r.getChangeId()).getHashtags();
    assertThat((Iterable<?>)hashtags).containsExactlyElementsIn(expected);

    // specify a single hashtag as option in new patch set
    String hashtag2 = "tag2";
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
            "b.txt", "anotherContent", r.getChangeId());
    r = push.to(git, "refs/for/master/%hashtag=" + hashtag2);
    r.assertOkStatus();
    expected = ImmutableSet.of(hashtag1, hashtag2);
    hashtags = gApi.changes().id(r.getChangeId()).getHashtags();
    assertThat((Iterable<?>)hashtags).containsExactlyElementsIn(expected);
  }

  @Test
  public void testPushForMasterWithMultipleHashtags() throws GitAPIException,
      OrmException, IOException, RestApiException {

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
    assertThat((Iterable<?>)hashtags).containsExactlyElementsIn(expected);

    // specify multiple hashtags as options in new patch set
    String hashtag3 = "tag3";
    String hashtag4 = "tag4";
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
            "b.txt", "anotherContent", r.getChangeId());
    r = push.to(git,
        "refs/for/master%hashtag=" + hashtag3 + ",hashtag=" + hashtag4);
    r.assertOkStatus();
    expected = ImmutableSet.of(hashtag1, hashtag2, hashtag3, hashtag4);
    hashtags = gApi.changes().id(r.getChangeId()).getHashtags();
    assertThat((Iterable<?>)hashtags).containsExactlyElementsIn(expected);
  }

  @Test
  public void testPushForMasterWithHashtagsNoteDbDisabled() throws GitAPIException,
      IOException {
    // push with hashtags should fail when noteDb is disabled
    assume().that(notesMigration.enabled()).isFalse();
    PushOneCommit.Result r = pushTo("refs/for/master%hashtag=tag1");
    r.assertErrorStatus("cannot add hashtags; noteDb is disabled");
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
        pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
            "b.txt", "anotherContent");

    PushOneCommit.Result r = push.to(git, "refs/for/master");
    r.assertOkStatus();

    PushResult pr = GitUtil.pushHead(
        git, "refs/for/foo%base=" + rBase.getCommitId().name(), false, false);
    assertThat(pr.getMessages()).containsMatch("changes: .*new: 1.*done");

    List<ChangeInfo> changes = query(r.getCommitId().name());
    assertThat(changes).hasSize(2);
    ChangeInfo c1 = get(changes.get(0).id);
    ChangeInfo c2 = get(changes.get(1).id);
    assertThat(c1.project).isEqualTo(c2.project);
    assertThat(c1.branch).isNotEqualTo(c2.branch);
    assertThat(c1.changeId).isEqualTo(c2.changeId);
    assertThat(c1.currentRevision).isEqualTo(c2.currentRevision);
  }

  @Test
  public void testPushCommitUsingSignedOffBy() throws Exception {
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
            "b.txt", "anotherContent");
    PushOneCommit.Result r = push.to(git, "refs/for/master");
    r.assertOkStatus();

    setUseSignedOffBy(InheritableBoolean.TRUE);
    blockForgeCommitter(project, "refs/heads/master");

    push = pushFactory.create(db, admin.getIdent(),
        PushOneCommit.SUBJECT + String.format(
            "\n\nSigned-off-by: %s <%s>", admin.fullName, admin.email),
        "b.txt", "anotherContent");
    r = push.to(git, "refs/for/master");
    r.assertOkStatus();

    push = pushFactory.create(db, admin.getIdent(), PushOneCommit.SUBJECT,
        "b.txt", "anotherContent");
    r = push.to(git, "refs/for/master");
    r.assertErrorStatus(
        "not Signed-off-by author/committer/uploader in commit message footer");
  }
}
