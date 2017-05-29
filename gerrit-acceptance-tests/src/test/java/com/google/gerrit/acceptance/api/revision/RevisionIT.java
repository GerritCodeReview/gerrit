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

package com.google.gerrit.acceptance.api.revision;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_CONTENT;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.acceptance.PushOneCommit.PATCH;
import static com.google.gerrit.acceptance.PushOneCommit.PATCH_FILE_ONLY;
import static com.google.gerrit.acceptance.PushOneCommit.SUBJECT;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;
import static com.google.gerrit.reviewdb.client.Patch.COMMIT_MSG;
import static com.google.gerrit.reviewdb.client.Patch.MERGE_LIST;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.DraftApi;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.CommentInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.MergeableInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ETagView;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Branch.NameKey;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.change.GetRevisionActions;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

public class RevisionIT extends AbstractDaemonTest {

  @Inject private GetRevisionActions getRevisionActions;
  @Inject private DynamicSet<PatchSetWebLink> patchSetLinks;

  @Test
  public void reviewTriplet() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
  }

  @Test
  public void reviewCurrent() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
  }

  @Test
  public void reviewNumber() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(1).review(ReviewInput.approve());

    r = updateChange(r, "new content");
    gApi.changes().id(r.getChangeId()).revision(2).review(ReviewInput.approve());
  }

  @Test
  public void submit() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = project.get() + "~master~" + r.getChangeId();
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    gApi.changes().id(changeId).current().submit();
    assertThat(gApi.changes().id(changeId).get().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void postSubmitApproval() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = project.get() + "~master~" + r.getChangeId();
    gApi.changes().id(changeId).current().review(ReviewInput.recommend());

    String label = "Code-Review";
    ApprovalInfo approval = getApproval(changeId, label);
    assertThat(approval.value).isEqualTo(1);
    assertThat(approval.postSubmit).isNull();

    // Submit by direct push.
    git().push().setRefSpecs(new RefSpec(r.getCommit().name() + ":refs/heads/master")).call();
    assertThat(gApi.changes().id(changeId).get().status).isEqualTo(ChangeStatus.MERGED);

    approval = getApproval(changeId, label);
    assertThat(approval.value).isEqualTo(1);
    assertThat(approval.postSubmit).isNull();
    assertPermitted(
        gApi.changes().id(changeId).get(EnumSet.of(DETAILED_LABELS)), "Code-Review", 1, 2);

    // Repeating the current label is allowed. Does not flip the postSubmit bit
    // due to deduplication codepath.
    gApi.changes().id(changeId).current().review(ReviewInput.recommend());
    approval = getApproval(changeId, label);
    assertThat(approval.value).isEqualTo(1);
    assertThat(approval.postSubmit).isNull();

    // Reducing vote is not allowed.
    try {
      gApi.changes().id(changeId).current().review(ReviewInput.dislike());
      fail("expected ResourceConflictException");
    } catch (ResourceConflictException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("Cannot reduce vote on labels for closed change: Code-Review");
    }
    approval = getApproval(changeId, label);
    assertThat(approval.value).isEqualTo(1);
    assertThat(approval.postSubmit).isNull();

    // Increasing vote is allowed.
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    approval = getApproval(changeId, label);
    assertThat(approval.value).isEqualTo(2);
    assertThat(approval.postSubmit).isTrue();
    assertPermitted(gApi.changes().id(changeId).get(EnumSet.of(DETAILED_LABELS)), "Code-Review", 2);

    // Decreasing to previous post-submit vote is still not allowed.
    try {
      gApi.changes().id(changeId).current().review(ReviewInput.dislike());
      fail("expected ResourceConflictException");
    } catch (ResourceConflictException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo("Cannot reduce vote on labels for closed change: Code-Review");
    }
    approval = getApproval(changeId, label);
    assertThat(approval.value).isEqualTo(2);
    assertThat(approval.postSubmit).isTrue();
  }

  @Test
  public void postSubmitApprovalAfterVoteRemoved() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = project.get() + "~master~" + r.getChangeId();

    setApiUser(admin);
    revision(r).review(ReviewInput.approve());

    setApiUser(user);
    revision(r).review(ReviewInput.recommend());

    setApiUser(admin);
    gApi.changes().id(changeId).reviewer(user.username).deleteVote("Code-Review");
    Optional<ApprovalInfo> crUser =
        get(changeId, DETAILED_LABELS)
            .labels
            .get("Code-Review")
            .all
            .stream()
            .filter(a -> a._accountId == user.id.get())
            .findFirst();
    assertThat(crUser).isPresent();
    assertThat(crUser.get().value).isEqualTo(0);

    revision(r).submit();

    setApiUser(user);
    ReviewInput in = new ReviewInput();
    in.label("Code-Review", 1);
    in.message = "Still LGTM";
    revision(r).review(in);

    ApprovalInfo cr =
        gApi.changes()
            .id(changeId)
            .get(EnumSet.of(ListChangesOption.DETAILED_LABELS))
            .labels
            .get("Code-Review")
            .all
            .stream()
            .filter(a -> a._accountId == user.getId().get())
            .findFirst()
            .get();
    assertThat(cr.postSubmit).isTrue();
  }

  @Test
  public void postSubmitDeleteApprovalNotAllowed() throws Exception {
    PushOneCommit.Result r = createChange();

    revision(r).review(ReviewInput.approve());
    revision(r).submit();

    ReviewInput in = new ReviewInput();
    in.label("Code-Review", 0);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Cannot reduce vote on labels for closed change: Code-Review");
    revision(r).review(in);
  }

  @TestProjectInput(submitType = SubmitType.CHERRY_PICK)
  @Test
  public void approvalCopiedDuringSubmitIsNotPostSubmit() throws Exception {
    PushOneCommit.Result r = createChange();
    Change.Id id = r.getChange().getId();
    gApi.changes().id(id.get()).current().review(ReviewInput.approve());
    gApi.changes().id(id.get()).current().submit();

    ChangeData cd = r.getChange();
    assertThat(cd.patchSets()).hasSize(2);
    PatchSetApproval psa =
        Iterators.getOnlyElement(
            cd.currentApprovals().stream().filter(a -> !a.isLegacySubmit()).iterator());
    assertThat(psa.getPatchSetId().get()).isEqualTo(2);
    assertThat(psa.getLabel()).isEqualTo("Code-Review");
    assertThat(psa.getValue()).isEqualTo(2);
    assertThat(psa.isPostSubmit()).isFalse();
  }

  @Test
  public void voteOnAbandonedChange() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).abandon();
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("change is closed");
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.reject());
  }

  @Test
  public void voteNotAllowedWithoutPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("is restricted");
    gApi.changes().id(r.getChange().getId().get()).current().review(ReviewInput.approve());
  }

  @Test
  public void deleteDraft() throws Exception {
    PushOneCommit.Result r = createDraft();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).delete();
  }

  @Test
  public void cherryPick() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master%topic=someTopic");
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects().name(project.get()).branch(in.destination).create(new BranchInput());
    ChangeApi orig = gApi.changes().id(project.get() + "~master~" + r.getChangeId());

    assertThat(orig.get().messages).hasSize(1);
    ChangeApi cherry = orig.revision(r.getCommit().name()).cherryPick(in);

    Collection<ChangeMessageInfo> messages =
        gApi.changes().id(project.get() + "~master~" + r.getChangeId()).get().messages;
    assertThat(messages).hasSize(2);

    String cherryPickedRevision = cherry.get().currentRevision;
    String expectedMessage =
        String.format(
            "Patch Set 1: Cherry Picked\n\n"
                + "This patchset was cherry picked to branch %s as commit %s",
            in.destination, cherryPickedRevision);

    Iterator<ChangeMessageInfo> origIt = messages.iterator();
    origIt.next();
    assertThat(origIt.next().message).isEqualTo(expectedMessage);

    assertThat(cherry.get().messages).hasSize(1);
    Iterator<ChangeMessageInfo> cherryIt = cherry.get().messages.iterator();
    expectedMessage = "Patch Set 1: Cherry Picked from branch master.";
    assertThat(cherryIt.next().message).isEqualTo(expectedMessage);

    assertThat(cherry.get().subject).contains(in.message);
    assertThat(cherry.get().topic).isEqualTo("someTopic-foo");
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();
  }

  @Test
  public void cherryPickSetChangeId() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    String id = "Ideadbeefdeadbeefdeadbeefdeadbeefdeadbe3f";
    in.message = "it goes to foo branch\n\nChange-Id: " + id;

    gApi.projects().name(project.get()).branch(in.destination).create(new BranchInput());
    ChangeApi orig = gApi.changes().id(project.get() + "~master~" + r.getChangeId());

    assertThat(orig.get().messages).hasSize(1);
    ChangeApi cherry = orig.revision(r.getCommit().name()).cherryPick(in);

    ChangeInfo changeInfo = cherry.get();

    // The cherry-pick honors the ChangeId specified in the input message:
    RevisionInfo revInfo = changeInfo.revisions.get(changeInfo.currentRevision);
    assertThat(revInfo).isNotNull();
    assertThat(revInfo.commit.message).endsWith(id + "\n");
  }

  @Test
  public void cherryPickwithNoTopic() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects().name(project.get()).branch(in.destination).create(new BranchInput());
    ChangeApi orig = gApi.changes().id(project.get() + "~master~" + r.getChangeId());

    ChangeApi cherry = orig.revision(r.getCommit().name()).cherryPick(in);
    assertThat(cherry.get().topic).isNull();
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();
  }

  @Test
  public void cherryPickToSameBranch() throws Exception {
    PushOneCommit.Result r = createChange();
    CherryPickInput in = new CherryPickInput();
    in.destination = "master";
    in.message = "it generates a new patch set\n\nChange-Id: " + r.getChangeId();
    ChangeInfo cherryInfo =
        gApi.changes()
            .id(project.get() + "~master~" + r.getChangeId())
            .revision(r.getCommit().name())
            .cherryPick(in)
            .get();
    assertThat(cherryInfo.messages).hasSize(2);
    Iterator<ChangeMessageInfo> cherryIt = cherryInfo.messages.iterator();
    assertThat(cherryIt.next().message).isEqualTo("Uploaded patch set 1.");
    assertThat(cherryIt.next().message).isEqualTo("Uploaded patch set 2.");
  }

  @Test
  public void cherryPickToSameBranchWithRebase() throws Exception {
    // Push a new change, then merge it
    PushOneCommit.Result baseChange = createChange();
    String triplet = project.get() + "~master~" + baseChange.getChangeId();
    RevisionApi baseRevision = gApi.changes().id(triplet).current();
    baseRevision.review(ReviewInput.approve());
    baseRevision.submit();

    // Push a new change (change 1)
    PushOneCommit.Result r1 = createChange();

    // Push another new change (change 2)
    String subject = "Test change\n\nChange-Id: Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef";
    PushOneCommit push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, subject, "another_file.txt", "another content");
    PushOneCommit.Result r2 = push.to("refs/for/master");

    // Change 2's parent should be change 1
    assertThat(r2.getCommit().getParents()[0].name()).isEqualTo(r1.getCommit().name());

    // Cherry pick change 2 onto the same branch
    triplet = project.get() + "~master~" + r2.getChangeId();
    ChangeApi orig = gApi.changes().id(triplet);
    CherryPickInput in = new CherryPickInput();
    in.destination = "master";
    in.message = subject;
    ChangeApi cherry = orig.revision(r2.getCommit().name()).cherryPick(in);
    ChangeInfo cherryInfo = cherry.get();
    assertThat(cherryInfo.messages).hasSize(2);
    Iterator<ChangeMessageInfo> cherryIt = cherryInfo.messages.iterator();
    assertThat(cherryIt.next().message).isEqualTo("Uploaded patch set 1.");
    assertThat(cherryIt.next().message).isEqualTo("Uploaded patch set 2.");

    // Parent of change 2 should now be the change that was merged, i.e.
    // change 2 is rebased onto the head of the master branch.
    String newParent =
        cherryInfo.revisions.get(cherryInfo.currentRevision).commit.parents.get(0).commit;
    assertThat(newParent).isEqualTo(baseChange.getCommit().name());
  }

  @Test
  public void cherryPickIdenticalTree() throws Exception {
    PushOneCommit.Result r = createChange();
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects().name(project.get()).branch(in.destination).create(new BranchInput());
    ChangeApi orig = gApi.changes().id(project.get() + "~master~" + r.getChangeId());

    assertThat(orig.get().messages).hasSize(1);
    ChangeApi cherry = orig.revision(r.getCommit().name()).cherryPick(in);

    Collection<ChangeMessageInfo> messages =
        gApi.changes().id(project.get() + "~master~" + r.getChangeId()).get().messages;
    assertThat(messages).hasSize(2);

    assertThat(cherry.get().subject).contains(in.message);
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Cherry pick failed: identical tree");
    orig.revision(r.getCommit().name()).cherryPick(in);
  }

  @Test
  public void cherryPickConflict() throws Exception {
    PushOneCommit.Result r = createChange();
    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = "it goes to stable branch";
    gApi.projects().name(project.get()).branch(in.destination).create(new BranchInput());

    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME,
            "another content");
    push.to("refs/heads/foo");

    String triplet = project.get() + "~master~" + r.getChangeId();
    ChangeApi orig = gApi.changes().id(triplet);
    assertThat(orig.get().messages).hasSize(1);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Cherry pick failed: merge conflict");
    orig.revision(r.getCommit().name()).cherryPick(in);
  }

  @Test
  public void cherryPickToExistingChange() throws Exception {
    PushOneCommit.Result r1 =
        pushFactory
            .create(db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "a")
            .to("refs/for/master");
    String t1 = project.get() + "~master~" + r1.getChangeId();

    BranchInput bin = new BranchInput();
    bin.revision = r1.getCommit().getParent(0).name();
    gApi.projects().name(project.get()).branch("foo").create(bin);

    PushOneCommit.Result r2 =
        pushFactory
            .create(db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "b", r1.getChangeId())
            .to("refs/for/foo");
    String t2 = project.get() + "~foo~" + r2.getChangeId();
    gApi.changes().id(t2).abandon();

    CherryPickInput in = new CherryPickInput();
    in.destination = "foo";
    in.message = r1.getCommit().getFullMessage();
    try {
      gApi.changes().id(t1).current().cherryPick(in);
      fail();
    } catch (ResourceConflictException e) {
      assertThat(e.getMessage())
          .isEqualTo(
              "Cannot create new patch set of change "
                  + info(t2)._number
                  + " because it is abandoned");
    }

    gApi.changes().id(t2).restore();
    gApi.changes().id(t1).current().cherryPick(in);
    assertThat(get(t2).revisions).hasSize(2);
    assertThat(gApi.changes().id(t2).current().file(FILE_NAME).content().asString()).isEqualTo("a");
  }

  @Test
  public void cherryPickMergeRelativeToDefaultParent() throws Exception {
    String parent1FileName = "a.txt";
    String parent2FileName = "b.txt";
    PushOneCommit.Result mergeChangeResult =
        createCherryPickableMerge(parent1FileName, parent2FileName);

    String cherryPickBranchName = "branch_for_cherry_pick";
    createBranch(new Branch.NameKey(project, cherryPickBranchName));

    CherryPickInput cherryPickInput = new CherryPickInput();
    cherryPickInput.destination = cherryPickBranchName;
    cherryPickInput.message = "Cherry-pick a merge commit to another branch";

    ChangeInfo cherryPickedChangeInfo =
        gApi.changes()
            .id(mergeChangeResult.getChangeId())
            .current()
            .cherryPick(cherryPickInput)
            .get();

    Map<String, FileInfo> cherryPickedFilesByName =
        cherryPickedChangeInfo.revisions.get(cherryPickedChangeInfo.currentRevision).files;
    assertThat(cherryPickedFilesByName).containsKey(parent2FileName);
    assertThat(cherryPickedFilesByName).doesNotContainKey(parent1FileName);
  }

  @Test
  public void cherryPickMergeRelativeToSpecificParent() throws Exception {
    String parent1FileName = "a.txt";
    String parent2FileName = "b.txt";
    PushOneCommit.Result mergeChangeResult =
        createCherryPickableMerge(parent1FileName, parent2FileName);

    String cherryPickBranchName = "branch_for_cherry_pick";
    createBranch(new Branch.NameKey(project, cherryPickBranchName));

    CherryPickInput cherryPickInput = new CherryPickInput();
    cherryPickInput.destination = cherryPickBranchName;
    cherryPickInput.message = "Cherry-pick a merge commit to another branch";
    cherryPickInput.parent = 2;

    ChangeInfo cherryPickedChangeInfo =
        gApi.changes()
            .id(mergeChangeResult.getChangeId())
            .current()
            .cherryPick(cherryPickInput)
            .get();

    Map<String, FileInfo> cherryPickedFilesByName =
        cherryPickedChangeInfo.revisions.get(cherryPickedChangeInfo.currentRevision).files;
    assertThat(cherryPickedFilesByName).containsKey(parent1FileName);
    assertThat(cherryPickedFilesByName).doesNotContainKey(parent2FileName);
  }

  @Test
  public void cherryPickMergeUsingInvalidParent() throws Exception {
    String parent1FileName = "a.txt";
    String parent2FileName = "b.txt";
    PushOneCommit.Result mergeChangeResult =
        createCherryPickableMerge(parent1FileName, parent2FileName);

    String cherryPickBranchName = "branch_for_cherry_pick";
    createBranch(new Branch.NameKey(project, cherryPickBranchName));

    CherryPickInput cherryPickInput = new CherryPickInput();
    cherryPickInput.destination = cherryPickBranchName;
    cherryPickInput.message = "Cherry-pick a merge commit to another branch";
    cherryPickInput.parent = 0;

    exception.expect(BadRequestException.class);
    exception.expectMessage(
        "Cherry Pick: Parent 0 does not exist. Please specify a parent in range [1, 2].");
    gApi.changes().id(mergeChangeResult.getChangeId()).current().cherryPick(cherryPickInput);
  }

  @Test
  public void cherryPickMergeUsingNonExistentParent() throws Exception {
    String parent1FileName = "a.txt";
    String parent2FileName = "b.txt";
    PushOneCommit.Result mergeChangeResult =
        createCherryPickableMerge(parent1FileName, parent2FileName);

    String cherryPickBranchName = "branch_for_cherry_pick";
    createBranch(new Branch.NameKey(project, cherryPickBranchName));

    CherryPickInput cherryPickInput = new CherryPickInput();
    cherryPickInput.destination = cherryPickBranchName;
    cherryPickInput.message = "Cherry-pick a merge commit to another branch";
    cherryPickInput.parent = 3;

    exception.expect(BadRequestException.class);
    exception.expectMessage(
        "Cherry Pick: Parent 3 does not exist. Please specify a parent in range [1, 2].");
    gApi.changes().id(mergeChangeResult.getChangeId()).current().cherryPick(cherryPickInput);
  }

  @Test
  public void cherryPickNotify() throws Exception {
    createBranch(new NameKey(project, "branch-1"));
    createBranch(new NameKey(project, "branch-2"));
    createBranch(new NameKey(project, "branch-3"));

    // Creates a change for 'admin'.
    PushOneCommit.Result result = createChange();
    String changeId = project.get() + "~master~" + result.getChangeId();

    // 'user' cherry-picks the change to a new branch, the source change's author/committer('admin')
    // will be added as a reviewer of the newly created change.
    setApiUser(user);
    CherryPickInput input = new CherryPickInput();
    input.message = "it goes to a new branch";

    // Enable the notification. 'admin' as a reviewer should be notified.
    input.destination = "branch-1";
    input.notify = NotifyHandling.ALL;
    sender.clear();
    gApi.changes().id(changeId).current().cherryPick(input);
    assertNotifyCc(admin);

    // Disable the notification. 'admin' as a reviewer should not be notified any more.
    input.destination = "branch-2";
    input.notify = NotifyHandling.NONE;
    sender.clear();
    gApi.changes().id(changeId).current().cherryPick(input);
    assertThat(sender.getMessages()).hasSize(0);

    // Disable the notification. The user provided in the 'notifyDetails' should still be notified.
    TestAccount userToNotify = accountCreator.user2();
    input.destination = "branch-3";
    input.notify = NotifyHandling.NONE;
    input.notifyDetails =
        ImmutableMap.of(RecipientType.TO, new NotifyInfo(ImmutableList.of(userToNotify.email)));
    sender.clear();
    gApi.changes().id(changeId).current().cherryPick(input);
    assertNotifyTo(userToNotify);
  }

  @Test
  public void cherryPickToMergedChangeRevision() throws Exception {
    createBranch(new NameKey(project, "foo"));

    PushOneCommit.Result dstChange = createChange(testRepo, "foo", SUBJECT, "b.txt", "b", "t");
    dstChange.assertOkStatus();

    merge(dstChange);

    PushOneCommit.Result result = createChange(testRepo, "foo", SUBJECT, "b.txt", "c", "t");
    result.assertOkStatus();
    merge(result);

    PushOneCommit.Result srcChange = createChange();

    CherryPickInput input = new CherryPickInput();
    input.destination = "foo";
    input.base = dstChange.getCommit().name();
    input.message = srcChange.getCommit().getFullMessage();
    ChangeInfo changeInfo =
        gApi.changes().id(srcChange.getChangeId()).current().cherryPick(input).get();
    assertCherryPickResult(changeInfo, input, srcChange.getChangeId());
  }

  @Test
  public void cherryPickToOpenChangeRevision() throws Exception {
    createBranch(new NameKey(project, "foo"));

    PushOneCommit.Result dstChange = createChange(testRepo, "foo", SUBJECT, "b.txt", "b", "t");
    dstChange.assertOkStatus();

    PushOneCommit.Result srcChange = createChange();

    CherryPickInput input = new CherryPickInput();
    input.destination = "foo";
    input.base = dstChange.getCommit().name();
    input.message = srcChange.getCommit().getFullMessage();
    ChangeInfo changeInfo =
        gApi.changes().id(srcChange.getChangeId()).current().cherryPick(input).get();
    assertCherryPickResult(changeInfo, input, srcChange.getChangeId());
  }

  @Test
  public void cherryPickToNonVisibleChangeFails() throws Exception {
    createBranch(new NameKey(project, "foo"));

    PushOneCommit.Result dstChange = createChange(testRepo, "foo", SUBJECT, "b.txt", "b", "t");
    dstChange.assertOkStatus();

    gApi.changes().id(dstChange.getChangeId()).setPrivate(true, null);

    PushOneCommit.Result srcChange = createChange();

    CherryPickInput input = new CherryPickInput();
    input.destination = "foo";
    input.base = dstChange.getCommit().name();
    input.message = srcChange.getCommit().getFullMessage();

    setApiUser(user);
    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage(
        String.format("Commit %s does not exist on branch refs/heads/foo", input.base));
    gApi.changes().id(srcChange.getChangeId()).current().cherryPick(input).get();
  }

  @Test
  public void cherryPickToAbandonedChangeFails() throws Exception {
    PushOneCommit.Result change1 = createChange();
    PushOneCommit.Result change2 = createChange();
    gApi.changes().id(change2.getChangeId()).abandon();

    CherryPickInput input = new CherryPickInput();
    input.destination = "master";
    input.base = change2.getCommit().name();
    input.message = change1.getCommit().getFullMessage();

    exception.expect(ResourceConflictException.class);
    exception.expectMessage(
        String.format(
            "Change %s with commit %s is %s",
            change2.getChange().getId().get(), input.base, ChangeStatus.ABANDONED));
    gApi.changes().id(change1.getChangeId()).current().cherryPick(input);
  }

  @Test
  public void cherryPickWithInvalidBaseFails() throws Exception {
    PushOneCommit.Result change1 = createChange();

    CherryPickInput input = new CherryPickInput();
    input.destination = "master";
    input.base = "invalid-sha1";
    input.message = change1.getCommit().getFullMessage();

    exception.expect(BadRequestException.class);
    exception.expectMessage(String.format("Base %s doesn't represent a valid SHA-1", input.base));
    gApi.changes().id(change1.getChangeId()).current().cherryPick(input);
  }

  @Test
  public void cherryPickToCommitWithoutChangeId() throws Exception {
    RevCommit commit1 = createNewCommitWithoutChangeId("refs/heads/foo", "a.txt", "content 1");

    createNewCommitWithoutChangeId("refs/heads/foo", "a.txt", "content 2");

    PushOneCommit.Result srcChange = createChange("subject", "b.txt", "b");
    srcChange.assertOkStatus();

    CherryPickInput input = new CherryPickInput();
    input.destination = "foo";
    input.base = commit1.name();
    input.message = srcChange.getCommit().getFullMessage();
    ChangeInfo changeInfo =
        gApi.changes().id(srcChange.getChangeId()).current().cherryPick(input).get();
    assertCherryPickResult(changeInfo, input, srcChange.getChangeId());
  }

  @Test
  public void cherryPickWithOursAsMergeStrategy() throws Exception {
    RevCommit initialCommit = getHead(repo());
    PushOneCommit.Result change1 = createChange(SUBJECT, FILE_NAME, "a1");
    testRepo.reset(initialCommit);

    merge(createChange(SUBJECT, FILE_NAME, "a2"));
    createBranch(new NameKey(project, "foo"));

    // Set the merge strategy as 'ours', which will simply use the tree of the source commit.
    CherryPickInput input = new CherryPickInput();
    input.message = "it goes to a new branch";
    input.destination = "foo";
    input.strategy = "ours";
    ChangeApi cherry = gApi.changes().id(change1.getChangeId()).current().cherryPick(input);
    assertThat(cherry.current().file(FILE_NAME).content().asString()).isEqualTo("a1");
  }

  @Test
  public void cherryPickWithTheirsAsMergeStrategy() throws Exception {
    RevCommit initialCommit = getHead(repo());
    PushOneCommit.Result change1 = createChange(SUBJECT, FILE_NAME, "a1");
    testRepo.reset(initialCommit);

    merge(createChange(SUBJECT, FILE_NAME, "a2"));
    createBranch(new NameKey(project, "foo"));

    // Set the merge strategy as 'theirs', which will simply use the tree of the tip commit on the
    // destination branch.
    CherryPickInput input = new CherryPickInput();
    input.message = "it goes to a new branch";
    input.destination = "foo";
    input.strategy = "theirs";
    ChangeApi cherry = gApi.changes().id(change1.getChangeId()).current().cherryPick(input);
    assertThat(cherry.current().file(FILE_NAME).content().asString()).isEqualTo("a2");
  }

  @Test
  public void cherryPickWithDefaultMergeStrategy() throws Exception {
    RevCommit initialCommit = getHead(repo());
    PushOneCommit.Result change1 = createChange(SUBJECT, FILE_NAME, "a1");
    testRepo.reset(initialCommit);

    merge(createChange(SUBJECT, FILE_NAME, "a2"));
    createBranch(new NameKey(project, "foo"));

    // Use the default merge strategy (ThreeWayMerger).
    CherryPickInput input = new CherryPickInput();
    input.message = "it goes to a new branch";
    input.destination = "foo";
    input.strategy = null;
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Cherry pick failed: merge conflict");
    gApi.changes().id(change1.getChangeId()).current().cherryPick(input);
  }

  @Test
  public void canRebase() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    merge(r1);

    push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r2 = push.to("refs/for/master");
    boolean canRebase =
        gApi.changes().id(r2.getChangeId()).revision(r2.getCommit().name()).canRebase();
    assertThat(canRebase).isFalse();
    merge(r2);

    testRepo.reset(r1.getCommit());
    push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r3 = push.to("refs/for/master");

    canRebase = gApi.changes().id(r3.getChangeId()).revision(r3.getCommit().name()).canRebase();
    assertThat(canRebase).isTrue();
  }

  @Test
  public void setUnsetReviewedFlag() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/for/master");

    gApi.changes().id(r.getChangeId()).current().setReviewed(PushOneCommit.FILE_NAME, true);

    assertThat(Iterables.getOnlyElement(gApi.changes().id(r.getChangeId()).current().reviewed()))
        .isEqualTo(PushOneCommit.FILE_NAME);

    gApi.changes().id(r.getChangeId()).current().setReviewed(PushOneCommit.FILE_NAME, false);

    assertThat(gApi.changes().id(r.getChangeId()).current().reviewed()).isEmpty();
  }

  @Test
  public void mergeable() throws Exception {
    ObjectId initial = repo().exactRef(HEAD).getLeaf().getObjectId();

    PushOneCommit push1 =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME,
            "push 1 content");

    PushOneCommit.Result r1 = push1.to("refs/for/master");
    assertMergeable(r1.getChangeId(), true);
    merge(r1);

    // Reset HEAD to initial so the new change is a merge conflict.
    RefUpdate ru = repo().updateRef(HEAD);
    ru.setNewObjectId(initial);
    assertThat(ru.forceUpdate()).isEqualTo(RefUpdate.Result.FORCED);

    PushOneCommit push2 =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME,
            "push 2 content");
    PushOneCommit.Result r2 = push2.to("refs/for/master");
    assertMergeable(r2.getChangeId(), false);
    // TODO(dborowitz): Test for other-branches.
  }

  @Test
  public void files() throws Exception {
    PushOneCommit.Result r = createChange();
    Map<String, FileInfo> files =
        gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).files();
    assertThat(files).hasSize(2);
    assertThat(Iterables.all(files.keySet(), f -> f.matches(FILE_NAME + '|' + COMMIT_MSG)))
        .isTrue();
  }

  @Test
  public void filesOnMergeCommitChange() throws Exception {
    PushOneCommit.Result r = createMergeCommitChange("refs/for/master");

    // list files against auto-merge
    assertThat(gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).files().keySet())
        .containsExactly(COMMIT_MSG, MERGE_LIST, "foo", "bar");

    // list files against parent 1
    assertThat(gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).files(1).keySet())
        .containsExactly(COMMIT_MSG, MERGE_LIST, "bar");

    // list files against parent 2
    assertThat(gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).files(2).keySet())
        .containsExactly(COMMIT_MSG, MERGE_LIST, "foo");
  }

  @Test
  public void description() throws Exception {
    PushOneCommit.Result r = createChange();
    assertDescription(r, "");
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).description("test");
    assertDescription(r, "test");
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).description("");
    assertDescription(r, "");
  }

  @Test
  public void setDescriptionNotAllowedWithoutPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    assertDescription(r, "");
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("edit description not permitted");
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).description("test");
  }

  @Test
  public void setDescriptionAllowedWithPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    assertDescription(r, "");
    grant(project, "refs/heads/master", Permission.OWNER, false, REGISTERED_USERS);
    setApiUser(user);
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).description("test");
    assertDescription(r, "test");
  }

  private void assertDescription(PushOneCommit.Result r, String expected) throws Exception {
    assertThat(gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).description())
        .isEqualTo(expected);
  }

  @Test
  public void content() throws Exception {
    PushOneCommit.Result r = createChange();
    assertContent(r, FILE_NAME, FILE_CONTENT);
    assertContent(r, COMMIT_MSG, r.getCommit().getFullMessage());
  }

  @Test
  public void contentType() throws Exception {
    PushOneCommit.Result r = createChange();

    String endPoint =
        "/changes/"
            + r.getChangeId()
            + "/revisions/"
            + r.getCommit().name()
            + "/files/"
            + FILE_NAME
            + "/content";
    RestResponse response = adminRestSession.head(endPoint);
    response.assertOK();
    assertThat(response.getContentType()).startsWith("text/plain");
    assertThat(response.hasContent()).isFalse();
  }

  @Test
  public void commit() throws Exception {
    WebLinkInfo expectedWebLinkInfo = new WebLinkInfo("foo", "imageUrl", "url");
    patchSetLinks.add(
        new PatchSetWebLink() {
          @Override
          public WebLinkInfo getPatchSetWebLink(String projectName, String commit) {
            return expectedWebLinkInfo;
          }
        });

    PushOneCommit.Result r = createChange();
    RevCommit c = r.getCommit();

    CommitInfo commitInfo = gApi.changes().id(r.getChangeId()).current().commit(false);
    assertThat(commitInfo.commit).isEqualTo(c.name());
    assertPersonIdent(commitInfo.author, c.getAuthorIdent());
    assertPersonIdent(commitInfo.committer, c.getCommitterIdent());
    assertThat(commitInfo.message).isEqualTo(c.getFullMessage());
    assertThat(commitInfo.subject).isEqualTo(c.getShortMessage());
    assertThat(commitInfo.parents).hasSize(1);
    assertThat(Iterables.getOnlyElement(commitInfo.parents).commit)
        .isEqualTo(c.getParent(0).name());
    assertThat(commitInfo.webLinks).isNull();

    commitInfo = gApi.changes().id(r.getChangeId()).current().commit(true);
    assertThat(commitInfo.webLinks).hasSize(1);
    WebLinkInfo webLinkInfo = Iterables.getOnlyElement(commitInfo.webLinks);
    assertThat(webLinkInfo.name).isEqualTo(expectedWebLinkInfo.name);
    assertThat(webLinkInfo.imageUrl).isEqualTo(expectedWebLinkInfo.imageUrl);
    assertThat(webLinkInfo.url).isEqualTo(expectedWebLinkInfo.url);
    assertThat(webLinkInfo.target).isEqualTo(expectedWebLinkInfo.target);
  }

  private void assertPersonIdent(GitPerson gitPerson, PersonIdent expectedIdent) {
    assertThat(gitPerson.name).isEqualTo(expectedIdent.getName());
    assertThat(gitPerson.email).isEqualTo(expectedIdent.getEmailAddress());
    assertThat(gitPerson.date).isEqualTo(new Timestamp(expectedIdent.getWhen().getTime()));
    assertThat(gitPerson.tz).isEqualTo(expectedIdent.getTimeZoneOffset());
  }

  private void assertMergeable(String id, boolean expected) throws Exception {
    MergeableInfo m = gApi.changes().id(id).current().mergeable();
    assertThat(m.mergeable).isEqualTo(expected);
    assertThat(m.submitType).isEqualTo(SubmitType.MERGE_IF_NECESSARY);
    assertThat(m.mergeableInto).isNull();
    ChangeInfo c = gApi.changes().id(id).info();
    assertThat(c.mergeable).isEqualTo(expected);
  }

  @Test
  public void drafts() throws Exception {
    PushOneCommit.Result r = createChange();
    DraftInput in = new DraftInput();
    in.line = 1;
    in.message = "nit: trailing whitespace";
    in.path = FILE_NAME;

    DraftApi draftApi =
        gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).createDraft(in);
    assertThat(draftApi.get().message).isEqualTo(in.message);
    assertThat(
            gApi.changes()
                .id(r.getChangeId())
                .revision(r.getCommit().name())
                .draft(draftApi.get().id)
                .get()
                .message)
        .isEqualTo(in.message);
    assertThat(gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).drafts())
        .hasSize(1);

    in.message = "good catch!";
    assertThat(
            gApi.changes()
                .id(r.getChangeId())
                .revision(r.getCommit().name())
                .draft(draftApi.get().id)
                .update(in)
                .message)
        .isEqualTo(in.message);

    assertThat(
            gApi.changes()
                .id(r.getChangeId())
                .revision(r.getCommit().name())
                .draft(draftApi.get().id)
                .get()
                .author
                .email)
        .isEqualTo(admin.email);

    draftApi.delete();
    assertThat(gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).drafts())
        .isEmpty();
  }

  @Test
  public void comments() throws Exception {
    PushOneCommit.Result r = createChange();
    CommentInput in = new CommentInput();
    in.line = 1;
    in.message = "nit: trailing whitespace";
    in.path = FILE_NAME;
    ReviewInput reviewInput = new ReviewInput();
    Map<String, List<CommentInput>> comments = new HashMap<>();
    comments.put(FILE_NAME, Collections.singletonList(in));
    reviewInput.comments = comments;
    reviewInput.message = "comment test";
    gApi.changes().id(r.getChangeId()).current().review(reviewInput);

    Map<String, List<CommentInfo>> out =
        gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).comments();
    assertThat(out).hasSize(1);
    CommentInfo comment = Iterables.getOnlyElement(out.get(FILE_NAME));
    assertThat(comment.message).isEqualTo(in.message);
    assertThat(comment.author.email).isEqualTo(admin.email);
    assertThat(comment.path).isNull();

    List<CommentInfo> list =
        gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).commentsAsList();
    assertThat(list).hasSize(1);

    CommentInfo comment2 = list.get(0);
    assertThat(comment2.path).isEqualTo(FILE_NAME);
    assertThat(comment2.line).isEqualTo(comment.line);
    assertThat(comment2.message).isEqualTo(comment.message);
    assertThat(comment2.author.email).isEqualTo(comment.author.email);

    assertThat(
            gApi.changes()
                .id(r.getChangeId())
                .revision(r.getCommit().name())
                .comment(comment.id)
                .get()
                .message)
        .isEqualTo(in.message);
  }

  @Test
  public void patch() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeApi changeApi = gApi.changes().id(r.getChangeId());
    BinaryResult bin = changeApi.revision(r.getCommit().name()).patch();
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    bin.writeTo(os);
    String res = new String(os.toByteArray(), UTF_8);
    ChangeInfo change = changeApi.get();
    RevisionInfo rev = change.revisions.get(change.currentRevision);
    DateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US);
    String date = df.format(rev.commit.author.date);
    assertThat(res).isEqualTo(String.format(PATCH, r.getCommit().name(), date, r.getChangeId()));
  }

  @Test
  public void patchWithPath() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeApi changeApi = gApi.changes().id(r.getChangeId());
    BinaryResult bin = changeApi.revision(r.getCommit().name()).patch(FILE_NAME);
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    bin.writeTo(os);
    String res = new String(os.toByteArray(), UTF_8);
    assertThat(res).isEqualTo(PATCH_FILE_ONLY);

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("File not found: nonexistent-file.");
    changeApi.revision(r.getCommit().name()).patch("nonexistent-file");
  }

  @Test
  public void actions() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(current(r).actions().keySet())
        .containsExactly("cherrypick", "description", "rebase");

    current(r).review(ReviewInput.approve());
    assertThat(current(r).actions().keySet())
        .containsExactly("submit", "cherrypick", "description", "rebase");

    current(r).submit();
    assertThat(current(r).actions().keySet()).containsExactly("cherrypick");
  }

  @Test
  public void actionsETag() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();

    String oldETag = checkETag(getRevisionActions, r2, null);
    current(r2).review(ReviewInput.approve());
    oldETag = checkETag(getRevisionActions, r2, oldETag);

    // Dependent change is included in ETag.
    current(r1).review(ReviewInput.approve());
    oldETag = checkETag(getRevisionActions, r2, oldETag);

    current(r2).submit();
    oldETag = checkETag(getRevisionActions, r2, oldETag);
  }

  @Test
  public void deleteVoteOnNonCurrentPatchSet() throws Exception {
    PushOneCommit.Result r = createChange(); // patch set 1
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());

    // patch set 2
    amendChange(r.getChangeId());

    // code-review
    setApiUser(user);
    recommend(r.getChangeId());

    // check if it's blocked to delete a vote on a non-current patch set.
    setApiUser(admin);
    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("Cannot access on non-current patch set");
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().getName())
        .reviewer(user.getId().toString())
        .deleteVote("Code-Review");
  }

  @Test
  public void deleteVoteOnCurrentPatchSet() throws Exception {
    PushOneCommit.Result r = createChange(); // patch set 1
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());

    // patch set 2
    amendChange(r.getChangeId());

    // code-review
    setApiUser(user);
    recommend(r.getChangeId());

    setApiUser(admin);
    gApi.changes()
        .id(r.getChangeId())
        .current()
        .reviewer(user.getId().toString())
        .deleteVote("Code-Review");

    Map<String, Short> m =
        gApi.changes().id(r.getChangeId()).current().reviewer(user.getId().toString()).votes();

    assertThat(m).containsExactly("Code-Review", Short.valueOf((short) 0));

    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    ChangeMessageInfo message = Iterables.getLast(c.messages);
    assertThat(message.author._accountId).isEqualTo(admin.getId().get());
    assertThat(message.message).isEqualTo("Removed Code-Review+1 by User <user@example.com>\n");
    assertThat(getReviewers(c.reviewers.get(REVIEWER)))
        .containsExactlyElementsIn(ImmutableSet.of(admin.getId(), user.getId()));
  }

  private static void assertCherryPickResult(
      ChangeInfo changeInfo, CherryPickInput input, String srcChangeId) throws Exception {
    assertThat(changeInfo.changeId).isEqualTo(srcChangeId);
    assertThat(changeInfo.revisions.keySet()).containsExactly(changeInfo.currentRevision);
    RevisionInfo revisionInfo = changeInfo.revisions.get(changeInfo.currentRevision);
    assertThat(revisionInfo.commit.message).isEqualTo(input.message);
    assertThat(revisionInfo.commit.parents).hasSize(1);
    assertThat(revisionInfo.commit.parents.get(0).commit).isEqualTo(input.base);
  }

  private PushOneCommit.Result updateChange(PushOneCommit.Result r, String content)
      throws Exception {
    PushOneCommit push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, "test commit", "a.txt", content, r.getChangeId());
    return push.to("refs/for/master");
  }

  private PushOneCommit.Result createDraft() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    return push.to("refs/drafts/master");
  }

  private RevisionApi current(PushOneCommit.Result r) throws Exception {
    return gApi.changes().id(r.getChangeId()).current();
  }

  private String checkETag(ETagView<RevisionResource> view, PushOneCommit.Result r, String oldETag)
      throws Exception {
    String eTag = view.getETag(parseRevisionResource(r));
    assertThat(eTag).isNotEqualTo(oldETag);
    return eTag;
  }

  private PushOneCommit.Result createCherryPickableMerge(
      String parent1FileName, String parent2FileName) throws Exception {
    RevCommit initialCommit = getHead(repo());

    String branchAName = "branchA";
    createBranch(new Branch.NameKey(project, branchAName));
    String branchBName = "branchB";
    createBranch(new Branch.NameKey(project, branchBName));

    PushOneCommit.Result changeAResult =
        pushFactory
            .create(db, admin.getIdent(), testRepo, "change a", parent1FileName, "Content of a")
            .to("refs/for/" + branchAName);

    testRepo.reset(initialCommit);
    PushOneCommit.Result changeBResult =
        pushFactory
            .create(db, admin.getIdent(), testRepo, "change b", parent2FileName, "Content of b")
            .to("refs/for/" + branchBName);

    PushOneCommit pushableMergeCommit =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            "merge",
            ImmutableMap.of(parent1FileName, "Content of a", parent2FileName, "Content of b"));
    pushableMergeCommit.setParents(
        ImmutableList.of(changeAResult.getCommit(), changeBResult.getCommit()));
    PushOneCommit.Result mergeChangeResult = pushableMergeCommit.to("refs/for/" + branchAName);
    mergeChangeResult.assertOkStatus();
    return mergeChangeResult;
  }

  private ApprovalInfo getApproval(String changeId, String label) throws Exception {
    ChangeInfo info = gApi.changes().id(changeId).get(EnumSet.of(DETAILED_LABELS));
    LabelInfo li = info.labels.get(label);
    assertThat(li).isNotNull();
    int accountId = atrScope.get().getUser().getAccountId().get();
    return li.all.stream().filter(a -> a._accountId == accountId).findFirst().get();
  }

  private static Iterable<Account.Id> getReviewers(Collection<AccountInfo> r) {
    return Iterables.transform(r, a -> new Account.Id(a._accountId));
  }
}
