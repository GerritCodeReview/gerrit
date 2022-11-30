// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_COMMIT;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.TestLabels.labelBuilder;
import static com.google.gerrit.server.project.testing.TestLabels.value;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.testing.TestLabels;
import com.google.inject.Inject;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;

/**
 * Test to verify that {@link com.google.gerrit.server.restapi.change.PostReviewOp} copies approvals
 * to follow-up patch sets if possible.
 */
public class CopyApprovalsToFollowUpPatchSetsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Before
  public void setup() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      // Overwrite "Code-Review" label that is inherited from All-Projects.
      // This way changes to the "Code Review" label don't affect other tests.
      LabelType.Builder codeReview =
          labelBuilder(
              LabelId.CODE_REVIEW,
              value(2, "Looks good to me, approved"),
              value(1, "Looks good to me, but someone else must approve"),
              value(0, "No score"),
              value(-1, "I would prefer this is not submitted as is"),
              value(-2, "This shall not be submitted"));
      u.getConfig().upsertLabelType(codeReview.build());

      LabelType.Builder verified =
          labelBuilder(
              LabelId.VERIFIED, value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
      u.getConfig().upsertLabelType(verified.build());

      u.save();
    }
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabel(TestLabels.codeReview().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .add(
            allowLabel(TestLabels.verified().getName())
                .ref(RefNames.REFS_HEADS + "*")
                .group(REGISTERED_USERS)
                .range(-1, 1))
        .update();
  }

  /**
   * Tests that new approvals on an outdated patch set are not copied to the follow-up patch set if
   * the new approvals are not copyable because no matching copy rule is configured.
   */
  @Test
  public void newApprovals_notCopied_copyingNotEnabled() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Vote on the first patch set and verify the change messages.
    vote(admin, changeId, patchSet1.number(), 2, 1);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: Code-Review+2 Verified+1");
    vote(user, changeId, patchSet1.number(), -2, -1);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: Code-Review-2 Verified-1");

    // Verify that no votes have been copied to the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, 0);
    assertCurrentVotes(c, user, 0, 0);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 2, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, -2, -1, /* expectedToBeCopied= */ false);
    assertNoApprovals(patchSet2.id(), admin);
    assertNoApprovals(patchSet2.id(), user);
  }

  /**
   * Tests that new approvals on an outdated patch set are copied to the follow-up patch set if the
   * follow-up patch set has no regular votes (non-copied votes that override copied votes).
   */
  @Test
  public void newApprovals_copied_noCurrentVote() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Vote on the first patch set and verify the change messages.
    vote(admin, changeId, patchSet1.number(), 2, 1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: Code-Review+2 Verified+1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Code-Review+2 has been copied to patch set 2 (copy condition: \"is:ANY\").\n"
                + "* Verified+1 has been copied to patch set 2 (copy condition: \"is:ANY\")."));
    vote(user, changeId, patchSet1.number(), -2, -1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: Code-Review-2 Verified-1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Code-Review-2 has been copied to patch set 2 (copy condition: \"is:ANY\").\n"
                + "* Verified-1 has been copied to patch set 2 (copy condition: \"is:ANY\")."));

    // Verify that the votes have been copied to the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 2, 1);
    assertCurrentVotes(c, user, -2, -1);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 2, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, -2, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 2, 1, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet2.id(), user, -2, -1, /* expectedToBeCopied= */ true);
  }

  /**
   * Tests that new approvals on an outdated patch set are not copied to the follow-up patch set if
   * the follow-up patch set has regular votes (non-copied votes that override copied votes).
   */
  @Test
  public void newApprovals_notCopied_currentVote() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Vote on the current patch set.
    vote(admin, changeId, patchSet2.number(), 2, 1);
    vote(user, changeId, patchSet2.number(), -2, -1);

    // Vote on the first patch set and verify change message.
    vote(admin, changeId, patchSet1.number(), 1, -1);
    assertLastChangeMessage(
        r.getChangeId(), String.format("Patch Set 1: Code-Review+1 Verified-1"));
    vote(user, changeId, patchSet1.number(), -1, 1);
    assertLastChangeMessage(
        r.getChangeId(), String.format("Patch Set 1: Code-Review-1 Verified+1"));

    // Verify that the votes have not been copied to the current patch set (since a current vote
    // already exists).
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 2, 1);
    assertCurrentVotes(c, user, -2, -1);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 1, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, -1, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 2, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), user, -2, -1, /* expectedToBeCopied= */ false);
  }

  /**
   * Tests that new approvals on an outdated patch set are not copied to the follow-up patch set if
   * the follow-up patch set has deletions of regular votes (non-copied deletion votes that override
   * copied votes).
   */
  @Test
  public void newApprovals_notCopied_currentDeletedVote() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Vote on the current patch set.
    vote(admin, changeId, patchSet2.number(), 2, 1);
    vote(user, changeId, patchSet2.number(), -2, -1);

    // Delete the votes on the current patch set.
    deleteCurrentVotes(admin, changeId);
    deleteCurrentVotes(user, changeId);

    // Vote on the first patch set and verify the change messages.
    vote(admin, changeId, patchSet1.number(), 1, -1);
    assertLastChangeMessage(
        r.getChangeId(), String.format("Patch Set 1: Code-Review+1 Verified-1"));
    vote(user, changeId, patchSet1.number(), -1, 1);
    assertLastChangeMessage(
        r.getChangeId(), String.format("Patch Set 1: Code-Review-1 Verified+1"));

    // Verify that the votes have not been copied to the current patch set (since a deletion vote
    // already exists on the current patch set).
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, 0);
    assertCurrentVotes(c, user, 0, 0);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 1, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, -1, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), user, 0, 0, /* expectedToBeCopied= */ false);
  }

  /**
   * Tests that updated approvals on an outdated patch set are not copied to the follow-up patch set
   * if the updated approvals are not copyable because no matching copy rule is configured.
   */
  @Test
  public void updatedApprovals_notCopied_copyingNotEnabled() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    // Vote on the first patch set.
    vote(admin, changeId, patchSet1.number(), 1, 1);
    vote(user, changeId, patchSet1.number(), -2, -1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Update the votes on the first patch set and verify the change messages.
    vote(admin, changeId, patchSet1.number(), 2, -1);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: Code-Review+2 Verified-1");
    vote(user, changeId, patchSet1.number(), -1, 1);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: Code-Review-1 Verified+1");

    // Verify that no votes have been copied to the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, 0);
    assertCurrentVotes(c, user, 0, 0);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 2, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, -1, 1, /* expectedToBeCopied= */ false);
    assertNoApprovals(patchSet2.id(), admin);
    assertNoApprovals(patchSet2.id(), user);
  }

  /**
   * Tests that if updated approvals on an outdated patch set are not copied to the follow-up patch
   * set that existing copies of the approvals on the follow-up patch sets are unset.
   */
  @Test
  public void updatedApprovals_notCopied_copyingNotEnabled_unsetsCopiedApprovals()
      throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:1 OR is:2"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:MAX"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    // Vote on the first patch set with votes that are copied.
    vote(admin, changeId, patchSet1.number(), 1, 1);
    vote(user, changeId, patchSet1.number(), 2, 1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Verify that the votes have been copied to the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 1, 1);
    assertCurrentVotes(c, user, 2, 1);

    // Update the votes on the first patch set with votes that are not copied and verify the change
    // messages.
    vote(admin, changeId, patchSet1.number(), -1, -1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: Code-Review-1 Verified-1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Copied Code-Review vote has been removed from patch set 2 (was Code-Review+1)"
                + " since the new Code-Review-1 vote is not copyable"
                + " (copy condition: \"is:1 OR is:2\").\n"
                + "* Copied Verified vote has been removed from patch set 2 (was Verified+1)"
                + " since the new Verified-1 vote is not copyable (copy condition: \"is:MAX\")."));
    vote(user, changeId, patchSet1.number(), -2, -1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: Code-Review-2 Verified-1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Copied Code-Review vote has been removed from patch set 2 (was Code-Review+2)"
                + " since the new Code-Review-2 vote is not copyable"
                + " (copy condition: \"is:1 OR is:2\").\n"
                + "* Copied Verified vote has been removed from patch set 2 (was Verified+1)"
                + " since the new Verified-1 vote is not copyable (copy condition: \"is:MAX\")."));

    // Verify that the copied votes on the current patch set have been unset.
    c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, 0);
    assertCurrentVotes(c, user, 0, 0);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, -1, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, -2, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 0, 0, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet2.id(), user, 0, 0, /* expectedToBeCopied= */ true);
  }

  /**
   * Tests that updated approvals on an outdated patch set are copied to the follow-up patch set if
   * the follow-up patch set has no regular votes (non-copied votes that override copied votes).
   */
  @Test
  public void updatedApprovals_copied_noCurrentVote() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:1 OR is:2"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:MAX"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    // Vote on the first patch set with votes that are not copied.
    vote(admin, changeId, patchSet1.number(), -2, -1);
    vote(user, changeId, patchSet1.number(), -1, -1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Verify that the votes have not been copied to the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, 0);
    assertCurrentVotes(c, user, 0, 0);

    // Update the votes on the first patch set with votes that are copied and verify the change
    // messages.
    vote(admin, changeId, patchSet1.number(), 2, 1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: Code-Review+2 Verified+1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Code-Review+2 has been copied to patch set 2"
                + " (copy condition: \"is:1 OR is:2\").\n"
                + "* Verified+1 has been copied to patch set 2 (copy condition: \"is:MAX\")."));
    vote(user, changeId, patchSet1.number(), 1, 1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: Code-Review+1 Verified+1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Code-Review+1 has been copied to patch set 2"
                + " (copy condition: \"is:1 OR is:2\").\n"
                + "* Verified+1 has been copied to patch set 2 (copy condition: \"is:MAX\")."));

    // Verify that the votes have been copied to the current patch set.
    c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 2, 1);
    assertCurrentVotes(c, user, 1, 1);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 2, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, 1, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 2, 1, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet2.id(), user, 1, 1, /* expectedToBeCopied= */ true);
  }

  /**
   * Tests that updated approvals on an outdated patch set are not copied to the follow-up patch set
   * if the follow-up patch set has regular votes (non-copied votes that override copied votes).
   */
  @Test
  public void updatedApprovals_notCopied_currentVote() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    // Vote on the first patch set.
    vote(admin, changeId, patchSet1.number(), 1, -1);
    vote(user, changeId, patchSet1.number(), -1, 1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Vote on the current patch set (overrides the copied votes).
    vote(admin, changeId, patchSet2.number(), 2, 1);
    vote(user, changeId, patchSet2.number(), -2, -1);

    // Update the votes on the first patch set and verify the change messages.
    vote(admin, changeId, patchSet1.number(), -1, 1);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: Code-Review-1 Verified+1");
    vote(user, changeId, patchSet1.number(), 1, -1);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: Code-Review+1 Verified-1");

    // Verify that the votes have not been copied to the current patch set (since a current vote
    // already exists).
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 2, 1);
    assertCurrentVotes(c, user, -2, -1);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, -1, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, 1, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 2, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), user, -2, -1, /* expectedToBeCopied= */ false);
  }

  /**
   * Tests that updated approvals on an outdated patch set are not copied to the follow-up patch set
   * if the follow-up patch set has deletions of regular votes (non-copied deletion votes that
   * override copied votes).
   */
  @Test
  public void updatedApprovals_notCopied_currentDeletedVote() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    // Vote on the first patch set.
    vote(admin, changeId, patchSet1.number(), 1, -1);
    vote(user, changeId, patchSet1.number(), -1, 1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Vote on the current patch set (overrides the copied approvals).
    vote(admin, changeId, patchSet2.number(), 2, 1);
    vote(user, changeId, patchSet2.number(), -2, -1);

    // Delete the votes on the current patch set.
    deleteCurrentVotes(admin, changeId);
    deleteCurrentVotes(user, changeId);

    // Update the votes on the first patch set and verify the change messages.
    vote(admin, changeId, patchSet1.number(), -1, 1);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: Code-Review-1 Verified+1");
    vote(user, changeId, patchSet1.number(), 1, -1);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: Code-Review+1 Verified-1");

    // Verify that the votes have not been copied to the current patch set (since a deletion vote
    // already exists on the current patch set).
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, 0);
    assertCurrentVotes(c, user, 0, 0);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, -1, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, 1, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), user, 0, 0, /* expectedToBeCopied= */ false);
  }

  /**
   * Tests that updated approvals on an outdated patch set are copied to the follow-up patch set if
   * the follow-up patch set has copied votes (the copied votes on the follow-up patch set are
   * updated).
   */
  @Test
  public void updatedApprovals_copied_currentCopiedVote() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    // Vote on the first patch set.
    vote(admin, changeId, patchSet1.number(), -2, -1);
    vote(user, changeId, patchSet1.number(), 2, 1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Verify that the votes have been copied to the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, -2, -1);
    assertCurrentVotes(c, user, 2, 1);

    // Update the votes on the first patch set with votes that are copied and verify the change
    // messages.
    vote(admin, changeId, patchSet1.number(), 2, 1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: Code-Review+2 Verified+1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Code-Review+2 has been copied to patch set 2 (was Code-Review-2)"
                + " (copy condition: \"is:ANY\").\n"
                + "* Verified+1 has been copied to patch set 2 (was Verified-1)"
                + " (copy condition: \"is:ANY\")."));
    vote(user, changeId, patchSet1.number(), -2, -1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: Code-Review-2 Verified-1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Code-Review-2 has been copied to patch set 2 (was Code-Review+2)"
                + " (copy condition: \"is:ANY\").\n"
                + "* Verified-1 has been copied to patch set 2 (was Verified+1)"
                + " (copy condition: \"is:ANY\")."));

    // Verify that the votes have been copied to the current patch set.
    c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 2, 1);
    assertCurrentVotes(c, user, -2, -1);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 2, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, -2, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 2, 1, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet2.id(), user, -2, -1, /* expectedToBeCopied= */ true);
  }

  /**
   * Tests that deleted approvals on an outdated patch set are not copied to the follow-up patch set
   * if the deleted approvals are not copyable because no matching copy rule is configured.
   */
  @Test
  public void deletedApprovals_notCopied_copyingNotEnabled() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    // Vote on the first patch set.
    vote(admin, changeId, patchSet1.number(), 2, 1);
    vote(user, changeId, patchSet1.number(), -2, -1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Vote on the current patch set.
    vote(admin, changeId, patchSet2.number(), -2, -1);
    vote(user, changeId, patchSet2.number(), 2, 1);

    // Delete the votes on the first patch set and verify the change messages.
    vote(admin, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: -Code-Review -Verified");
    vote(user, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: -Code-Review -Verified");

    // Verify that the vote deletions have not been copied to the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, -2, -1);
    assertCurrentVotes(c, user, 2, 1);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, -2, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), user, 2, 1, /* expectedToBeCopied= */ false);
  }

  /**
   * Tests that deleted approvals on an outdated patch set are not copied to the follow-up patch set
   * if the follow-up patch set has no regular votes (non-copied votes that override copied votes).
   */
  @Test
  public void deletedApprovals_notCopied_noCurrentVote() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:0 OR is:1 OR is:2"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:0 OR is:1"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    // Vote on the first patch set with votes that are not copied.
    vote(admin, changeId, patchSet1.number(), -2, -1);
    vote(user, changeId, patchSet1.number(), -1, -1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Verify that the votes have not been copied to the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, 0);
    assertCurrentVotes(c, user, 0, 0);

    // Delete the votes on the first patch set and verify the change messages.
    vote(admin, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: -Code-Review -Verified");
    vote(user, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: -Code-Review -Verified");

    // Verify that there are still no votes on the current patch set.
    c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, 0);
    assertCurrentVotes(c, user, 0, 0);

    // Verify the approvals in NoteDb (the deletion votes have not been copied).
    assertApprovals(patchSet1.id(), admin, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, 0, 0, /* expectedToBeCopied= */ false);
    assertNoApprovals(patchSet2.id(), admin);
    assertNoApprovals(patchSet2.id(), user);
  }

  /**
   * Tests that deleted approvals on an outdated patch set are not copied to the follow-up patch set
   * if the follow-up patch set has regular votes (non-copied votes that override copied votes).
   */
  @Test
  public void deletedApprovals_notCopied_currentVote() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    // Vote on the first patch set.
    vote(admin, changeId, patchSet1.number(), 1, -1);
    vote(user, changeId, patchSet1.number(), -1, 1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Vote on the current patch set (overrides the copied votes).
    vote(admin, changeId, patchSet2.number(), 2, 1);
    vote(user, changeId, patchSet2.number(), -2, -1);

    // Delete the votes on the first patch set and verify the change messages.
    vote(admin, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: -Code-Review -Verified");
    vote(user, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: -Code-Review -Verified");

    // Verify that the vote deletions have not been copied to the current patch set (since a current
    // vote already exists).
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 2, 1);
    assertCurrentVotes(c, user, -2, -1);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 2, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), user, -2, -1, /* expectedToBeCopied= */ false);
  }

  /**
   * Tests that deleted approvals on an outdated patch set are not copied to the follow-up patch set
   * if the follow-up patch set has deletions of regular votes (non-copied deletion votes that
   * override copied votes).
   */
  @Test
  public void deletedApprovals_notCopied_currentDeletedVote() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    // Vote on the first patch set.
    vote(admin, changeId, patchSet1.number(), 1, -1);
    vote(user, changeId, patchSet1.number(), -1, 1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Vote on the current patch set (overrides the copied approvals).
    vote(admin, changeId, patchSet2.number(), 2, 1);
    vote(user, changeId, patchSet2.number(), -2, -1);

    // Delete the votes on the current patch set.
    deleteCurrentVotes(admin, changeId);
    deleteCurrentVotes(user, changeId);

    // Delete the votes on the first patch set and verify the change messages.
    vote(admin, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: -Code-Review -Verified");
    vote(user, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(r.getChangeId(), "Patch Set 1: -Code-Review -Verified");

    // Verify that there are still no votes on the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, 0);
    assertCurrentVotes(c, user, 0, 0);

    // Verify the approvals in NoteDb (the deletion votes have not been copied).
    assertApprovals(patchSet1.id(), admin, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), user, 0, 0, /* expectedToBeCopied= */ false);
  }

  /**
   * Tests that deleted approvals on an outdated patch set are copied to the follow-up patch set if
   * the follow-up patch set has copied votes (the copied votes on the follow-up patch set are
   * removed).
   */
  @Test
  public void deletedApprovals_copied_currentCopiedVote() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    // Vote on the first patch set.
    vote(admin, changeId, patchSet1.number(), -2, -1);
    vote(user, changeId, patchSet1.number(), 2, 1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    // Verify that the votes have been copied to the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, -2, -1);
    assertCurrentVotes(c, user, 2, 1);

    // Delete the votes on the first patch set and verify the change messages.
    vote(admin, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: -Code-Review -Verified\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Copied Code-Review vote has been removed from patch set 2 (was Code-Review-2)"
                + " since the new Code-Review=0 vote is not copyable"
                + " (copy condition: \"is:ANY\").\n"
                + "* Copied Verified vote has been removed from patch set 2 (was Verified-1)"
                + " since the new Verified=0 vote is not copyable (copy condition: \"is:ANY\")."));
    vote(user, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: -Code-Review -Verified\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Copied Code-Review vote has been removed from patch set 2 (was Code-Review+2)"
                + " since the new Code-Review=0 vote is not copyable"
                + " (copy condition: \"is:ANY\").\n"
                + "* Copied Verified vote has been removed from patch set 2 (was Verified+1)"
                + " since the new Verified=0 vote is not copyable (copy condition: \"is:ANY\")."));

    // Verify that the vote deletions have been copied to the current patch set.
    c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, 0);
    assertCurrentVotes(c, user, 0, 0);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 0, 0, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet2.id(), user, 0, 0, /* expectedToBeCopied= */ true);
  }

  /** Tests that new approvals on an outdated patch set are copied to all follow-up patch sets. */
  @Test
  public void copyNewApprovalAcrossMultipleFollowUpPatchSets() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet3 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet4 = r.getChange().currentPatchSet();

    // Vote on the first patch set and verify the change messages.
    vote(admin, changeId, patchSet1.number(), 2, 1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: Code-Review+2 Verified+1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Code-Review+2 has been copied to patch set 2, 3, 4"
                + " (copy condition: \"is:ANY\").\n"
                + "* Verified+1 has been copied to patch set 2, 3, 4"
                + " (copy condition: \"is:ANY\")."));
    vote(user, changeId, patchSet1.number(), -2, -1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: Code-Review-2 Verified-1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Code-Review-2 has been copied to patch set 2, 3, 4"
                + " (copy condition: \"is:ANY\").\n"
                + "* Verified-1 has been copied to patch set 2, 3, 4"
                + " (copy condition: \"is:ANY\")."));

    // Verify that votes have been copied to the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 2, 1);
    assertCurrentVotes(c, user, -2, -1);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 2, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, -2, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 2, 1, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet2.id(), user, -2, -1, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet3.id(), admin, 2, 1, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet3.id(), user, -2, -1, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet4.id(), admin, 2, 1, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet4.id(), user, -2, -1, /* expectedToBeCopied= */ true);
  }

  /**
   * Tests that new approvals on an outdated patch set are copied to all follow-up patch sets, but
   * not across patch sets have non-copied votes.
   */
  @Test
  public void
      copyNewApprovalAcrossMultipleFollowUpPatchSets_stopOnFirstFollowUpPatchSetToWhichTheVoteIsNotCopyable()
          throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:1 OR is:2"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet3 = r.getChange().currentPatchSet();

    // Vote on the third patch set with non-copyable Code-Review votes and copyable Verified votes.
    vote(admin, changeId, patchSet3.number(), -2, -1);
    vote(user, changeId, patchSet3.number(), -1, -1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet4 = r.getChange().currentPatchSet();

    // Verify that the Verified votes from patch set 3 have been copied to the current patch
    // set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, -1);
    assertCurrentVotes(c, user, 0, -1);

    // Vote on the first patch set with copyable votes and verify the change messages.
    vote(admin, changeId, patchSet1.number(), 2, 1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: Code-Review+2 Verified+1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Code-Review+2 has been copied to patch set 2 "
                + "(copy condition: \"is:1 OR is:2\").\n"
                + "* Verified+1 has been copied to patch set 2 (copy condition: \"is:ANY\")."));
    vote(user, changeId, patchSet1.number(), 1, 1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: Code-Review+1 Verified+1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Code-Review+1 has been copied to patch set 2"
                + " (copy condition: \"is:1 OR is:2\").\n"
                + "* Verified+1 has been copied to patch set 2 (copy condition: \"is:ANY\")."));

    // Verify that votes have been not copied to the current patch set.
    c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, -1);
    assertCurrentVotes(c, user, 0, -1);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 2, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, 1, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 2, 1, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet2.id(), user, 1, 1, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet3.id(), admin, -2, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet3.id(), user, -1, -1, /* expectedToBeCopied= */ false);
    assertNoApproval(patchSet4.id(), admin, LabelId.CODE_REVIEW);
    assertApproval(patchSet4.id(), admin, LabelId.VERIFIED, -1, /* expectedToBeCopied= */ true);
    assertNoApproval(patchSet4.id(), user, LabelId.CODE_REVIEW);
    assertApproval(patchSet4.id(), user, LabelId.VERIFIED, -1, /* expectedToBeCopied= */ true);
  }

  /**
   * Tests that deleted approvals on an outdated patch set are copied to all follow-up patch sets.
   */
  @Test
  public void copyApprovalDeletionAcrossMultipleFollowUpPatchSets() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    // Vote on the first patch set.
    vote(admin, changeId, patchSet1.number(), 2, 1);
    vote(user, changeId, patchSet1.number(), -2, -1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet3 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet4 = r.getChange().currentPatchSet();

    // Verify that votes have been copied to the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 2, 1);
    assertCurrentVotes(c, user, -2, -1);

    // Delete the votes on the first patch set and verify the change messages.
    vote(admin, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: -Code-Review -Verified\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Copied Code-Review vote has been removed from patch set"
                + " 2 (was Code-Review+2), 3 (was Code-Review+2), 4 (was Code-Review+2)"
                + " since the new Code-Review=0 vote is not copyable"
                + " (copy condition: \"is:ANY\").\n"
                + "* Copied Verified vote has been removed from patch set"
                + " 2 (was Verified+1), 3 (was Verified+1), 4 (was Verified+1)"
                + " since the new Verified=0 vote is not copyable (copy condition: \"is:ANY\")."));
    vote(user, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: -Code-Review -Verified\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Copied Code-Review vote has been removed from patch set"
                + " 2 (was Code-Review-2), 3 (was Code-Review-2), 4 (was Code-Review-2)"
                + " since the new Code-Review=0 vote is not copyable"
                + " (copy condition: \"is:ANY\").\n"
                + "* Copied Verified vote has been removed from patch set"
                + " 2 (was Verified-1), 3 (was Verified-1), 4 (was Verified-1)"
                + " since the new Verified=0 vote is not copyable (copy condition: \"is:ANY\")."));

    // Verify that the votes has been copied to the current patch set.
    c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, 0);
    assertCurrentVotes(c, user, 0, 0);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 0, 0, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet2.id(), user, 0, 0, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet3.id(), admin, 0, 0, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet3.id(), user, 0, 0, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet4.id(), admin, 0, 0, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet4.id(), user, 0, 0, /* expectedToBeCopied= */ true);
  }

  /**
   * Tests that deleted approvals on an outdated patch set are copied to all follow-up patch sets,
   * but not across patch sets have non-copied votes.
   */
  @Test
  public void
      copyApprovalDeletionAcrossMultipleFollowUpPatchSets_stopOnFirstFollowUpPatchSetToWhichTheVoteIsNotCopyable()
          throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:1 OR is:2"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    // Vote on the first patch set with copyable votes.
    vote(admin, changeId, patchSet1.number(), 2, 1);
    vote(user, changeId, patchSet1.number(), 1, -1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet3 = r.getChange().currentPatchSet();

    // Vote on the third patch set with non-copyable Code-Review votes and copyable Verified votes.
    vote(admin, changeId, patchSet3.number(), -2, -1);
    vote(user, changeId, patchSet3.number(), -1, -1);

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet4 = r.getChange().currentPatchSet();

    // Verify that the Verified votes from patch set 3 have been copied to the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, -1);
    assertCurrentVotes(c, user, 0, -1);

    // Delete the votes on the first patch set.
    vote(admin, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: -Code-Review -Verified\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Copied Code-Review vote has been removed from patch set 2 (was Code-Review+2)"
                + " since the new Code-Review=0 vote is not copyable"
                + " (copy condition: \"is:1 OR is:2\").\n"
                + "* Copied Verified vote has been removed from patch set 2 (was Verified+1)"
                + " since the new Verified=0 vote is not copyable (copy condition: \"is:ANY\")."));
    vote(user, changeId, patchSet1.number(), 0, 0);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 1: -Code-Review -Verified\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Copied Code-Review vote has been removed from patch set 2 (was Code-Review+1)"
                + " since the new Code-Review=0 vote is not copyable"
                + " (copy condition: \"is:1 OR is:2\").\n"
                + "* Copied Verified vote has been removed from patch set 2 (was Verified-1)"
                + " since the new Verified=0 vote is not copyable (copy condition: \"is:ANY\")."));

    // Verify that the vote deletions have been not copied to the current patch set.
    c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 0, -1);
    assertCurrentVotes(c, user, 0, -1);

    // Verify the approvals in NoteDb.
    assertApprovals(patchSet1.id(), admin, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet1.id(), user, 0, 0, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet2.id(), admin, 0, 0, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet2.id(), user, 0, 0, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet3.id(), admin, -2, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet3.id(), user, -1, -1, /* expectedToBeCopied= */ false);
    assertNoApproval(patchSet4.id(), admin, LabelId.CODE_REVIEW);
    assertApproval(patchSet4.id(), admin, LabelId.VERIFIED, -1, /* expectedToBeCopied= */ true);
    assertNoApproval(patchSet4.id(), user, LabelId.CODE_REVIEW);
    assertApproval(patchSet4.id(), user, LabelId.VERIFIED, -1, /* expectedToBeCopied= */ true);
  }

  /** Tests that new approvals on an outdated patch set are not copied to predecessor patch sets. */
  @Test
  public void notCopyToPredecessorPatchSets() throws Exception {
    updateCodeReviewLabel(b -> b.setCopyCondition("is:ANY"));
    updateVerifiedLabel(b -> b.setCopyCondition("is:ANY"));

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    PatchSet patchSet1 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet2 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet3 = r.getChange().currentPatchSet();

    r = amendChange(changeId);
    r.assertOkStatus();
    PatchSet patchSet4 = r.getChange().currentPatchSet();

    // Vote on the third patch set and verify the change messages.
    vote(admin, changeId, patchSet3.number(), 2, 1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 3: Code-Review+2 Verified+1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Code-Review+2 has been copied to patch set 4 (copy condition: \"is:ANY\").\n"
                + "* Verified+1 has been copied to patch set 4 (copy condition: \"is:ANY\")."));
    vote(user, changeId, patchSet3.number(), -2, -1);
    assertLastChangeMessage(
        r.getChangeId(),
        String.format(
            "Patch Set 3: Code-Review-2 Verified-1\n\n"
                + "Copied votes on follow-up patch sets have been updated:\n"
                + "* Code-Review-2 has been copied to patch set 4 (copy condition: \"is:ANY\").\n"
                + "* Verified-1 has been copied to patch set 4 (copy condition: \"is:ANY\")."));

    // Verify that votes have been copied to the current patch set.
    ChangeInfo c = detailedChange(changeId);
    assertCurrentVotes(c, admin, 2, 1);
    assertCurrentVotes(c, user, -2, -1);

    // Verify the approvals in NoteDb.
    assertNoApprovals(patchSet1.id(), admin);
    assertNoApprovals(patchSet1.id(), user);
    assertNoApprovals(patchSet2.id(), admin);
    assertNoApprovals(patchSet2.id(), user);
    assertApprovals(patchSet3.id(), admin, 2, 1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet3.id(), user, -2, -1, /* expectedToBeCopied= */ false);
    assertApprovals(patchSet4.id(), admin, 2, 1, /* expectedToBeCopied= */ true);
    assertApprovals(patchSet4.id(), user, -2, -1, /* expectedToBeCopied= */ true);
  }

  private void updateCodeReviewLabel(Consumer<LabelType.Builder> update) throws Exception {
    updateLabel(LabelId.CODE_REVIEW, update);
  }

  private void updateVerifiedLabel(Consumer<LabelType.Builder> update) throws Exception {
    updateLabel(LabelId.VERIFIED, update);
  }

  private void updateLabel(String labelName, Consumer<LabelType.Builder> update) throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateLabelType(labelName, update);
      u.save();
    }
  }

  private ChangeInfo detailedChange(String changeId) throws Exception {
    return gApi.changes().id(changeId).get(DETAILED_LABELS, CURRENT_REVISION, CURRENT_COMMIT);
  }

  private void vote(
      TestAccount user, String changeId, int psNum, int codeReviewVote, int verifiedVote)
      throws Exception {
    requestScopeOperations.setApiUser(user.id());
    ReviewInput in =
        new ReviewInput()
            .label(LabelId.CODE_REVIEW, codeReviewVote)
            .label(LabelId.VERIFIED, verifiedVote);
    gApi.changes().id(changeId).revision(psNum).review(in);
  }

  private void deleteCurrentVotes(TestAccount user, String changeId) throws Exception {
    requestScopeOperations.setApiUser(user.id());
    deleteCurrentVote(user, changeId, LabelId.CODE_REVIEW);
    deleteCurrentVote(user, changeId, LabelId.VERIFIED);
  }

  private void deleteCurrentVote(TestAccount user, String changeId, String label) throws Exception {
    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId).reviewer(user.id().toString()).deleteVote(label);
  }

  private void assertCurrentVotes(
      ChangeInfo c, TestAccount user, int codeReviewVote, int verifiedVote) {
    assertCurrentVote(c, user, LabelId.CODE_REVIEW, codeReviewVote);
    assertCurrentVote(c, user, LabelId.VERIFIED, verifiedVote);
  }

  private void assertCurrentVote(ChangeInfo c, TestAccount user, String label, int expectedVote) {
    Integer vote = 0;
    if (c.labels.get(label) != null && c.labels.get(label).all != null) {
      for (ApprovalInfo approval : c.labels.get(label).all) {
        if (approval._accountId == user.id().get()) {
          vote = approval.value;
          break;
        }
      }
    }

    assertWithMessage("label = " + label).that(vote).isEqualTo(expectedVote);
  }

  private void assertNoApprovals(PatchSet.Id patchSetId, TestAccount user) {
    assertNoApproval(patchSetId, user, LabelId.CODE_REVIEW);
    assertNoApproval(patchSetId, user, LabelId.VERIFIED);
  }

  private void assertNoApproval(PatchSet.Id patchSetId, TestAccount user, String label) {
    ChangeNotes notes = notesFactory.create(project, patchSetId.changeId());
    Optional<PatchSetApproval> patchSetApproval =
        notes.getApprovals().all().get(patchSetId).stream()
            .filter(psa -> psa.accountId().equals(user.id()) && psa.label().equals(label))
            .findAny();
    assertThat(patchSetApproval).isEmpty();
  }

  private void assertApprovals(
      PatchSet.Id patchSetId,
      TestAccount user,
      int expectedCodeReviewVote,
      int expectedVerifiedVote,
      boolean expectedToBeCopied) {
    assertApproval(
        patchSetId, user, LabelId.CODE_REVIEW, expectedCodeReviewVote, expectedToBeCopied);
    assertApproval(patchSetId, user, LabelId.VERIFIED, expectedVerifiedVote, expectedToBeCopied);
  }

  private void assertApproval(
      PatchSet.Id patchSetId,
      TestAccount user,
      String label,
      int expectedVote,
      boolean expectedToBeCopied) {
    ChangeNotes notes = notesFactory.create(project, patchSetId.changeId());
    Optional<PatchSetApproval> patchSetApproval =
        notes.getApprovals().all().get(patchSetId).stream()
            .filter(psa -> psa.accountId().equals(user.id()) && psa.label().equals(label))
            .findAny();
    assertThat(patchSetApproval).isPresent();
    assertThat(patchSetApproval.get().value()).isEqualTo((short) expectedVote);
    assertThat(patchSetApproval.get().copied()).isEqualTo(expectedToBeCopied);
  }

  private void assertLastChangeMessage(String changeId, String expectedMessage)
      throws RestApiException {
    assertThat(Iterables.getLast(gApi.changes().id(changeId).get().messages).message)
        .isEqualTo(expectedMessage);
  }
}
