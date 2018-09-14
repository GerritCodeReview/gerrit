// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.common.data.Permission.READ;
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.jgit.lib.Constants.SIGNED_OFF_BY_TAG;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.submit.ChangeAlreadyMergedException;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CreateChangeIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @BeforeClass
  public static void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @AfterClass
  public static void restoreTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void createEmptyChange_MissingBranch() throws Exception {
    ChangeInput ci = new ChangeInput();
    ci.project = project.get();
    assertCreateFails(ci, BadRequestException.class, "branch must be non-empty");
  }

  @Test
  public void createEmptyChange_MissingMessage() throws Exception {
    ChangeInput ci = new ChangeInput();
    ci.project = project.get();
    ci.branch = "master";
    assertCreateFails(ci, BadRequestException.class, "commit message must be non-empty");
  }

  @Test
  public void createEmptyChange_InvalidStatus() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.MERGED);
    assertCreateFails(ci, BadRequestException.class, "unsupported change status");
  }

  @Test
  public void createEmptyChange_InvalidChangeId() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject = "Subject\n\nChange-Id: I0000000000000000000000000000000000000000";
    assertCreateFails(
        ci, ResourceConflictException.class, "invalid Change-Id line format in message footer");
  }

  @Test
  public void createEmptyChange_InvalidSubject() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject = "Change-Id: I1234000000000000000000000000000000000000";
    assertCreateFails(
        ci,
        ResourceConflictException.class,
        "missing subject; Change-Id must be in message footer");
  }

  @Test
  public void createNewChange_InvalidCommentInCommitMessage() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject = "#12345 Test";
    assertCreateFails(ci, BadRequestException.class, "commit message must be non-empty");
  }

  @Test
  public void createNewChange() throws Exception {
    ChangeInfo info = assertCreateSucceeds(newChangeInput(ChangeStatus.NEW));
    assertThat(info.revisions.get(info.currentRevision).commit.message)
        .contains("Change-Id: " + info.changeId);
  }

  @Test
  public void createNewChangeWithCommentsInCommitMessage() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    ci.subject += "\n# Comment line";
    ChangeInfo info = gApi.changes().create(ci).get();
    assertThat(info.revisions.get(info.currentRevision).commit.message)
        .doesNotContain("# Comment line");
  }

  @Test
  public void createNewChangeWithChangeId() throws Exception {
    ChangeInput ci = newChangeInput(ChangeStatus.NEW);
    String changeId = "I1234000000000000000000000000000000000000";
    String changeIdLine = "Change-Id: " + changeId;
    ci.subject = "Subject\n\n" + changeIdLine;
    ChangeInfo info = assertCreateSucceeds(ci);
    assertThat(info.changeId).isEqualTo(changeId);
    assertThat(info.revisions.get(info.currentRevision).commit.message).contains(changeIdLine);
  }

  @Test
  public void notificationsOnChangeCreation() throws Exception {
    requestScopeOperations.setApiUser(user.id());
    watch(project.get());

    // check that watcher is notified
    requestScopeOperations.setApiUser(admin.id());
    assertCreateSucceeds(newChangeInput(ChangeStatus.NEW));

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.getEmailAddress());
    assertThat(m.body()).contains(admin.fullName() + " has uploaded this change for review.");

    // check that watcher is not notified if notify=NONE
    sender.clear();
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.notify = NotifyHandling.NONE;
    assertCreateSucceeds(input);
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void createNewChangeSignedOffByFooter() throws Exception {
    setSignedOffByFooter(true);
    try {
      ChangeInfo info = assertCreateSucceeds(newChangeInput(ChangeStatus.NEW));
      String message = info.revisions.get(info.currentRevision).commit.message;
      assertThat(message)
          .contains(
              String.format(
                  "%sAdministrator <%s>", SIGNED_OFF_BY_TAG, admin.newIdent().getEmailAddress()));
    } finally {
      setSignedOffByFooter(false);
    }
  }

  @Test
  public void createNewChangeSignedOffByFooterWithChangeId() throws Exception {
    setSignedOffByFooter(true);
    try {
      ChangeInput ci = newChangeInput(ChangeStatus.NEW);
      String changeId = "I1234000000000000000000000000000000000000";
      String changeIdLine = "Change-Id: " + changeId;
      ci.subject = "Subject\n\n" + changeIdLine;
      ChangeInfo info = assertCreateSucceeds(ci);
      assertThat(info.changeId).isEqualTo(changeId);
      String message = info.revisions.get(info.currentRevision).commit.message;
      assertThat(message).contains(changeIdLine);
      assertThat(message)
          .contains(
              String.format(
                  "%sAdministrator <%s>", SIGNED_OFF_BY_TAG, admin.newIdent().getEmailAddress()));
    } finally {
      setSignedOffByFooter(false);
    }
  }

  @Test
  public void createNewPrivateChange() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.isPrivate = true;
    assertCreateSucceeds(input);
  }

  @Test
  public void createNewWorkInProgressChange() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.workInProgress = true;
    assertCreateSucceeds(input);
  }

  @Test
  public void createChangeWithParentCommit() throws Exception {
    Map<String, PushOneCommit.Result> setup =
        changeInTwoBranches("foo", "foo.txt", "bar", "bar.txt");
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.baseCommit = setup.get("master").getCommit().getId().name();
    assertCreateSucceeds(input);
  }

  @Test
  public void createChangeWithBadParentCommitFails() throws Exception {
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.baseCommit = "notasha1";
    assertCreateFails(
        input, UnprocessableEntityException.class, "Base notasha1 doesn't represent a valid SHA-1");
  }

  @Test
  public void createChangeWithParentCommitOnWrongBranchFails() throws Exception {
    Map<String, PushOneCommit.Result> setup =
        changeInTwoBranches("foo", "foo.txt", "bar", "bar.txt");
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.branch = "foo";
    input.baseCommit = setup.get("bar").getCommit().getId().name();
    assertCreateFails(
        input,
        BadRequestException.class,
        String.format("Commit %s doesn't exist on ref refs/heads/foo", input.baseCommit));
  }

  @Test
  public void createChangeWithoutAccessToParentCommitFails() throws Exception {
    Map<String, PushOneCommit.Result> results =
        changeInTwoBranches("invisible-branch", "a.txt", "visible-branch", "b.txt");
    block(project, "refs/heads/invisible-branch", READ, REGISTERED_USERS);

    ChangeInput in = newChangeInput(ChangeStatus.NEW);
    in.branch = "visible-branch";
    in.baseChange = results.get("invisible-branch").getChangeId();
    assertCreateFails(
        in, UnprocessableEntityException.class, "Base change not found: " + in.baseChange);
  }

  @Test
  public void noteDbCommit() throws Exception {
    ChangeInfo c = assertCreateSucceeds(newChangeInput(ChangeStatus.NEW));
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commit =
          rw.parseCommit(repo.exactRef(changeMetaRef(new Change.Id(c._number))).getObjectId());

      assertThat(commit.getShortMessage()).isEqualTo("Create change");

      PersonIdent expectedAuthor =
          changeNoteUtil.newIdent(getAccount(admin.id()), c.created, serverIdent.get());
      assertThat(commit.getAuthorIdent()).isEqualTo(expectedAuthor);

      assertThat(commit.getCommitterIdent())
          .isEqualTo(new PersonIdent(serverIdent.get(), c.created));
      assertThat(commit.getParentCount()).isEqualTo(0);
    }
  }

  @Test
  public void createMergeChange() throws Exception {
    changeInTwoBranches("branchA", "a.txt", "branchB", "b.txt");
    ChangeInput in = newMergeChangeInput("branchA", "branchB", "");
    assertCreateSucceeds(in);
  }

  @Test
  public void createMergeChange_Conflicts() throws Exception {
    changeInTwoBranches("branchA", "shared.txt", "branchB", "shared.txt");
    ChangeInput in = newMergeChangeInput("branchA", "branchB", "");
    assertCreateFails(in, RestApiException.class, "merge conflict");
  }

  @Test
  public void createMergeChange_Conflicts_Ours() throws Exception {
    changeInTwoBranches("branchA", "shared.txt", "branchB", "shared.txt");
    ChangeInput in = newMergeChangeInput("branchA", "branchB", "ours");
    assertCreateSucceeds(in);
  }

  @Test
  public void invalidSource() throws Exception {
    changeInTwoBranches("branchA", "a.txt", "branchB", "b.txt");
    ChangeInput in = newMergeChangeInput("branchA", "invalid", "");
    assertCreateFails(in, BadRequestException.class, "Cannot resolve 'invalid' to a commit");
  }

  @Test
  public void invalidStrategy() throws Exception {
    changeInTwoBranches("branchA", "a.txt", "branchB", "b.txt");
    ChangeInput in = newMergeChangeInput("branchA", "branchB", "octopus");
    assertCreateFails(in, BadRequestException.class, "invalid merge strategy: octopus");
  }

  @Test
  public void alreadyMerged() throws Exception {
    ObjectId c0 =
        testRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("first commit")
            .add("a.txt", "a contents ")
            .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("second commit")
        .add("b.txt", "b contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    ChangeInput in = newMergeChangeInput("master", c0.getName(), "");
    assertCreateFails(
        in, ChangeAlreadyMergedException.class, "'" + c0.getName() + "' has already been merged");
  }

  @Test
  public void onlyContentMerged() throws Exception {
    testRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("first commit")
        .add("a.txt", "a contents ")
        .create();
    testRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    // create a change, and cherrypick into master
    PushOneCommit.Result cId = createChange();
    RevCommit commitId = cId.getCommit();
    CherryPickInput cpi = new CherryPickInput();
    cpi.destination = "master";
    cpi.message = "cherry pick the commit";
    ChangeApi orig = gApi.changes().id(cId.getChangeId());
    ChangeApi cherry = orig.current().cherryPick(cpi);
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();

    ObjectId remoteId = getRemoteHead();
    assertThat(remoteId).isNotEqualTo(commitId);

    ChangeInput in = newMergeChangeInput("master", commitId.getName(), "");
    assertCreateSucceeds(in);
  }

  @Test
  public void cherryPickCommitWithoutChangeId() throws Exception {
    // This test is a little superfluous, since the current cherry-pick code ignores
    // the commit message of the to-be-cherry-picked change, using the one in
    // CherryPickInput instead.
    CherryPickInput input = new CherryPickInput();
    input.destination = "foo";
    input.message = "it goes to foo branch";
    gApi.projects().name(project.get()).branch(input.destination).create(new BranchInput());

    RevCommit revCommit = createNewCommitWithoutChangeId("refs/heads/master", "a.txt", "content");
    ChangeInfo changeInfo =
        gApi.projects().name(project.get()).commit(revCommit.getName()).cherryPick(input).get();

    assertThat(changeInfo.messages).hasSize(1);
    Iterator<ChangeMessageInfo> messageIterator = changeInfo.messages.iterator();
    String expectedMessage =
        String.format("Patch Set 1: Cherry Picked from commit %s.", revCommit.getName());
    assertThat(messageIterator.next().message).isEqualTo(expectedMessage);

    RevisionInfo revInfo = changeInfo.revisions.get(changeInfo.currentRevision);
    assertThat(revInfo).isNotNull();
    CommitInfo commitInfo = revInfo.commit;
    assertThat(commitInfo.message)
        .isEqualTo(input.message + "\n\nChange-Id: " + changeInfo.changeId + "\n");
  }

  @Test
  public void cherryPickCommitWithChangeId() throws Exception {
    CherryPickInput input = new CherryPickInput();
    input.destination = "foo";

    RevCommit revCommit = createChange().getCommit();
    List<String> footers = revCommit.getFooterLines("Change-Id");
    assertThat(footers).hasSize(1);
    String changeId = footers.get(0);

    input.message = "it goes to foo branch\n\nChange-Id: " + changeId;
    gApi.projects().name(project.get()).branch(input.destination).create(new BranchInput());

    ChangeInfo changeInfo =
        gApi.projects().name(project.get()).commit(revCommit.getName()).cherryPick(input).get();

    assertThat(changeInfo.messages).hasSize(1);
    Iterator<ChangeMessageInfo> messageIterator = changeInfo.messages.iterator();
    String expectedMessage =
        String.format("Patch Set 1: Cherry Picked from commit %s.", revCommit.getName());
    assertThat(messageIterator.next().message).isEqualTo(expectedMessage);

    RevisionInfo revInfo = changeInfo.revisions.get(changeInfo.currentRevision);
    assertThat(revInfo).isNotNull();
    assertThat(revInfo.commit.message).isEqualTo(input.message + "\n");
  }

  @Test
  public void createChangeOnExistingBranchNotPermitted() throws Exception {
    createBranch(new Branch.NameKey(project, "foo"));
    blockRead("refs/heads/*");
    requestScopeOperations.setApiUser(user.id());
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.branch = "foo";

    assertCreateFails(input, ResourceNotFoundException.class, "ref refs/heads/foo not found");
  }

  @Test
  public void createChangeOnNonExistingBranchNotPermitted() throws Exception {
    blockRead("refs/heads/*");
    requestScopeOperations.setApiUser(user.id());
    ChangeInput input = newChangeInput(ChangeStatus.NEW);
    input.branch = "foo";
    // sets this option to be true to make sure permission check happened before this option could
    // be considered.
    input.newBranch = true;

    assertCreateFails(input, ResourceNotFoundException.class, "ref refs/heads/foo not found");
  }

  private ChangeInput newChangeInput(ChangeStatus status) {
    ChangeInput in = new ChangeInput();
    in.project = project.get();
    in.branch = "master";
    in.subject = "Empty change";
    in.topic = "support-gerrit-workflow-in-browser";
    in.status = status;
    return in;
  }

  private ChangeInfo assertCreateSucceeds(ChangeInput in) throws Exception {
    ChangeInfo out = gApi.changes().create(in).get();
    assertThat(out.project).isEqualTo(in.project);
    assertThat(RefNames.fullName(out.branch)).isEqualTo(RefNames.fullName(in.branch));
    assertThat(out.subject).isEqualTo(in.subject.split("\n")[0]);
    assertThat(out.topic).isEqualTo(in.topic);
    assertThat(out.status).isEqualTo(in.status);
    if (in.isPrivate) {
      assertThat(out.isPrivate).isTrue();
    } else {
      assertThat(out.isPrivate).isNull();
    }
    if (in.workInProgress) {
      assertThat(out.workInProgress).isTrue();
    } else {
      assertThat(out.workInProgress).isNull();
    }
    assertThat(out.revisions).hasSize(1);
    assertThat(out.submitted).isNull();
    assertThat(in.status).isEqualTo(ChangeStatus.NEW);
    return out;
  }

  private void assertCreateFails(
      ChangeInput in, Class<? extends RestApiException> errType, String errSubstring)
      throws Exception {
    exception.expect(errType);
    exception.expectMessage(errSubstring);
    gApi.changes().create(in);
  }

  // TODO(davido): Expose setting of account preferences in the API
  private void setSignedOffByFooter(boolean value) throws Exception {
    RestResponse r = adminRestSession.get("/accounts/" + admin.email() + "/preferences");
    r.assertOK();
    GeneralPreferencesInfo i = newGson().fromJson(r.getReader(), GeneralPreferencesInfo.class);
    i.signedOffBy = value;

    r = adminRestSession.put("/accounts/" + admin.email() + "/preferences", i);
    r.assertOK();
    GeneralPreferencesInfo o = newGson().fromJson(r.getReader(), GeneralPreferencesInfo.class);

    if (value) {
      assertThat(o.signedOffBy).isTrue();
    } else {
      assertThat(o.signedOffBy).isNull();
    }

    requestScopeOperations.resetCurrentApiUser();
  }

  private ChangeInput newMergeChangeInput(String targetBranch, String sourceRef, String strategy) {
    // create a merge change from branchA to master in gerrit
    ChangeInput in = new ChangeInput();
    in.project = project.get();
    in.branch = targetBranch;
    in.subject = "merge " + sourceRef + " to " + targetBranch;
    in.status = ChangeStatus.NEW;
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = sourceRef;
    in.merge = mergeInput;
    if (!Strings.isNullOrEmpty(strategy)) {
      in.merge.strategy = strategy;
    }
    return in;
  }

  /**
   * Create an empty commit in master, two new branches with one commit each.
   *
   * @param branchA name of first branch to create
   * @param fileA name of file to commit to branchA
   * @param branchB name of second branch to create
   * @param fileB name of file to commit to branchB
   * @return A {@code Map} of branchName => commit result.
   * @throws Exception
   */
  private Map<String, Result> changeInTwoBranches(
      String branchA, String fileA, String branchB, String fileB) throws Exception {
    // create a initial commit in master
    Result initialCommit =
        pushFactory
            .create(user.newIdent(), testRepo, "initial commit", "readme.txt", "initial commit")
            .to("refs/heads/master");
    initialCommit.assertOkStatus();

    // create two new branches
    createBranch(new Branch.NameKey(project, branchA));
    createBranch(new Branch.NameKey(project, branchB));

    // create a commit in branchA
    Result changeA =
        pushFactory
            .create(user.newIdent(), testRepo, "change A", fileA, "A content")
            .to("refs/heads/" + branchA);
    changeA.assertOkStatus();

    // create a commit in branchB
    PushOneCommit commitB =
        pushFactory.create(user.newIdent(), testRepo, "change B", fileB, "B content");
    commitB.setParent(initialCommit.getCommit());
    Result changeB = commitB.to("refs/heads/" + branchB);
    changeB.assertOkStatus();

    return ImmutableMap.of("master", initialCommit, branchA, changeA, branchB, changeB);
  }
}
