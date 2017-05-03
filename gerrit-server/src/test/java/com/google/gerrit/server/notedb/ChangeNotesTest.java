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

package com.google.gerrit.server.notedb;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;
import static com.google.gerrit.reviewdb.client.RefNames.refsDraftComments;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REMOVED;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;
import static org.junit.Assert.fail;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.CommentRange;
import com.google.gerrit.reviewdb.client.PatchLineComment.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.gerrit.server.util.RequestId;
import com.google.gerrit.testutil.TestChanges;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Test;

public class ChangeNotesTest extends AbstractChangeNotesTest {
  @Inject private DraftCommentNotes.Factory draftNotesFactory;

  @Inject private ChangeNoteUtil noteUtil;

  @Inject private @GerritServerId String serverId;

  @Test
  public void tagChangeMessage() throws Exception {
    String tag = "jenkins";
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("verification from jenkins");
    update.setTag(tag);
    update.commit();

    ChangeNotes notes = newNotes(c);

    assertThat(notes.getChangeMessages()).hasSize(1);
    assertThat(notes.getChangeMessages().get(0).getTag()).isEqualTo(tag);
  }

  @Test
  public void patchSetDescription() throws Exception {
    String description = "descriptive";
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setPsDescription(description);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getCurrentPatchSet().getDescription()).isEqualTo(description);

    description = "new, now more descriptive!";
    update = newUpdate(c, changeOwner);
    update.setPsDescription(description);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getCurrentPatchSet().getDescription()).isEqualTo(description);
  }

  @Test
  public void tagInlineComments() throws Exception {
    String tag = "jenkins";
    Change c = newChange();
    RevCommit commit = tr.commit().message("PS2").create();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putComment(
        Status.PUBLISHED,
        newComment(
            c.currentPatchSetId(),
            "a.txt",
            "uuid1",
            new CommentRange(1, 2, 3, 4),
            1,
            changeOwner,
            null,
            TimeUtil.nowTs(),
            "Comment",
            (short) 1,
            commit.name(),
            false));
    update.setTag(tag);
    update.commit();

    ChangeNotes notes = newNotes(c);

    ImmutableListMultimap<RevId, Comment> comments = notes.getComments();
    assertThat(comments).hasSize(1);
    assertThat(comments.entries().asList().get(0).getValue().tag).isEqualTo(tag);
  }

  @Test
  public void tagApprovals() throws Exception {
    String tag1 = "jenkins";
    String tag2 = "ip";
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Verified", (short) -1);
    update.setTag(tag1);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.putApproval("Verified", (short) 1);
    update.setTag(tag2);
    update.commit();

    ChangeNotes notes = newNotes(c);

    ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals = notes.getApprovals();
    assertThat(approvals).hasSize(1);
    assertThat(approvals.entries().asList().get(0).getValue().getTag()).isEqualTo(tag2);
  }

  @Test
  public void multipleTags() throws Exception {
    String ipTag = "ip";
    String coverageTag = "coverage";
    String integrationTag = "integration";
    Change c = newChange();

    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Verified", (short) -1);
    update.setChangeMessage("integration verification");
    update.setTag(integrationTag);
    update.commit();

    RevCommit commit = tr.commit().message("PS2").create();
    update = newUpdate(c, changeOwner);
    update.putComment(
        Status.PUBLISHED,
        newComment(
            c.currentPatchSetId(),
            "a.txt",
            "uuid1",
            new CommentRange(1, 2, 3, 4),
            1,
            changeOwner,
            null,
            TimeUtil.nowTs(),
            "Comment",
            (short) 1,
            commit.name(),
            false));
    update.setChangeMessage("coverage verification");
    update.setTag(coverageTag);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.setChangeMessage("ip clear");
    update.setTag(ipTag);
    update.commit();

    ChangeNotes notes = newNotes(c);

    ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals = notes.getApprovals();
    assertThat(approvals).hasSize(1);
    PatchSetApproval approval = approvals.entries().asList().get(0).getValue();
    assertThat(approval.getTag()).isEqualTo(integrationTag);
    assertThat(approval.getValue()).isEqualTo(-1);

    ImmutableListMultimap<RevId, Comment> comments = notes.getComments();
    assertThat(comments).hasSize(1);
    assertThat(comments.entries().asList().get(0).getValue().tag).isEqualTo(coverageTag);

    ImmutableList<ChangeMessage> messages = notes.getChangeMessages();
    assertThat(messages).hasSize(3);
    assertThat(messages.get(0).getTag()).isEqualTo(integrationTag);
    assertThat(messages.get(1).getTag()).isEqualTo(coverageTag);
    assertThat(messages.get(2).getTag()).isEqualTo(ipTag);
  }

  @Test
  public void approvalsOnePatchSet() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Verified", (short) 1);
    update.putApproval("Code-Review", (short) -1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getApprovals().keySet()).containsExactly(c.currentPatchSetId());
    List<PatchSetApproval> psas = notes.getApprovals().get(c.currentPatchSetId());
    assertThat(psas).hasSize(2);

    assertThat(psas.get(0).getPatchSetId()).isEqualTo(c.currentPatchSetId());
    assertThat(psas.get(0).getAccountId().get()).isEqualTo(1);
    assertThat(psas.get(0).getLabel()).isEqualTo("Code-Review");
    assertThat(psas.get(0).getValue()).isEqualTo((short) -1);
    assertThat(psas.get(0).getGranted()).isEqualTo(truncate(after(c, 2000)));

    assertThat(psas.get(1).getPatchSetId()).isEqualTo(c.currentPatchSetId());
    assertThat(psas.get(1).getAccountId().get()).isEqualTo(1);
    assertThat(psas.get(1).getLabel()).isEqualTo("Verified");
    assertThat(psas.get(1).getValue()).isEqualTo((short) 1);
    assertThat(psas.get(1).getGranted()).isEqualTo(psas.get(0).getGranted());
  }

  @Test
  public void approvalsMultiplePatchSets() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) -1);
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    incrementPatchSet(c);
    update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.commit();
    PatchSet.Id ps2 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, PatchSetApproval> psas = notes.getApprovals();
    assertThat(psas).hasSize(2);

    PatchSetApproval psa1 = Iterables.getOnlyElement(psas.get(ps1));
    assertThat(psa1.getPatchSetId()).isEqualTo(ps1);
    assertThat(psa1.getAccountId().get()).isEqualTo(1);
    assertThat(psa1.getLabel()).isEqualTo("Code-Review");
    assertThat(psa1.getValue()).isEqualTo((short) -1);
    assertThat(psa1.getGranted()).isEqualTo(truncate(after(c, 2000)));

    PatchSetApproval psa2 = Iterables.getOnlyElement(psas.get(ps2));
    assertThat(psa2.getPatchSetId()).isEqualTo(ps2);
    assertThat(psa2.getAccountId().get()).isEqualTo(1);
    assertThat(psa2.getLabel()).isEqualTo("Code-Review");
    assertThat(psa2.getValue()).isEqualTo((short) +1);
    assertThat(psa2.getGranted()).isEqualTo(truncate(after(c, 4000)));
  }

  @Test
  public void approvalsMultipleApprovals() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) -1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    PatchSetApproval psa =
        Iterables.getOnlyElement(notes.getApprovals().get(c.currentPatchSetId()));
    assertThat(psa.getLabel()).isEqualTo("Code-Review");
    assertThat(psa.getValue()).isEqualTo((short) -1);

    update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    notes = newNotes(c);
    psa = Iterables.getOnlyElement(notes.getApprovals().get(c.currentPatchSetId()));
    assertThat(psa.getLabel()).isEqualTo("Code-Review");
    assertThat(psa.getValue()).isEqualTo((short) 1);
  }

  @Test
  public void approvalsMultipleUsers() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) -1);
    update.commit();

    update = newUpdate(c, otherUser);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getApprovals().keySet()).containsExactly(c.currentPatchSetId());
    List<PatchSetApproval> psas = notes.getApprovals().get(c.currentPatchSetId());
    assertThat(psas).hasSize(2);

    assertThat(psas.get(0).getPatchSetId()).isEqualTo(c.currentPatchSetId());
    assertThat(psas.get(0).getAccountId().get()).isEqualTo(1);
    assertThat(psas.get(0).getLabel()).isEqualTo("Code-Review");
    assertThat(psas.get(0).getValue()).isEqualTo((short) -1);
    assertThat(psas.get(0).getGranted()).isEqualTo(truncate(after(c, 2000)));

    assertThat(psas.get(1).getPatchSetId()).isEqualTo(c.currentPatchSetId());
    assertThat(psas.get(1).getAccountId().get()).isEqualTo(2);
    assertThat(psas.get(1).getLabel()).isEqualTo("Code-Review");
    assertThat(psas.get(1).getValue()).isEqualTo((short) 1);
    assertThat(psas.get(1).getGranted()).isEqualTo(truncate(after(c, 3000)));
  }

  @Test
  public void approvalsTombstone() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Not-For-Long", (short) 1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    PatchSetApproval psa =
        Iterables.getOnlyElement(notes.getApprovals().get(c.currentPatchSetId()));
    assertThat(psa.getAccountId().get()).isEqualTo(1);
    assertThat(psa.getLabel()).isEqualTo("Not-For-Long");
    assertThat(psa.getValue()).isEqualTo((short) 1);

    update = newUpdate(c, changeOwner);
    update.removeApproval("Not-For-Long");
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getApprovals())
        .containsExactlyEntriesIn(
            ImmutableListMultimap.of(
                psa.getPatchSetId(),
                new PatchSetApproval(psa.getKey(), (short) 0, update.getWhen())));
  }

  @Test
  public void removeOtherUsersApprovals() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    update.putApproval("Not-For-Long", (short) 1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    PatchSetApproval psa =
        Iterables.getOnlyElement(notes.getApprovals().get(c.currentPatchSetId()));
    assertThat(psa.getAccountId()).isEqualTo(otherUserId);
    assertThat(psa.getLabel()).isEqualTo("Not-For-Long");
    assertThat(psa.getValue()).isEqualTo((short) 1);

    update = newUpdate(c, changeOwner);
    update.removeApprovalFor(otherUserId, "Not-For-Long");
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getApprovals())
        .containsExactlyEntriesIn(
            ImmutableListMultimap.of(
                psa.getPatchSetId(),
                new PatchSetApproval(psa.getKey(), (short) 0, update.getWhen())));

    // Add back approval on same label.
    update = newUpdate(c, otherUser);
    update.putApproval("Not-For-Long", (short) 2);
    update.commit();

    notes = newNotes(c);
    psa = Iterables.getOnlyElement(notes.getApprovals().get(c.currentPatchSetId()));
    assertThat(psa.getAccountId()).isEqualTo(otherUserId);
    assertThat(psa.getLabel()).isEqualTo("Not-For-Long");
    assertThat(psa.getValue()).isEqualTo((short) 2);
  }

  @Test
  public void putOtherUsersApprovals() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.putApprovalFor(otherUser.getAccountId(), "Code-Review", (short) -1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    List<PatchSetApproval> approvals =
        ReviewDbUtil.intKeyOrdering()
            .onResultOf(PatchSetApproval::getAccountId)
            .sortedCopy(notes.getApprovals().get(c.currentPatchSetId()));
    assertThat(approvals).hasSize(2);

    assertThat(approvals.get(0).getAccountId()).isEqualTo(changeOwner.getAccountId());
    assertThat(approvals.get(0).getLabel()).isEqualTo("Code-Review");
    assertThat(approvals.get(0).getValue()).isEqualTo((short) 1);

    assertThat(approvals.get(1).getAccountId()).isEqualTo(otherUser.getAccountId());
    assertThat(approvals.get(1).getLabel()).isEqualTo("Code-Review");
    assertThat(approvals.get(1).getValue()).isEqualTo((short) -1);
  }

  @Test
  public void approvalsPostSubmit() throws Exception {
    Change c = newChange();
    RequestId submissionId = RequestId.forChange(c);
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.putApproval("Verified", (short) 1);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.merge(
        submissionId,
        ImmutableList.of(
            submitRecord(
                "NOT_READY",
                null,
                submitLabel("Verified", "OK", changeOwner.getAccountId()),
                submitLabel("Code-Review", "NEED", null))));
    update.commit();

    update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    List<PatchSetApproval> approvals = Lists.newArrayList(notes.getApprovals().values());
    assertThat(approvals).hasSize(2);
    assertThat(approvals.get(0).getLabel()).isEqualTo("Verified");
    assertThat(approvals.get(0).getValue()).isEqualTo((short) 1);
    assertThat(approvals.get(0).isPostSubmit()).isFalse();
    assertThat(approvals.get(1).getLabel()).isEqualTo("Code-Review");
    assertThat(approvals.get(1).getValue()).isEqualTo((short) 2);
    assertThat(approvals.get(1).isPostSubmit()).isTrue();
  }

  @Test
  public void approvalsDuringSubmit() throws Exception {
    Change c = newChange();
    RequestId submissionId = RequestId.forChange(c);
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.putApproval("Verified", (short) 1);
    update.commit();

    Account.Id ownerId = changeOwner.getAccountId();
    Account.Id otherId = otherUser.getAccountId();
    update = newUpdate(c, otherUser);
    update.merge(
        submissionId,
        ImmutableList.of(
            submitRecord(
                "NOT_READY",
                null,
                submitLabel("Verified", "OK", ownerId),
                submitLabel("Code-Review", "NEED", null))));
    update.putApproval("Other-Label", (short) 1);
    update.putApprovalFor(ownerId, "Code-Review", (short) 2);
    update.commit();

    update = newUpdate(c, otherUser);
    update.putApproval("Other-Label", (short) 2);
    update.commit();

    ChangeNotes notes = newNotes(c);

    List<PatchSetApproval> approvals = Lists.newArrayList(notes.getApprovals().values());
    assertThat(approvals).hasSize(3);
    assertThat(approvals.get(0).getAccountId()).isEqualTo(ownerId);
    assertThat(approvals.get(0).getLabel()).isEqualTo("Verified");
    assertThat(approvals.get(0).getValue()).isEqualTo(1);
    assertThat(approvals.get(0).isPostSubmit()).isFalse();
    assertThat(approvals.get(1).getAccountId()).isEqualTo(ownerId);
    assertThat(approvals.get(1).getLabel()).isEqualTo("Code-Review");
    assertThat(approvals.get(1).getValue()).isEqualTo(2);
    assertThat(approvals.get(1).isPostSubmit()).isFalse(); // During submit.
    assertThat(approvals.get(2).getAccountId()).isEqualTo(otherId);
    assertThat(approvals.get(2).getLabel()).isEqualTo("Other-Label");
    assertThat(approvals.get(2).getValue()).isEqualTo(2);
    assertThat(approvals.get(2).isPostSubmit()).isTrue();
  }

  @Test
  public void multipleReviewers() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.putReviewer(otherUser.getAccount().getId(), REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Timestamp ts = new Timestamp(update.getWhen().getTime());
    assertThat(notes.getReviewers())
        .isEqualTo(
            ReviewerSet.fromTable(
                ImmutableTable.<ReviewerStateInternal, Account.Id, Timestamp>builder()
                    .put(REVIEWER, new Account.Id(1), ts)
                    .put(REVIEWER, new Account.Id(2), ts)
                    .build()));
  }

  @Test
  public void reviewerTypes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.putReviewer(otherUser.getAccount().getId(), CC);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Timestamp ts = new Timestamp(update.getWhen().getTime());
    assertThat(notes.getReviewers())
        .isEqualTo(
            ReviewerSet.fromTable(
                ImmutableTable.<ReviewerStateInternal, Account.Id, Timestamp>builder()
                    .put(REVIEWER, new Account.Id(1), ts)
                    .put(CC, new Account.Id(2), ts)
                    .build()));
  }

  @Test
  public void oneReviewerMultipleTypes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(otherUser.getAccount().getId(), REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    Timestamp ts = new Timestamp(update.getWhen().getTime());
    assertThat(notes.getReviewers())
        .isEqualTo(ReviewerSet.fromTable(ImmutableTable.of(REVIEWER, new Account.Id(2), ts)));

    update = newUpdate(c, otherUser);
    update.putReviewer(otherUser.getAccount().getId(), CC);
    update.commit();

    notes = newNotes(c);
    ts = new Timestamp(update.getWhen().getTime());
    assertThat(notes.getReviewers())
        .isEqualTo(ReviewerSet.fromTable(ImmutableTable.of(CC, new Account.Id(2), ts)));
  }

  @Test
  public void removeReviewer() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(otherUser.getAccount().getId(), REVIEWER);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    update = newUpdate(c, otherUser);
    update.putApproval("Code-Review", (short) 1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    List<PatchSetApproval> psas = notes.getApprovals().get(c.currentPatchSetId());
    assertThat(psas).hasSize(2);
    assertThat(psas.get(0).getAccountId()).isEqualTo(changeOwner.getAccount().getId());
    assertThat(psas.get(1).getAccountId()).isEqualTo(otherUser.getAccount().getId());

    update = newUpdate(c, changeOwner);
    update.removeReviewer(otherUser.getAccount().getId());
    update.commit();

    notes = newNotes(c);
    psas = notes.getApprovals().get(c.currentPatchSetId());
    assertThat(psas).hasSize(1);
    assertThat(psas.get(0).getAccountId()).isEqualTo(changeOwner.getAccount().getId());
  }

  @Test
  public void submitRecords() throws Exception {
    Change c = newChange();
    RequestId submissionId = RequestId.forChange(c);
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setSubjectForCommit("Submit patch set 1");

    update.merge(
        submissionId,
        ImmutableList.of(
            submitRecord(
                "NOT_READY",
                null,
                submitLabel("Verified", "OK", changeOwner.getAccountId()),
                submitLabel("Code-Review", "NEED", null)),
            submitRecord(
                "NOT_READY",
                null,
                submitLabel("Verified", "OK", changeOwner.getAccountId()),
                submitLabel("Alternative-Code-Review", "NEED", null))));
    update.commit();

    ChangeNotes notes = newNotes(c);
    List<SubmitRecord> recs = notes.getSubmitRecords();
    assertThat(recs).hasSize(2);
    assertThat(recs.get(0))
        .isEqualTo(
            submitRecord(
                "NOT_READY",
                null,
                submitLabel("Verified", "OK", changeOwner.getAccountId()),
                submitLabel("Code-Review", "NEED", null)));
    assertThat(recs.get(1))
        .isEqualTo(
            submitRecord(
                "NOT_READY",
                null,
                submitLabel("Verified", "OK", changeOwner.getAccountId()),
                submitLabel("Alternative-Code-Review", "NEED", null)));
    assertThat(notes.getChange().getSubmissionId()).isEqualTo(submissionId.toStringForStorage());
  }

  @Test
  public void latestSubmitRecordsOnly() throws Exception {
    Change c = newChange();
    RequestId submissionId = RequestId.forChange(c);
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setSubjectForCommit("Submit patch set 1");
    update.merge(
        submissionId,
        ImmutableList.of(
            submitRecord("OK", null, submitLabel("Code-Review", "OK", otherUser.getAccountId()))));
    update.commit();

    incrementPatchSet(c);
    update = newUpdate(c, changeOwner);
    update.setSubjectForCommit("Submit patch set 2");
    update.merge(
        submissionId,
        ImmutableList.of(
            submitRecord(
                "OK", null, submitLabel("Code-Review", "OK", changeOwner.getAccountId()))));
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getSubmitRecords())
        .containsExactly(
            submitRecord("OK", null, submitLabel("Code-Review", "OK", changeOwner.getAccountId())));
    assertThat(notes.getChange().getSubmissionId()).isEqualTo(submissionId.toStringForStorage());
  }

  @Test
  public void emptyChangeUpdate() throws Exception {
    Change c = newChange();
    Ref initial = repo.exactRef(changeMetaRef(c.getId()));
    assertThat(initial).isNotNull();

    // Empty update doesn't create a new commit.
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.commit();
    assertThat(update.getResult()).isNull();

    Ref updated = repo.exactRef(changeMetaRef(c.getId()));
    assertThat(updated.getObjectId()).isEqualTo(initial.getObjectId());
  }

  @Test
  public void assigneeCommit() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setAssignee(otherUserId);
    ObjectId result = update.commit();
    assertThat(result).isNotNull();
    try (RevWalk rw = new RevWalk(repo)) {
      RevCommit commit = rw.parseCommit(update.getResult());
      rw.parseBody(commit);
      String strIdent = otherUser.getName() + " <" + otherUserId + "@" + serverId + ">";
      assertThat(commit.getFullMessage()).contains("Assignee: " + strIdent);
    }
  }

  @Test
  public void assigneeChangeNotes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setAssignee(otherUserId);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getChange().getAssignee()).isEqualTo(otherUserId);

    update = newUpdate(c, changeOwner);
    update.setAssignee(changeOwner.getAccountId());
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getChange().getAssignee()).isEqualTo(changeOwner.getAccountId());
  }

  @Test
  public void pastAssigneesChangeNotes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setAssignee(otherUserId);
    update.commit();

    ChangeNotes notes = newNotes(c);

    update = newUpdate(c, changeOwner);
    update.setAssignee(changeOwner.getAccountId());
    update.commit();

    update = newUpdate(c, changeOwner);
    update.setAssignee(otherUserId);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.removeAssignee();
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getPastAssignees()).hasSize(2);
  }

  @Test
  public void hashtagCommit() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    LinkedHashSet<String> hashtags = new LinkedHashSet<>();
    hashtags.add("tag1");
    hashtags.add("tag2");
    update.setHashtags(hashtags);
    update.commit();
    try (RevWalk walk = new RevWalk(repo)) {
      RevCommit commit = walk.parseCommit(update.getResult());
      walk.parseBody(commit);
      assertThat(commit.getFullMessage()).contains("Hashtags: tag1,tag2\n");
    }
  }

  @Test
  public void hashtagChangeNotes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    LinkedHashSet<String> hashtags = new LinkedHashSet<>();
    hashtags.add("tag1");
    hashtags.add("tag2");
    update.setHashtags(hashtags);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getHashtags()).isEqualTo(hashtags);
  }

  @Test
  public void topicChangeNotes() throws Exception {
    Change c = newChange();

    // initially topic is not set
    ChangeNotes notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isNull();

    // set topic
    String topic = "myTopic";
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setTopic(topic);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isEqualTo(topic);

    // clear topic by setting empty string
    update = newUpdate(c, changeOwner);
    update.setTopic("");
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isNull();

    // set other topic
    topic = "otherTopic";
    update = newUpdate(c, changeOwner);
    update.setTopic(topic);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isEqualTo(topic);

    // clear topic by setting null
    update = newUpdate(c, changeOwner);
    update.setTopic(null);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isNull();
  }

  @Test
  public void changeIdChangeNotes() throws Exception {
    Change c = newChange();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getChange().getKey()).isEqualTo(c.getKey());

    // An update doesn't affect the Change-Id
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setTopic("topic"); // Change something to get a new commit.
    update.commit();
    assertThat(notes.getChange().getKey()).isEqualTo(c.getKey());

    // Trying to set another Change-Id fails
    String otherChangeId = "I577fb248e474018276351785930358ec0450e9f7";
    update = newUpdate(c, changeOwner);
    exception.expect(IllegalArgumentException.class);
    exception.expectMessage(
        "The Change-Id was already set to "
            + c.getKey()
            + ", so we cannot set this Change-Id: "
            + otherChangeId);
    update.setChangeId(otherChangeId);
  }

  @Test
  public void branchChangeNotes() throws Exception {
    Change c = newChange();

    ChangeNotes notes = newNotes(c);
    Branch.NameKey expectedBranch = new Branch.NameKey(project, "refs/heads/master");
    assertThat(notes.getChange().getDest()).isEqualTo(expectedBranch);

    // An update doesn't affect the branch
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setTopic("topic"); // Change something to get a new commit.
    update.commit();
    assertThat(newNotes(c).getChange().getDest()).isEqualTo(expectedBranch);

    // Set another branch
    String otherBranch = "refs/heads/stable";
    update = newUpdate(c, changeOwner);
    update.setBranch(otherBranch);
    update.commit();
    assertThat(newNotes(c).getChange().getDest())
        .isEqualTo(new Branch.NameKey(project, otherBranch));
  }

  @Test
  public void ownerChangeNotes() throws Exception {
    Change c = newChange();

    assertThat(newNotes(c).getChange().getOwner()).isEqualTo(changeOwner.getAccountId());

    // An update doesn't affect the owner
    ChangeUpdate update = newUpdate(c, otherUser);
    update.setTopic("topic"); // Change something to get a new commit.
    update.commit();
    assertThat(newNotes(c).getChange().getOwner()).isEqualTo(changeOwner.getAccountId());
  }

  @Test
  public void createdOnChangeNotes() throws Exception {
    Change c = newChange();

    Timestamp createdOn = newNotes(c).getChange().getCreatedOn();
    assertThat(createdOn).isNotNull();

    // An update doesn't affect the createdOn timestamp.
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setTopic("topic"); // Change something to get a new commit.
    update.commit();
    assertThat(newNotes(c).getChange().getCreatedOn()).isEqualTo(createdOn);
  }

  @Test
  public void lastUpdatedOnChangeNotes() throws Exception {
    Change c = newChange();

    ChangeNotes notes = newNotes(c);
    Timestamp ts1 = notes.getChange().getLastUpdatedOn();
    assertThat(ts1).isEqualTo(notes.getChange().getCreatedOn());

    // Various kinds of updates that update the timestamp.
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setTopic("topic"); // Change something to get a new commit.
    update.commit();
    Timestamp ts2 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts2).isGreaterThan(ts1);

    update = newUpdate(c, changeOwner);
    update.setChangeMessage("Some message");
    update.commit();
    Timestamp ts3 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts3).isGreaterThan(ts2);

    update = newUpdate(c, changeOwner);
    update.setHashtags(ImmutableSet.of("foo"));
    update.commit();
    Timestamp ts4 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts4).isGreaterThan(ts3);

    incrementPatchSet(c);
    Timestamp ts5 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts5).isGreaterThan(ts4);

    update = newUpdate(c, changeOwner);
    update.putApproval("Code-Review", (short) 1);
    update.commit();
    Timestamp ts6 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts6).isGreaterThan(ts5);

    update = newUpdate(c, changeOwner);
    update.setStatus(Change.Status.ABANDONED);
    update.commit();
    Timestamp ts7 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts7).isGreaterThan(ts6);

    update = newUpdate(c, changeOwner);
    update.putReviewer(otherUser.getAccountId(), ReviewerStateInternal.REVIEWER);
    update.commit();
    Timestamp ts8 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts8).isGreaterThan(ts7);

    update = newUpdate(c, changeOwner);
    update.setGroups(ImmutableList.of("a", "b"));
    update.commit();
    Timestamp ts9 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts9).isGreaterThan(ts8);

    // Finish off by merging the change.
    update = newUpdate(c, changeOwner);
    update.merge(
        RequestId.forChange(c),
        ImmutableList.of(
            submitRecord(
                "NOT_READY",
                null,
                submitLabel("Verified", "OK", changeOwner.getAccountId()),
                submitLabel("Alternative-Code-Review", "NEED", null))));
    update.commit();
    Timestamp ts10 = newNotes(c).getChange().getLastUpdatedOn();
    assertThat(ts10).isGreaterThan(ts9);
  }

  @Test
  public void subjectLeadingWhitespaceChangeNotes() throws Exception {
    Change c = TestChanges.newChange(project, changeOwner.getAccountId());
    String trimmedSubj = c.getSubject();
    c.setCurrentPatchSet(c.currentPatchSetId(), "  " + trimmedSubj, c.getOriginalSubject());
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeId(c.getKey().get());
    update.setBranch(c.getDest().get());
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getChange().getSubject()).isEqualTo(trimmedSubj);

    String tabSubj = "\t\t" + trimmedSubj;

    c = TestChanges.newChange(project, changeOwner.getAccountId());
    c.setCurrentPatchSet(c.currentPatchSetId(), tabSubj, c.getOriginalSubject());
    update = newUpdate(c, changeOwner);
    update.setChangeId(c.getKey().get());
    update.setBranch(c.getDest().get());
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getChange().getSubject()).isEqualTo(tabSubj);
  }

  @Test
  public void commitChangeNotesUnique() throws Exception {
    // PatchSetId -> RevId must be a one to one mapping
    Change c = newChange();

    ChangeNotes notes = newNotes(c);
    PatchSet ps = notes.getCurrentPatchSet();
    assertThat(ps).isNotNull();

    // new revId for the same patch set, ps1
    ChangeUpdate update = newUpdate(c, changeOwner);
    RevCommit commit = tr.commit().message("PS1 again").create();
    update.setCommit(rw, commit);
    update.commit();

    try {
      notes = newNotes(c);
      fail("Expected IOException");
    } catch (OrmException e) {
      assertCause(
          e,
          ConfigInvalidException.class,
          "Multiple revisions parsed for patch set 1:"
              + " RevId{"
              + commit.name()
              + "} and "
              + ps.getRevision().get());
    }
  }

  @Test
  public void patchSetChangeNotes() throws Exception {
    Change c = newChange();

    // ps1 created by newChange()
    ChangeNotes notes = newNotes(c);
    PatchSet ps1 = notes.getCurrentPatchSet();
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(ps1.getId());
    assertThat(notes.getChange().getSubject()).isEqualTo("Change subject");
    assertThat(notes.getChange().getOriginalSubject()).isEqualTo("Change subject");
    assertThat(ps1.getId()).isEqualTo(new PatchSet.Id(c.getId(), 1));
    assertThat(ps1.getUploader()).isEqualTo(changeOwner.getAccountId());

    // ps2 by other user
    RevCommit commit = incrementPatchSet(c, otherUser);
    notes = newNotes(c);
    PatchSet ps2 = notes.getCurrentPatchSet();
    assertThat(ps2.getId()).isEqualTo(new PatchSet.Id(c.getId(), 2));
    assertThat(notes.getChange().getSubject()).isEqualTo("PS2");
    assertThat(notes.getChange().getOriginalSubject()).isEqualTo("Change subject");
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(ps2.getId());
    assertThat(ps2.getRevision().get()).isNotEqualTo(ps1.getRevision());
    assertThat(ps2.getRevision().get()).isEqualTo(commit.name());
    assertThat(ps2.getUploader()).isEqualTo(otherUser.getAccountId());
    assertThat(ps2.getCreatedOn()).isEqualTo(notes.getChange().getLastUpdatedOn());

    // comment on ps1, current patch set is still ps2
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setPatchSetId(ps1.getId());
    update.setChangeMessage("Comment on old patch set.");
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getChange().currentPatchSetId()).isEqualTo(ps2.getId());
  }

  @Test
  public void patchSetStates() throws Exception {
    Change c = newChange();
    PatchSet.Id psId1 = c.currentPatchSetId();

    incrementCurrentPatchSetFieldOnly(c);
    PatchSet.Id psId2 = c.currentPatchSetId();
    RevCommit commit = tr.commit().message("PS2").create();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setCommit(rw, commit);
    update.setPatchSetState(PatchSetState.DRAFT);
    update.putApproval("Code-Review", (short) 1);
    update.setChangeMessage("This is a message");
    update.putComment(
        Status.PUBLISHED,
        newComment(
            c.currentPatchSetId(),
            "a.txt",
            "uuid1",
            new CommentRange(1, 2, 3, 4),
            1,
            changeOwner,
            null,
            TimeUtil.nowTs(),
            "Comment",
            (short) 1,
            commit.name(),
            false));
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getPatchSets().get(psId2).isDraft()).isTrue();
    assertThat(notes.getPatchSets().keySet()).containsExactly(psId1, psId2);
    assertThat(notes.getApprovals()).isNotEmpty();
    assertThat(notes.getChangeMessagesByPatchSet()).isNotEmpty();
    assertThat(notes.getChangeMessages()).isNotEmpty();
    assertThat(notes.getComments()).isNotEmpty();

    // publish ps2
    update = newUpdate(c, changeOwner);
    update.setPatchSetState(PatchSetState.PUBLISHED);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getPatchSets().get(psId2).isDraft()).isFalse();

    // delete ps2
    update = newUpdate(c, changeOwner);
    update.setPatchSetState(PatchSetState.DELETED);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getPatchSets().keySet()).containsExactly(psId1);
    assertThat(notes.getApprovals()).isEmpty();
    assertThat(notes.getChangeMessagesByPatchSet()).isEmpty();
    assertThat(notes.getChangeMessages()).isEmpty();
    assertThat(notes.getComments()).isEmpty();
  }

  @Test
  public void patchSetGroups() throws Exception {
    Change c = newChange();
    PatchSet.Id psId1 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getPatchSets().get(psId1).getGroups()).isEmpty();

    // ps1
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setGroups(ImmutableList.of("a", "b"));
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getPatchSets().get(psId1).getGroups()).containsExactly("a", "b").inOrder();

    incrementCurrentPatchSetFieldOnly(c);
    PatchSet.Id psId2 = c.currentPatchSetId();
    update = newUpdate(c, changeOwner);
    update.setCommit(rw, tr.commit().message("PS2").create());
    update.setGroups(ImmutableList.of("d"));
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getPatchSets().get(psId2).getGroups()).containsExactly("d");
    assertThat(notes.getPatchSets().get(psId1).getGroups()).containsExactly("a", "b").inOrder();
  }

  @Test
  public void pushCertificate() throws Exception {
    String pushCert =
        "certificate version 0.1\n"
            + "pusher This is not a real push cert\n"
            + "-----BEGIN PGP SIGNATURE-----\n"
            + "Version: GnuPG v1\n"
            + "\n"
            + "Nor is this a real signature.\n"
            + "-----END PGP SIGNATURE-----\n";

    // ps2 with push cert
    Change c = newChange();
    PatchSet.Id psId1 = c.currentPatchSetId();
    incrementCurrentPatchSetFieldOnly(c);
    PatchSet.Id psId2 = c.currentPatchSetId();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setPatchSetId(psId2);
    RevCommit commit = tr.commit().message("PS2").create();
    update.setCommit(rw, commit, pushCert);
    update.commit();

    ChangeNotes notes = newNotes(c);
    String note = readNote(notes, commit);
    if (!testJson()) {
      assertThat(note).isEqualTo(pushCert);
    }
    Map<PatchSet.Id, PatchSet> patchSets = notes.getPatchSets();
    assertThat(patchSets.get(psId1).getPushCertificate()).isNull();
    assertThat(patchSets.get(psId2).getPushCertificate()).isEqualTo(pushCert);
    assertThat(notes.getComments()).isEmpty();

    // comment on ps2
    update = newUpdate(c, changeOwner);
    update.setPatchSetId(psId2);
    Timestamp ts = TimeUtil.nowTs();
    update.putComment(
        Status.PUBLISHED,
        newComment(
            psId2,
            "a.txt",
            "uuid1",
            new CommentRange(1, 2, 3, 4),
            1,
            changeOwner,
            null,
            ts,
            "Comment",
            (short) 1,
            commit.name(),
            false));
    update.commit();

    notes = newNotes(c);

    patchSets = notes.getPatchSets();
    assertThat(patchSets.get(psId1).getPushCertificate()).isNull();
    assertThat(patchSets.get(psId2).getPushCertificate()).isEqualTo(pushCert);
    assertThat(notes.getComments()).isNotEmpty();

    if (!testJson()) {
      assertThat(readNote(notes, commit))
          .isEqualTo(
              pushCert
                  + "Revision: "
                  + commit.name()
                  + "\n"
                  + "Patch-set: 2\n"
                  + "File: a.txt\n"
                  + "\n"
                  + "1:2-3:4\n"
                  + ChangeNoteUtil.formatTime(serverIdent, ts)
                  + "\n"
                  + "Author: Change Owner <1@gerrit>\n"
                  + "Unresolved: false\n"
                  + "UUID: uuid1\n"
                  + "Bytes: 7\n"
                  + "Comment\n"
                  + "\n");
    }
  }

  @Test
  public void emptyExceptSubject() throws Exception {
    ChangeUpdate update = newUpdate(newChange(), changeOwner);
    update.setSubjectForCommit("Create change");
    assertThat(update.commit()).isNotNull();
  }

  @Test
  public void multipleUpdatesInManager() throws Exception {
    Change c = newChange();
    ChangeUpdate update1 = newUpdate(c, changeOwner);
    update1.putApproval("Verified", (short) 1);

    ChangeUpdate update2 = newUpdate(c, otherUser);
    update2.putApproval("Code-Review", (short) 2);

    try (NoteDbUpdateManager updateManager = updateManagerFactory.create(project)) {
      updateManager.add(update1);
      updateManager.add(update2);
      updateManager.execute();
    }

    ChangeNotes notes = newNotes(c);
    List<PatchSetApproval> psas = notes.getApprovals().get(c.currentPatchSetId());
    assertThat(psas).hasSize(2);

    assertThat(psas.get(0).getAccountId()).isEqualTo(changeOwner.getAccount().getId());
    assertThat(psas.get(0).getLabel()).isEqualTo("Verified");
    assertThat(psas.get(0).getValue()).isEqualTo((short) 1);

    assertThat(psas.get(1).getAccountId()).isEqualTo(otherUser.getAccount().getId());
    assertThat(psas.get(1).getLabel()).isEqualTo("Code-Review");
    assertThat(psas.get(1).getValue()).isEqualTo((short) 2);
  }

  @Test
  public void multipleUpdatesIncludingComments() throws Exception {
    Change c = newChange();
    ChangeUpdate update1 = newUpdate(c, otherUser);
    String uuid1 = "uuid1";
    String message1 = "comment 1";
    CommentRange range1 = new CommentRange(1, 1, 2, 1);
    Timestamp time1 = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();
    RevCommit tipCommit;
    try (NoteDbUpdateManager updateManager = updateManagerFactory.create(project)) {
      Comment comment1 =
          newComment(
              psId,
              "file1",
              uuid1,
              range1,
              range1.getEndLine(),
              otherUser,
              null,
              time1,
              message1,
              (short) 0,
              "abcd1234abcd1234abcd1234abcd1234abcd1234",
              false);
      update1.setPatchSetId(psId);
      update1.putComment(Status.PUBLISHED, comment1);
      updateManager.add(update1);

      ChangeUpdate update2 = newUpdate(c, otherUser);
      update2.putApproval("Code-Review", (short) 2);
      updateManager.add(update2);

      updateManager.execute();
    }

    ChangeNotes notes = newNotes(c);
    ObjectId tip = notes.getRevision();
    tipCommit = rw.parseCommit(tip);

    RevCommit commitWithApprovals = tipCommit;
    assertThat(commitWithApprovals).isNotNull();
    RevCommit commitWithComments = commitWithApprovals.getParent(0);
    assertThat(commitWithComments).isNotNull();

    try (ChangeNotesRevWalk rw = ChangeNotesCommit.newRevWalk(repo)) {
      ChangeNotesParser notesWithComments =
          new ChangeNotesParser(c.getId(), commitWithComments.copy(), rw, noteUtil, args.metrics);
      ChangeNotesState state = notesWithComments.parseAll();
      assertThat(state.approvals()).isEmpty();
      assertThat(state.publishedComments()).hasSize(1);
    }

    try (ChangeNotesRevWalk rw = ChangeNotesCommit.newRevWalk(repo)) {
      ChangeNotesParser notesWithApprovals =
          new ChangeNotesParser(c.getId(), commitWithApprovals.copy(), rw, noteUtil, args.metrics);
      ChangeNotesState state = notesWithApprovals.parseAll();
      assertThat(state.approvals()).hasSize(1);
      assertThat(state.publishedComments()).hasSize(1);
    }
  }

  @Test
  public void multipleUpdatesAcrossRefs() throws Exception {
    Change c1 = newChange();
    ChangeUpdate update1 = newUpdate(c1, changeOwner);
    update1.putApproval("Verified", (short) 1);

    Change c2 = newChange();
    ChangeUpdate update2 = newUpdate(c2, otherUser);
    update2.putApproval("Code-Review", (short) 2);

    Ref initial1 = repo.exactRef(update1.getRefName());
    assertThat(initial1).isNotNull();
    Ref initial2 = repo.exactRef(update2.getRefName());
    assertThat(initial2).isNotNull();

    try (NoteDbUpdateManager updateManager = updateManagerFactory.create(project)) {
      updateManager.add(update1);
      updateManager.add(update2);
      updateManager.execute();
    }

    Ref ref1 = repo.exactRef(update1.getRefName());
    assertThat(ref1.getObjectId()).isEqualTo(update1.getResult());
    assertThat(ref1.getObjectId()).isNotEqualTo(initial1.getObjectId());
    Ref ref2 = repo.exactRef(update2.getRefName());
    assertThat(ref2.getObjectId()).isEqualTo(update2.getResult());
    assertThat(ref2.getObjectId()).isNotEqualTo(initial2.getObjectId());

    PatchSetApproval approval1 =
        newNotes(c1).getApprovals().get(c1.currentPatchSetId()).iterator().next();
    assertThat(approval1.getLabel()).isEqualTo("Verified");

    PatchSetApproval approval2 =
        newNotes(c2).getApprovals().get(c2.currentPatchSetId()).iterator().next();
    assertThat(approval2.getLabel()).isEqualTo("Code-Review");
  }

  @Test
  public void changeMessageOnePatchSet() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.setChangeMessage("Just a little code change.\n");
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages = notes.getChangeMessagesByPatchSet();
    assertThat(changeMessages.keySet()).containsExactly(ps1);

    ChangeMessage cm = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertThat(cm.getMessage()).isEqualTo("Just a little code change.\n");
    assertThat(cm.getAuthor()).isEqualTo(changeOwner.getAccount().getId());
    assertThat(cm.getPatchSetId()).isEqualTo(ps1);
  }

  @Test
  public void noChangeMessage() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getChangeMessages()).isEmpty();
  }

  @Test
  public void changeMessageWithTrailingDoubleNewline() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("Testing trailing double newline\n\n");
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages = notes.getChangeMessagesByPatchSet();
    assertThat(changeMessages).hasSize(1);

    ChangeMessage cm1 = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertThat(cm1.getMessage()).isEqualTo("Testing trailing double newline\n\n");
    assertThat(cm1.getAuthor()).isEqualTo(changeOwner.getAccount().getId());
  }

  @Test
  public void changeMessageWithMultipleParagraphs() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("Testing paragraph 1\n\nTesting paragraph 2\n\nTesting paragraph 3");
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages = notes.getChangeMessagesByPatchSet();
    assertThat(changeMessages).hasSize(1);

    ChangeMessage cm1 = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertThat(cm1.getMessage())
        .isEqualTo(
            "Testing paragraph 1\n"
                + "\n"
                + "Testing paragraph 2\n"
                + "\n"
                + "Testing paragraph 3");
    assertThat(cm1.getAuthor()).isEqualTo(changeOwner.getAccount().getId());
  }

  @Test
  public void changeMessagesMultiplePatchSets() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.setChangeMessage("This is the change message for the first PS.");
    update.commit();
    PatchSet.Id ps1 = c.currentPatchSetId();

    incrementPatchSet(c);
    update = newUpdate(c, changeOwner);

    update.setChangeMessage("This is the change message for the second PS.");
    update.commit();
    PatchSet.Id ps2 = c.currentPatchSetId();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages = notes.getChangeMessagesByPatchSet();
    assertThat(changeMessages).hasSize(2);

    ChangeMessage cm1 = Iterables.getOnlyElement(changeMessages.get(ps1));
    assertThat(cm1.getMessage()).isEqualTo("This is the change message for the first PS.");
    assertThat(cm1.getAuthor()).isEqualTo(changeOwner.getAccount().getId());

    ChangeMessage cm2 = Iterables.getOnlyElement(changeMessages.get(ps2));
    assertThat(cm1.getPatchSetId()).isEqualTo(ps1);
    assertThat(cm2.getMessage()).isEqualTo("This is the change message for the second PS.");
    assertThat(cm2.getAuthor()).isEqualTo(changeOwner.getAccount().getId());
    assertThat(cm2.getPatchSetId()).isEqualTo(ps2);
  }

  @Test
  public void changeMessageMultipleInOnePatchSet() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.setChangeMessage("First change message.\n");
    update.commit();

    PatchSet.Id ps1 = c.currentPatchSetId();

    update = newUpdate(c, changeOwner);
    update.putReviewer(changeOwner.getAccount().getId(), REVIEWER);
    update.setChangeMessage("Second change message.\n");
    update.commit();

    ChangeNotes notes = newNotes(c);
    ListMultimap<PatchSet.Id, ChangeMessage> changeMessages = notes.getChangeMessagesByPatchSet();
    assertThat(changeMessages.keySet()).hasSize(1);

    List<ChangeMessage> cm = changeMessages.get(ps1);
    assertThat(cm).hasSize(2);
    assertThat(cm.get(0).getMessage()).isEqualTo("First change message.\n");
    assertThat(cm.get(0).getAuthor()).isEqualTo(changeOwner.getAccount().getId());
    assertThat(cm.get(0).getPatchSetId()).isEqualTo(ps1);
    assertThat(cm.get(1).getMessage()).isEqualTo("Second change message.\n");
    assertThat(cm.get(1).getAuthor()).isEqualTo(changeOwner.getAccount().getId());
    assertThat(cm.get(1).getPatchSetId()).isEqualTo(ps1);
  }

  @Test
  public void patchLineCommentsFileComment() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    PatchSet.Id psId = c.currentPatchSetId();
    RevId revId = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");

    Comment comment =
        newComment(
            psId,
            "file1",
            "uuid",
            null,
            0,
            otherUser,
            null,
            TimeUtil.nowTs(),
            "message",
            (short) 1,
            revId.get(),
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getComments()).isEqualTo(ImmutableListMultimap.of(revId, comment));
  }

  @Test
  public void patchLineCommentsZeroColumns() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    PatchSet.Id psId = c.currentPatchSetId();
    RevId revId = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");
    CommentRange range = new CommentRange(1, 0, 2, 0);

    Comment comment =
        newComment(
            psId,
            "file1",
            "uuid",
            range,
            range.getEndLine(),
            otherUser,
            null,
            TimeUtil.nowTs(),
            "message",
            (short) 1,
            revId.get(),
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getComments()).isEqualTo(ImmutableListMultimap.of(revId, comment));
  }

  @Test
  public void patchLineCommentZeroRange() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    PatchSet.Id psId = c.currentPatchSetId();
    RevId revId = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");
    CommentRange range = new CommentRange(0, 0, 0, 0);

    Comment comment =
        newComment(
            psId,
            "file",
            "uuid",
            range,
            range.getEndLine(),
            otherUser,
            null,
            TimeUtil.nowTs(),
            "message",
            (short) 1,
            revId.get(),
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getComments()).isEqualTo(ImmutableListMultimap.of(revId, comment));
  }

  @Test
  public void patchLineCommentEmptyFilename() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    PatchSet.Id psId = c.currentPatchSetId();
    RevId revId = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");
    CommentRange range = new CommentRange(1, 2, 3, 4);

    Comment comment =
        newComment(
            psId,
            "",
            "uuid",
            range,
            range.getEndLine(),
            otherUser,
            null,
            TimeUtil.nowTs(),
            "message",
            (short) 1,
            revId.get(),
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getComments()).isEqualTo(ImmutableListMultimap.of(revId, comment));
  }

  @Test
  public void patchLineCommentNotesFormatSide1() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String uuid3 = "uuid3";
    String message1 = "comment 1";
    String message2 = "comment 2";
    String message3 = "comment 3";
    CommentRange range1 = new CommentRange(1, 1, 2, 1);
    Timestamp time1 = TimeUtil.nowTs();
    Timestamp time2 = TimeUtil.nowTs();
    Timestamp time3 = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    Comment comment1 =
        newComment(
            psId,
            "file1",
            uuid1,
            range1,
            range1.getEndLine(),
            otherUser,
            null,
            time1,
            message1,
            (short) 1,
            "abcd1234abcd1234abcd1234abcd1234abcd1234",
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range2 = new CommentRange(2, 1, 3, 1);
    Comment comment2 =
        newComment(
            psId,
            "file1",
            uuid2,
            range2,
            range2.getEndLine(),
            otherUser,
            null,
            time2,
            message2,
            (short) 1,
            "abcd1234abcd1234abcd1234abcd1234abcd1234",
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment2);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range3 = new CommentRange(3, 0, 4, 1);
    Comment comment3 =
        newComment(
            psId,
            "file2",
            uuid3,
            range3,
            range3.getEndLine(),
            otherUser,
            null,
            time3,
            message3,
            (short) 1,
            "abcd1234abcd1234abcd1234abcd1234abcd1234",
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment3);
    update.commit();

    ChangeNotes notes = newNotes(c);

    try (RevWalk walk = new RevWalk(repo)) {
      ArrayList<Note> notesInTree = Lists.newArrayList(notes.revisionNoteMap.noteMap.iterator());
      Note note = Iterables.getOnlyElement(notesInTree);

      byte[] bytes = walk.getObjectReader().open(note.getData(), Constants.OBJ_BLOB).getBytes();
      String noteString = new String(bytes, UTF_8);

      if (!testJson()) {
        assertThat(noteString)
            .isEqualTo(
                "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
                    + "Patch-set: 1\n"
                    + "File: file1\n"
                    + "\n"
                    + "1:1-2:1\n"
                    + ChangeNoteUtil.formatTime(serverIdent, time1)
                    + "\n"
                    + "Author: Other Account <2@gerrit>\n"
                    + "Unresolved: false\n"
                    + "UUID: uuid1\n"
                    + "Bytes: 9\n"
                    + "comment 1\n"
                    + "\n"
                    + "2:1-3:1\n"
                    + ChangeNoteUtil.formatTime(serverIdent, time2)
                    + "\n"
                    + "Author: Other Account <2@gerrit>\n"
                    + "Unresolved: false\n"
                    + "UUID: uuid2\n"
                    + "Bytes: 9\n"
                    + "comment 2\n"
                    + "\n"
                    + "File: file2\n"
                    + "\n"
                    + "3:0-4:1\n"
                    + ChangeNoteUtil.formatTime(serverIdent, time3)
                    + "\n"
                    + "Author: Other Account <2@gerrit>\n"
                    + "Unresolved: false\n"
                    + "UUID: uuid3\n"
                    + "Bytes: 9\n"
                    + "comment 3\n"
                    + "\n");
      }
    }
  }

  @Test
  public void patchLineCommentNotesFormatSide0() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String message1 = "comment 1";
    String message2 = "comment 2";
    CommentRange range1 = new CommentRange(1, 1, 2, 1);
    Timestamp time1 = TimeUtil.nowTs();
    Timestamp time2 = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    Comment comment1 =
        newComment(
            psId,
            "file1",
            uuid1,
            range1,
            range1.getEndLine(),
            otherUser,
            null,
            time1,
            message1,
            (short) 0,
            "abcd1234abcd1234abcd1234abcd1234abcd1234",
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    CommentRange range2 = new CommentRange(2, 1, 3, 1);
    Comment comment2 =
        newComment(
            psId,
            "file1",
            uuid2,
            range2,
            range2.getEndLine(),
            otherUser,
            null,
            time2,
            message2,
            (short) 0,
            "abcd1234abcd1234abcd1234abcd1234abcd1234",
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);

    try (RevWalk walk = new RevWalk(repo)) {
      ArrayList<Note> notesInTree = Lists.newArrayList(notes.revisionNoteMap.noteMap.iterator());
      Note note = Iterables.getOnlyElement(notesInTree);

      byte[] bytes = walk.getObjectReader().open(note.getData(), Constants.OBJ_BLOB).getBytes();
      String noteString = new String(bytes, UTF_8);

      if (!testJson()) {
        assertThat(noteString)
            .isEqualTo(
                "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
                    + "Base-for-patch-set: 1\n"
                    + "File: file1\n"
                    + "\n"
                    + "1:1-2:1\n"
                    + ChangeNoteUtil.formatTime(serverIdent, time1)
                    + "\n"
                    + "Author: Other Account <2@gerrit>\n"
                    + "Unresolved: false\n"
                    + "UUID: uuid1\n"
                    + "Bytes: 9\n"
                    + "comment 1\n"
                    + "\n"
                    + "2:1-3:1\n"
                    + ChangeNoteUtil.formatTime(serverIdent, time2)
                    + "\n"
                    + "Author: Other Account <2@gerrit>\n"
                    + "Unresolved: false\n"
                    + "UUID: uuid2\n"
                    + "Bytes: 9\n"
                    + "comment 2\n"
                    + "\n");
      }
    }
  }

  @Test
  public void patchLineCommentNotesResolvedChangesValue() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String message1 = "comment 1";
    String message2 = "comment 2";
    CommentRange range1 = new CommentRange(1, 1, 2, 1);
    Timestamp time1 = TimeUtil.nowTs();
    Timestamp time2 = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    Comment comment1 =
        newComment(
            psId,
            "file1",
            uuid1,
            range1,
            range1.getEndLine(),
            otherUser,
            null,
            time1,
            message1,
            (short) 0,
            "abcd1234abcd1234abcd1234abcd1234abcd1234",
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    Comment comment2 =
        newComment(
            psId,
            "file1",
            uuid2,
            range1,
            range1.getEndLine(),
            otherUser,
            uuid1,
            time2,
            message2,
            (short) 0,
            "abcd1234abcd1234abcd1234abcd1234abcd1234",
            true);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);

    try (RevWalk walk = new RevWalk(repo)) {
      ArrayList<Note> notesInTree = Lists.newArrayList(notes.revisionNoteMap.noteMap.iterator());
      Note note = Iterables.getOnlyElement(notesInTree);

      byte[] bytes = walk.getObjectReader().open(note.getData(), Constants.OBJ_BLOB).getBytes();
      String noteString = new String(bytes, UTF_8);

      if (!testJson()) {
        assertThat(noteString)
            .isEqualTo(
                "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
                    + "Base-for-patch-set: 1\n"
                    + "File: file1\n"
                    + "\n"
                    + "1:1-2:1\n"
                    + ChangeNoteUtil.formatTime(serverIdent, time1)
                    + "\n"
                    + "Author: Other Account <2@gerrit>\n"
                    + "Unresolved: false\n"
                    + "UUID: uuid1\n"
                    + "Bytes: 9\n"
                    + "comment 1\n"
                    + "\n"
                    + "1:1-2:1\n"
                    + ChangeNoteUtil.formatTime(serverIdent, time2)
                    + "\n"
                    + "Author: Other Account <2@gerrit>\n"
                    + "Parent: uuid1\n"
                    + "Unresolved: true\n"
                    + "UUID: uuid2\n"
                    + "Bytes: 9\n"
                    + "comment 2\n"
                    + "\n");
      }
    }
  }

  @Test
  public void patchLineCommentNotesFormatMultiplePatchSetsSameRevId() throws Exception {
    Change c = newChange();
    PatchSet.Id psId1 = c.currentPatchSetId();
    incrementPatchSet(c);
    PatchSet.Id psId2 = c.currentPatchSetId();
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String uuid3 = "uuid3";
    String message1 = "comment 1";
    String message2 = "comment 2";
    String message3 = "comment 3";
    CommentRange range1 = new CommentRange(1, 1, 2, 1);
    CommentRange range2 = new CommentRange(2, 1, 3, 1);
    Timestamp time = TimeUtil.nowTs();
    RevId revId = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");

    Comment comment1 =
        newComment(
            psId1,
            "file1",
            uuid1,
            range1,
            range1.getEndLine(),
            otherUser,
            null,
            time,
            message1,
            (short) 0,
            revId.get(),
            false);
    Comment comment2 =
        newComment(
            psId1,
            "file1",
            uuid2,
            range2,
            range2.getEndLine(),
            otherUser,
            null,
            time,
            message2,
            (short) 0,
            revId.get(),
            false);
    Comment comment3 =
        newComment(
            psId2,
            "file1",
            uuid3,
            range1,
            range1.getEndLine(),
            otherUser,
            null,
            time,
            message3,
            (short) 0,
            revId.get(),
            false);

    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(psId2);
    update.putComment(Status.PUBLISHED, comment3);
    update.putComment(Status.PUBLISHED, comment2);
    update.putComment(Status.PUBLISHED, comment1);
    update.commit();

    ChangeNotes notes = newNotes(c);

    try (RevWalk walk = new RevWalk(repo)) {
      ArrayList<Note> notesInTree = Lists.newArrayList(notes.revisionNoteMap.noteMap.iterator());
      Note note = Iterables.getOnlyElement(notesInTree);

      byte[] bytes = walk.getObjectReader().open(note.getData(), Constants.OBJ_BLOB).getBytes();
      String noteString = new String(bytes, UTF_8);
      String timeStr = ChangeNoteUtil.formatTime(serverIdent, time);

      if (!testJson()) {
        assertThat(noteString)
            .isEqualTo(
                "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
                    + "Base-for-patch-set: 1\n"
                    + "File: file1\n"
                    + "\n"
                    + "1:1-2:1\n"
                    + timeStr
                    + "\n"
                    + "Author: Other Account <2@gerrit>\n"
                    + "Unresolved: false\n"
                    + "UUID: uuid1\n"
                    + "Bytes: 9\n"
                    + "comment 1\n"
                    + "\n"
                    + "2:1-3:1\n"
                    + timeStr
                    + "\n"
                    + "Author: Other Account <2@gerrit>\n"
                    + "Unresolved: false\n"
                    + "UUID: uuid2\n"
                    + "Bytes: 9\n"
                    + "comment 2\n"
                    + "\n"
                    + "Base-for-patch-set: 2\n"
                    + "File: file1\n"
                    + "\n"
                    + "1:1-2:1\n"
                    + timeStr
                    + "\n"
                    + "Author: Other Account <2@gerrit>\n"
                    + "Unresolved: false\n"
                    + "UUID: uuid3\n"
                    + "Bytes: 9\n"
                    + "comment 3\n"
                    + "\n");
      }
    }
    assertThat(notes.getComments())
        .isEqualTo(
            ImmutableListMultimap.of(
                revId, comment1,
                revId, comment2,
                revId, comment3));
  }

  @Test
  public void patchLineCommentNotesFormatRealAuthor() throws Exception {
    Change c = newChange();
    CurrentUser ownerAsOtherUser = userFactory.runAs(null, otherUserId, changeOwner);
    ChangeUpdate update = newUpdate(c, ownerAsOtherUser);
    String uuid = "uuid";
    String message = "comment";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    Timestamp time = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();
    RevId revId = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");

    Comment comment =
        newComment(
            psId,
            "file",
            uuid,
            range,
            range.getEndLine(),
            otherUser,
            null,
            time,
            message,
            (short) 1,
            revId.get(),
            false);
    comment.setRealAuthor(changeOwner.getAccountId());
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment);
    update.commit();

    ChangeNotes notes = newNotes(c);

    try (RevWalk walk = new RevWalk(repo)) {
      ArrayList<Note> notesInTree = Lists.newArrayList(notes.revisionNoteMap.noteMap.iterator());
      Note note = Iterables.getOnlyElement(notesInTree);

      byte[] bytes = walk.getObjectReader().open(note.getData(), Constants.OBJ_BLOB).getBytes();
      String noteString = new String(bytes, UTF_8);

      if (!testJson()) {
        assertThat(noteString)
            .isEqualTo(
                "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
                    + "Patch-set: 1\n"
                    + "File: file\n"
                    + "\n"
                    + "1:1-2:1\n"
                    + ChangeNoteUtil.formatTime(serverIdent, time)
                    + "\n"
                    + "Author: Other Account <2@gerrit>\n"
                    + "Real-author: Change Owner <1@gerrit>\n"
                    + "Unresolved: false\n"
                    + "UUID: uuid\n"
                    + "Bytes: 7\n"
                    + "comment\n"
                    + "\n");
      }
    }
    assertThat(notes.getComments()).isEqualTo(ImmutableListMultimap.of(revId, comment));
  }

  @Test
  public void patchLineCommentNotesFormatWeirdUser() throws Exception {
    Account account = new Account(new Account.Id(3), TimeUtil.nowTs());
    account.setFullName("Weird\n\u0002<User>\n");
    account.setPreferredEmail(" we\r\nird@ex>ample<.com");
    accountCache.put(account);
    IdentifiedUser user = userFactory.create(account.getId());

    Change c = newChange();
    ChangeUpdate update = newUpdate(c, user);
    String uuid = "uuid";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    Timestamp time = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    Comment comment =
        newComment(
            psId,
            "file1",
            uuid,
            range,
            range.getEndLine(),
            user,
            null,
            time,
            "comment",
            (short) 1,
            "abcd1234abcd1234abcd1234abcd1234abcd1234",
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment);
    update.commit();

    ChangeNotes notes = newNotes(c);

    try (RevWalk walk = new RevWalk(repo)) {
      ArrayList<Note> notesInTree = Lists.newArrayList(notes.revisionNoteMap.noteMap.iterator());
      Note note = Iterables.getOnlyElement(notesInTree);

      byte[] bytes = walk.getObjectReader().open(note.getData(), Constants.OBJ_BLOB).getBytes();
      String noteString = new String(bytes, UTF_8);
      String timeStr = ChangeNoteUtil.formatTime(serverIdent, time);

      if (!testJson()) {
        assertThat(noteString)
            .isEqualTo(
                "Revision: abcd1234abcd1234abcd1234abcd1234abcd1234\n"
                    + "Patch-set: 1\n"
                    + "File: file1\n"
                    + "\n"
                    + "1:1-2:1\n"
                    + timeStr
                    + "\n"
                    + "Author: Weird\u0002User <3@gerrit>\n"
                    + "Unresolved: false\n"
                    + "UUID: uuid\n"
                    + "Bytes: 7\n"
                    + "comment\n"
                    + "\n");
      }
    }
    assertThat(notes.getComments())
        .isEqualTo(ImmutableListMultimap.of(new RevId(comment.revId), comment));
  }

  @Test
  public void patchLineCommentMultipleOnePatchsetOneFileBothSides() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String rev1 = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String rev2 = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    String messageForBase = "comment for base";
    String messageForPS = "comment for ps";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    Comment commentForBase =
        newComment(
            psId,
            "filename",
            uuid1,
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            messageForBase,
            (short) 0,
            rev1,
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, commentForBase);
    update.commit();

    update = newUpdate(c, otherUser);
    Comment commentForPS =
        newComment(
            psId,
            "filename",
            uuid2,
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            messageForPS,
            (short) 1,
            rev2,
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, commentForPS);
    update.commit();

    assertThat(newNotes(c).getComments())
        .containsExactlyEntriesIn(
            ImmutableListMultimap.of(
                new RevId(rev1), commentForBase,
                new RevId(rev2), commentForPS));
  }

  @Test
  public void patchLineCommentMultipleOnePatchsetOneFile() throws Exception {
    Change c = newChange();
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String rev = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id psId = c.currentPatchSetId();
    String filename = "filename";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp timeForComment1 = TimeUtil.nowTs();
    Timestamp timeForComment2 = TimeUtil.nowTs();
    Comment comment1 =
        newComment(
            psId,
            filename,
            uuid1,
            range,
            range.getEndLine(),
            otherUser,
            null,
            timeForComment1,
            "comment 1",
            side,
            rev,
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    Comment comment2 =
        newComment(
            psId,
            filename,
            uuid2,
            range,
            range.getEndLine(),
            otherUser,
            null,
            timeForComment2,
            "comment 2",
            side,
            rev,
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment2);
    update.commit();

    assertThat(newNotes(c).getComments())
        .containsExactlyEntriesIn(
            ImmutableListMultimap.of(
                new RevId(rev), comment1,
                new RevId(rev), comment2))
        .inOrder();
  }

  @Test
  public void patchLineCommentMultipleOnePatchsetMultipleFiles() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id psId = c.currentPatchSetId();
    String filename1 = "filename1";
    String filename2 = "filename2";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    Comment comment1 =
        newComment(
            psId,
            filename1,
            uuid,
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment 1",
            side,
            rev,
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment1);
    update.commit();

    update = newUpdate(c, otherUser);
    Comment comment2 =
        newComment(
            psId,
            filename2,
            uuid,
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment 2",
            side,
            rev,
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment2);
    update.commit();

    assertThat(newNotes(c).getComments())
        .containsExactlyEntriesIn(
            ImmutableListMultimap.of(
                new RevId(rev), comment1,
                new RevId(rev), comment2))
        .inOrder();
  }

  @Test
  public void patchLineCommentMultiplePatchsets() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev1 = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String rev2 = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    Comment comment1 =
        newComment(
            ps1,
            filename,
            uuid,
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment on ps1",
            side,
            rev1,
            false);
    update.setPatchSetId(ps1);
    update.putComment(Status.PUBLISHED, comment1);
    update.commit();

    incrementPatchSet(c);
    PatchSet.Id ps2 = c.currentPatchSetId();

    update = newUpdate(c, otherUser);
    now = TimeUtil.nowTs();
    Comment comment2 =
        newComment(
            ps2,
            filename,
            uuid,
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment on ps2",
            side,
            rev2,
            false);
    update.setPatchSetId(ps2);
    update.putComment(Status.PUBLISHED, comment2);
    update.commit();

    assertThat(newNotes(c).getComments())
        .containsExactlyEntriesIn(
            ImmutableListMultimap.of(
                new RevId(rev1), comment1,
                new RevId(rev2), comment2));
  }

  @Test
  public void patchLineCommentSingleDraftToPublished() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    Comment comment1 =
        newComment(
            ps1,
            filename,
            uuid,
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment on ps1",
            side,
            rev,
            false);
    update.setPatchSetId(ps1);
    update.putComment(Status.DRAFT, comment1);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId))
        .containsExactlyEntriesIn(ImmutableListMultimap.of(new RevId(rev), comment1));
    assertThat(notes.getComments()).isEmpty();

    update = newUpdate(c, otherUser);
    update.setPatchSetId(ps1);
    update.putComment(Status.PUBLISHED, comment1);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).isEmpty();
    assertThat(notes.getComments())
        .containsExactlyEntriesIn(ImmutableListMultimap.of(new RevId(rev), comment1));
  }

  @Test
  public void patchLineCommentMultipleDraftsSameSidePublishOne() throws Exception {
    Change c = newChange();
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String rev = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range1 = new CommentRange(1, 1, 2, 2);
    CommentRange range2 = new CommentRange(2, 2, 3, 3);
    String filename = "filename1";
    short side = (short) 1;
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    // Write two drafts on the same side of one patch set.
    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);
    Comment comment1 =
        newComment(
            psId,
            filename,
            uuid1,
            range1,
            range1.getEndLine(),
            otherUser,
            null,
            now,
            "comment on ps1",
            side,
            rev,
            false);
    Comment comment2 =
        newComment(
            psId,
            filename,
            uuid2,
            range2,
            range2.getEndLine(),
            otherUser,
            null,
            now,
            "other on ps1",
            side,
            rev,
            false);
    update.putComment(Status.DRAFT, comment1);
    update.putComment(Status.DRAFT, comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId))
        .containsExactlyEntriesIn(
            ImmutableListMultimap.of(
                new RevId(rev), comment1,
                new RevId(rev), comment2))
        .inOrder();
    assertThat(notes.getComments()).isEmpty();

    // Publish first draft.
    update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment1);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId))
        .containsExactlyEntriesIn(ImmutableListMultimap.of(new RevId(rev), comment2));
    assertThat(notes.getComments())
        .containsExactlyEntriesIn(ImmutableListMultimap.of(new RevId(rev), comment1));
  }

  @Test
  public void patchLineCommentsMultipleDraftsBothSidesPublishAll() throws Exception {
    Change c = newChange();
    String uuid1 = "uuid1";
    String uuid2 = "uuid2";
    String rev1 = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String rev2 = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range1 = new CommentRange(1, 1, 2, 2);
    CommentRange range2 = new CommentRange(2, 2, 3, 3);
    String filename = "filename1";
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    // Write two drafts, one on each side of the patchset.
    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);
    Comment baseComment =
        newComment(
            psId,
            filename,
            uuid1,
            range1,
            range1.getEndLine(),
            otherUser,
            null,
            now,
            "comment on base",
            (short) 0,
            rev1,
            false);
    Comment psComment =
        newComment(
            psId,
            filename,
            uuid2,
            range2,
            range2.getEndLine(),
            otherUser,
            null,
            now,
            "comment on ps",
            (short) 1,
            rev2,
            false);

    update.putComment(Status.DRAFT, baseComment);
    update.putComment(Status.DRAFT, psComment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId))
        .containsExactlyEntriesIn(
            ImmutableListMultimap.of(
                new RevId(rev1), baseComment,
                new RevId(rev2), psComment));
    assertThat(notes.getComments()).isEmpty();

    // Publish both comments.
    update = newUpdate(c, otherUser);
    update.setPatchSetId(psId);

    update.putComment(Status.PUBLISHED, baseComment);
    update.putComment(Status.PUBLISHED, psComment);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).isEmpty();
    assertThat(notes.getComments())
        .containsExactlyEntriesIn(
            ImmutableListMultimap.of(
                new RevId(rev1), baseComment,
                new RevId(rev2), psComment));
  }

  @Test
  public void patchLineCommentsDeleteAllDrafts() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    ObjectId objId = ObjectId.fromString(rev);
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id psId = c.currentPatchSetId();
    String filename = "filename";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    Comment comment =
        newComment(
            psId,
            filename,
            uuid,
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment on ps1",
            side,
            rev,
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.DRAFT, comment);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).hasSize(1);
    assertThat(notes.getDraftCommentNotes().getNoteMap().contains(objId)).isTrue();

    update = newUpdate(c, otherUser);
    now = TimeUtil.nowTs();
    update.setPatchSetId(psId);
    update.deleteComment(comment);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).isEmpty();
    assertThat(notes.getDraftCommentNotes().getNoteMap()).isNull();
  }

  @Test
  public void patchLineCommentsDeleteAllDraftsForOneRevision() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev1 = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String rev2 = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    ObjectId objId1 = ObjectId.fromString(rev1);
    ObjectId objId2 = ObjectId.fromString(rev2);
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    Comment comment1 =
        newComment(
            ps1,
            filename,
            uuid,
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment on ps1",
            side,
            rev1,
            false);
    update.setPatchSetId(ps1);
    update.putComment(Status.DRAFT, comment1);
    update.commit();

    incrementPatchSet(c);
    PatchSet.Id ps2 = c.currentPatchSetId();

    update = newUpdate(c, otherUser);
    now = TimeUtil.nowTs();
    Comment comment2 =
        newComment(
            ps2,
            filename,
            uuid,
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment on ps2",
            side,
            rev2,
            false);
    update.setPatchSetId(ps2);
    update.putComment(Status.DRAFT, comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).hasSize(2);

    update = newUpdate(c, otherUser);
    now = TimeUtil.nowTs();
    update.setPatchSetId(ps2);
    update.deleteComment(comment2);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).hasSize(1);
    NoteMap noteMap = notes.getDraftCommentNotes().getNoteMap();
    assertThat(noteMap.contains(objId1)).isTrue();
    assertThat(noteMap.contains(objId2)).isFalse();
  }

  @Test
  public void addingPublishedCommentDoesNotCreateNoOpCommitOnEmptyDraftRef() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    Comment comment =
        newComment(
            ps1,
            filename,
            uuid,
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment on ps1",
            side,
            rev,
            false);
    update.putComment(Status.PUBLISHED, comment);
    update.commit();

    assertThat(repo.exactRef(changeMetaRef(c.getId()))).isNotNull();
    String draftRef = refsDraftComments(c.getId(), otherUser.getAccountId());
    assertThat(exactRefAllUsers(draftRef)).isNull();
  }

  @Test
  public void addingPublishedCommentDoesNotCreateNoOpCommitOnNonEmptyDraftRef() throws Exception {
    Change c = newChange();
    String rev = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    Comment draft =
        newComment(
            ps1,
            filename,
            "uuid1",
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "draft comment on ps1",
            side,
            rev,
            false);
    update.putComment(Status.DRAFT, draft);
    update.commit();

    String draftRef = refsDraftComments(c.getId(), otherUser.getAccountId());
    ObjectId old = exactRefAllUsers(draftRef);
    assertThat(old).isNotNull();

    update = newUpdate(c, otherUser);
    Comment pub =
        newComment(
            ps1,
            filename,
            "uuid2",
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment on ps1",
            side,
            rev,
            false);
    update.putComment(Status.PUBLISHED, pub);
    update.commit();

    assertThat(exactRefAllUsers(draftRef)).isEqualTo(old);
  }

  @Test
  public void fileComment() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid = "uuid";
    String rev = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String messageForBase = "comment for base";
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    Comment comment =
        newComment(
            psId,
            "filename",
            uuid,
            null,
            0,
            otherUser,
            null,
            now,
            messageForBase,
            (short) 0,
            rev,
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment);
    update.commit();

    assertThat(newNotes(c).getComments())
        .containsExactlyEntriesIn(ImmutableListMultimap.of(new RevId(rev), comment));
  }

  @Test
  public void patchLineCommentNoRange() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, otherUser);
    String uuid = "uuid";
    String rev = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String messageForBase = "comment for base";
    Timestamp now = TimeUtil.nowTs();
    PatchSet.Id psId = c.currentPatchSetId();

    Comment comment =
        newComment(
            psId,
            "filename",
            uuid,
            null,
            1,
            otherUser,
            null,
            now,
            messageForBase,
            (short) 0,
            rev,
            false);
    update.setPatchSetId(psId);
    update.putComment(Status.PUBLISHED, comment);
    update.commit();

    assertThat(newNotes(c).getComments())
        .containsExactlyEntriesIn(ImmutableListMultimap.of(new RevId(rev), comment));
  }

  @Test
  public void putCommentsForMultipleRevisions() throws Exception {
    Change c = newChange();
    String uuid = "uuid";
    String rev1 = "abcd1234abcd1234abcd1234abcd1234abcd1234";
    String rev2 = "abcd4567abcd4567abcd4567abcd4567abcd4567";
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    String filename = "filename1";
    short side = (short) 1;

    incrementPatchSet(c);
    PatchSet.Id ps2 = c.currentPatchSetId();

    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(ps2);
    Timestamp now = TimeUtil.nowTs();
    Comment comment1 =
        newComment(
            ps1,
            filename,
            uuid,
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment on ps1",
            side,
            rev1,
            false);
    Comment comment2 =
        newComment(
            ps2,
            filename,
            uuid,
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment on ps2",
            side,
            rev2,
            false);
    update.putComment(Status.DRAFT, comment1);
    update.putComment(Status.DRAFT, comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).hasSize(2);
    assertThat(notes.getComments()).isEmpty();

    update = newUpdate(c, otherUser);
    update.setPatchSetId(ps2);
    update.putComment(Status.PUBLISHED, comment1);
    update.putComment(Status.PUBLISHED, comment2);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId)).isEmpty();
    assertThat(notes.getComments()).hasSize(2);
  }

  @Test
  public void publishSubsetOfCommentsOnRevision() throws Exception {
    Change c = newChange();
    RevId rev1 = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    update.setPatchSetId(ps1);
    Timestamp now = TimeUtil.nowTs();
    Comment comment1 =
        newComment(
            ps1,
            "file1",
            "uuid1",
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment1",
            side,
            rev1.get(),
            false);
    Comment comment2 =
        newComment(
            ps1,
            "file2",
            "uuid2",
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment2",
            side,
            rev1.get(),
            false);
    update.putComment(Status.DRAFT, comment1);
    update.putComment(Status.DRAFT, comment2);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId).get(rev1)).containsExactly(comment1, comment2);
    assertThat(notes.getComments()).isEmpty();

    update = newUpdate(c, otherUser);
    update.setPatchSetId(ps1);
    update.putComment(Status.PUBLISHED, comment2);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId).get(rev1)).containsExactly(comment1);
    assertThat(notes.getComments().get(rev1)).containsExactly(comment2);
  }

  @Test
  public void updateWithServerIdent() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, internalUser);
    update.setChangeMessage("A message.");
    update.commit();

    ChangeMessage msg = Iterables.getLast(newNotes(c).getChangeMessages());
    assertThat(msg.getMessage()).isEqualTo("A message.");
    assertThat(msg.getAuthor()).isNull();

    update = newUpdate(c, internalUser);
    exception.expect(IllegalStateException.class);
    update.putApproval("Code-Review", (short) 1);
  }

  @Test
  public void filterOutAndFixUpZombieDraftComments() throws Exception {
    Change c = newChange();
    RevId rev1 = new RevId("abcd1234abcd1234abcd1234abcd1234abcd1234");
    CommentRange range = new CommentRange(1, 1, 2, 1);
    PatchSet.Id ps1 = c.currentPatchSetId();
    short side = (short) 1;

    ChangeUpdate update = newUpdate(c, otherUser);
    Timestamp now = TimeUtil.nowTs();
    Comment comment1 =
        newComment(
            ps1,
            "file1",
            "uuid1",
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "comment on ps1",
            side,
            rev1.get(),
            false);
    Comment comment2 =
        newComment(
            ps1,
            "file2",
            "uuid2",
            range,
            range.getEndLine(),
            otherUser,
            null,
            now,
            "another comment",
            side,
            rev1.get(),
            false);
    update.putComment(Status.DRAFT, comment1);
    update.putComment(Status.DRAFT, comment2);
    update.commit();

    String refName = refsDraftComments(c.getId(), otherUserId);
    ObjectId oldDraftId = exactRefAllUsers(refName);

    update = newUpdate(c, otherUser);
    update.setPatchSetId(ps1);
    update.putComment(Status.PUBLISHED, comment2);
    update.commit();
    assertThat(exactRefAllUsers(refName)).isNotNull();
    assertThat(exactRefAllUsers(refName)).isNotEqualTo(oldDraftId);

    // Re-add draft version of comment2 back to draft ref without updating
    // change ref. Simulates the case where deleting the draft failed
    // non-atomically after adding the published comment succeeded.
    ChangeDraftUpdate draftUpdate = newUpdate(c, otherUser).createDraftUpdateIfNull();
    draftUpdate.putComment(comment2);
    try (NoteDbUpdateManager manager = updateManagerFactory.create(c.getProject())) {
      manager.add(draftUpdate);
      manager.execute();
    }

    // Looking at drafts directly shows the zombie comment.
    DraftCommentNotes draftNotes = draftNotesFactory.create(c, otherUserId);
    assertThat(draftNotes.load().getComments().get(rev1)).containsExactly(comment1, comment2);

    // Zombie comment is filtered out of drafts via ChangeNotes.
    ChangeNotes notes = newNotes(c);
    assertThat(notes.getDraftComments(otherUserId).get(rev1)).containsExactly(comment1);
    assertThat(notes.getComments().get(rev1)).containsExactly(comment2);

    update = newUpdate(c, otherUser);
    update.setPatchSetId(ps1);
    update.putComment(Status.PUBLISHED, comment1);
    update.commit();

    // Updating an unrelated comment causes the zombie comment to get fixed up.
    assertThat(exactRefAllUsers(refName)).isNull();
  }

  @Test
  public void updateCommentsInSequentialUpdates() throws Exception {
    Change c = newChange();
    CommentRange range = new CommentRange(1, 1, 2, 1);
    String rev = "abcd1234abcd1234abcd1234abcd1234abcd1234";

    ChangeUpdate update1 = newUpdate(c, otherUser);
    Comment comment1 =
        newComment(
            c.currentPatchSetId(),
            "filename",
            "uuid1",
            range,
            range.getEndLine(),
            otherUser,
            null,
            new Timestamp(update1.getWhen().getTime()),
            "comment 1",
            (short) 1,
            rev,
            false);
    update1.putComment(Status.PUBLISHED, comment1);

    ChangeUpdate update2 = newUpdate(c, otherUser);
    Comment comment2 =
        newComment(
            c.currentPatchSetId(),
            "filename",
            "uuid2",
            range,
            range.getEndLine(),
            otherUser,
            null,
            new Timestamp(update2.getWhen().getTime()),
            "comment 2",
            (short) 1,
            rev,
            false);
    update2.putComment(Status.PUBLISHED, comment2);

    try (NoteDbUpdateManager manager = updateManagerFactory.create(project)) {
      manager.add(update1);
      manager.add(update2);
      manager.execute();
    }

    ChangeNotes notes = newNotes(c);
    List<Comment> comments = notes.getComments().get(new RevId(rev));
    assertThat(comments).hasSize(2);
    assertThat(comments.get(0).message).isEqualTo("comment 1");
    assertThat(comments.get(1).message).isEqualTo("comment 2");
  }

  @Test
  public void realUser() throws Exception {
    Change c = newChange();
    CurrentUser ownerAsOtherUser = userFactory.runAs(null, otherUserId, changeOwner);
    ChangeUpdate update = newUpdate(c, ownerAsOtherUser);
    update.setChangeMessage("Message on behalf of other user");
    update.commit();

    ChangeMessage msg = Iterables.getLast(newNotes(c).getChangeMessages());
    assertThat(msg.getMessage()).isEqualTo("Message on behalf of other user");
    assertThat(msg.getAuthor()).isEqualTo(otherUserId);
    assertThat(msg.getRealAuthor()).isEqualTo(changeOwner.getAccountId());
  }

  @Test
  public void ignoreEntitiesBeyondCurrentPatchSet() throws Exception {
    Change c = newChange();
    ChangeNotes notes = newNotes(c);
    int numMessages = notes.getChangeMessages().size();
    int numPatchSets = notes.getPatchSets().size();
    int numApprovals = notes.getApprovals().size();
    int numComments = notes.getComments().size();

    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setPatchSetId(new PatchSet.Id(c.getId(), c.currentPatchSetId().get() + 1));
    update.setChangeMessage("Should be ignored");
    update.putApproval("Code-Review", (short) 2);
    CommentRange range = new CommentRange(1, 1, 2, 1);
    Comment comment =
        newComment(
            update.getPatchSetId(),
            "filename",
            "uuid",
            range,
            range.getEndLine(),
            changeOwner,
            null,
            new Timestamp(update.getWhen().getTime()),
            "comment",
            (short) 1,
            "abcd1234abcd1234abcd1234abcd1234abcd1234",
            false);
    update.putComment(Status.PUBLISHED, comment);
    update.commit();

    notes = newNotes(c);
    assertThat(notes.getChangeMessages()).hasSize(numMessages);
    assertThat(notes.getPatchSets()).hasSize(numPatchSets);
    assertThat(notes.getApprovals()).hasSize(numApprovals);
    assertThat(notes.getComments()).hasSize(numComments);
  }

  @Test
  public void currentPatchSet() throws Exception {
    Change c = newChange();
    assertThat(newNotes(c).getChange().currentPatchSetId().get()).isEqualTo(1);

    incrementPatchSet(c);
    assertThat(newNotes(c).getChange().currentPatchSetId().get()).isEqualTo(2);

    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setPatchSetId(new PatchSet.Id(c.getId(), 1));
    update.setCurrentPatchSet();
    update.commit();
    assertThat(newNotes(c).getChange().currentPatchSetId().get()).isEqualTo(1);

    incrementPatchSet(c);
    assertThat(newNotes(c).getChange().currentPatchSetId().get()).isEqualTo(3);

    // Delete PS3, PS1 becomes current, as the most recent event explicitly set
    // it to current.
    update = newUpdate(c, changeOwner);
    update.setPatchSetState(PatchSetState.DELETED);
    update.commit();
    assertThat(newNotes(c).getChange().currentPatchSetId().get()).isEqualTo(1);

    // Delete PS1, PS2 becomes current.
    update = newUpdate(c, changeOwner);
    update.setPatchSetId(new PatchSet.Id(c.getId(), 1));
    update.setPatchSetState(PatchSetState.DELETED);
    update.commit();
    assertThat(newNotes(c).getChange().currentPatchSetId().get()).isEqualTo(2);
  }

  @Test
  public void readOnlyUntilExpires() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    Timestamp until = new Timestamp(TimeUtil.nowMs() + 10000);
    update.setReadOnlyUntil(until);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.setTopic("failing-topic");
    try {
      update.commit();
      assert_().fail("expected OrmException");
    } catch (OrmException e) {
      assertThat(e.getMessage()).contains("read-only until");
    }

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isNotEqualTo("failing-topic");
    assertThat(notes.getReadOnlyUntil()).isEqualTo(until);

    TestTimeUtil.incrementClock(30, TimeUnit.SECONDS);
    update = newUpdate(c, changeOwner);
    update.setTopic("succeeding-topic");
    update.commit();

    // Write succeeded; lease still exists, even though it's expired.
    notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isEqualTo("succeeding-topic");
    assertThat(notes.getReadOnlyUntil()).isEqualTo(until);

    // New lease takes precedence.
    update = newUpdate(c, changeOwner);
    until = new Timestamp(TimeUtil.nowMs() + 10000);
    update.setReadOnlyUntil(until);
    update.commit();
    assertThat(newNotes(c).getReadOnlyUntil()).isEqualTo(until);
  }

  @Test
  public void readOnlyUntilCleared() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    Timestamp until = new Timestamp(TimeUtil.nowMs() + TimeUnit.DAYS.toMillis(30));
    update.setReadOnlyUntil(until);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.setTopic("failing-topic");
    try {
      update.commit();
      assert_().fail("expected OrmException");
    } catch (OrmException e) {
      assertThat(e.getMessage()).contains("read-only until");
    }

    // Sentinel timestamp of 0 can be written to clear lease.
    update = newUpdate(c, changeOwner);
    update.setReadOnlyUntil(new Timestamp(0));
    update.commit();

    update = newUpdate(c, changeOwner);
    update.setTopic("succeeding-topic");
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getChange().getTopic()).isEqualTo("succeeding-topic");
    assertThat(notes.getReadOnlyUntil()).isEqualTo(new Timestamp(0));
  }

  @Test
  public void privateDefault() throws Exception {
    Change c = newChange();
    ChangeNotes notes = newNotes(c);
    assertThat(notes.isPrivate()).isFalse();
  }

  @Test
  public void privateSetPrivate() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setPrivate(true);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.isPrivate()).isTrue();
  }

  @Test
  public void privateSetPrivateMultipleTimes() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setPrivate(true);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.setPrivate(false);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.isPrivate()).isFalse();
  }

  @Test
  public void defaultReviewersByEmailIsEmpty() throws Exception {
    Change c = newChange();
    ChangeNotes notes = newNotes(c);
    assertThat(notes.getReviewersByEmail().all()).isEmpty();
  }

  @Test
  public void putReviewerByEmail() throws Exception {
    Address adr = new Address("Foo Bar", "foo.bar@gerritcodereview.com");

    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewerByEmail(adr, ReviewerStateInternal.REVIEWER);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getReviewersByEmail().all()).containsExactly(adr);
  }

  @Test
  public void putAndRemoveReviewerByEmail() throws Exception {
    Address adr = new Address("Foo Bar", "foo.bar@gerritcodereview.com");

    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewerByEmail(adr, ReviewerStateInternal.REVIEWER);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.removeReviewerByEmail(adr);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getReviewersByEmail().all()).isEmpty();
  }

  @Test
  public void putRemoveAndAddBackReviewerByEmail() throws Exception {
    Address adr = new Address("Foo Bar", "foo.bar@gerritcodereview.com");

    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewerByEmail(adr, ReviewerStateInternal.REVIEWER);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.removeReviewerByEmail(adr);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.putReviewerByEmail(adr, ReviewerStateInternal.CC);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getReviewersByEmail().all()).containsExactly(adr);
  }

  @Test
  public void putReviewerByEmailAndCcByEmail() throws Exception {
    Address adrReviewer = new Address("Foo Bar", "foo.bar@gerritcodereview.com");
    Address adrCc = new Address("Foo Bor", "foo.bar.2@gerritcodereview.com");

    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewerByEmail(adrReviewer, ReviewerStateInternal.REVIEWER);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.putReviewerByEmail(adrCc, ReviewerStateInternal.CC);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getReviewersByEmail().byState(ReviewerStateInternal.REVIEWER))
        .containsExactly(adrReviewer);
    assertThat(notes.getReviewersByEmail().byState(ReviewerStateInternal.CC))
        .containsExactly(adrCc);
    assertThat(notes.getReviewersByEmail().all()).containsExactly(adrReviewer, adrCc);
  }

  @Test
  public void putReviewerByEmailAndChangeToCc() throws Exception {
    Address adr = new Address("Foo Bar", "foo.bar@gerritcodereview.com");

    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewerByEmail(adr, ReviewerStateInternal.REVIEWER);
    update.commit();

    update = newUpdate(c, changeOwner);
    update.putReviewerByEmail(adr, ReviewerStateInternal.CC);
    update.commit();

    ChangeNotes notes = newNotes(c);
    assertThat(notes.getReviewersByEmail().byState(ReviewerStateInternal.REVIEWER)).isEmpty();
    assertThat(notes.getReviewersByEmail().byState(ReviewerStateInternal.CC)).containsExactly(adr);
    assertThat(notes.getReviewersByEmail().all()).containsExactly(adr);
  }

  @Test
  public void hasReviewStarted() throws Exception {
    ChangeNotes notes = newNotes(newChange());
    assertThat(notes.hasReviewStarted()).isTrue();

    notes = newNotes(newWorkInProgressChange());
    assertThat(notes.hasReviewStarted()).isFalse();

    Change c = newWorkInProgressChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.hasReviewStarted()).isFalse();

    update = newUpdate(c, changeOwner);
    update.setWorkInProgress(true);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.hasReviewStarted()).isFalse();

    update = newUpdate(c, changeOwner);
    update.setWorkInProgress(false);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.hasReviewStarted()).isTrue();

    // Once review is started, setting WIP should have no impact.
    c = newChange();
    notes = newNotes(c);
    assertThat(notes.hasReviewStarted()).isTrue();
    update = newUpdate(c, changeOwner);
    update.setWorkInProgress(true);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.hasReviewStarted()).isTrue();
  }

  @Test
  public void pendingReviewers() throws Exception {
    Address adr1 = new Address("Foo Bar1", "foo.bar1@gerritcodereview.com");
    Address adr2 = new Address("Foo Bar2", "foo.bar2@gerritcodereview.com");
    Account.Id ownerId = changeOwner.getAccount().getId();
    Account.Id otherUserId = otherUser.getAccount().getId();

    ChangeNotes notes = newNotes(newChange());
    assertThat(notes.getPendingReviewers().asTable()).isEmpty();
    assertThat(notes.getPendingReviewersByEmail().asTable()).isEmpty();

    Change c = newWorkInProgressChange();
    notes = newNotes(c);
    assertThat(notes.getPendingReviewers().asTable()).isEmpty();
    assertThat(notes.getPendingReviewersByEmail().asTable()).isEmpty();

    ChangeUpdate update = newUpdate(c, changeOwner);
    update.putReviewer(ownerId, REVIEWER);
    update.putReviewer(otherUserId, CC);
    update.putReviewerByEmail(adr1, REVIEWER);
    update.putReviewerByEmail(adr2, CC);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getPendingReviewers().byState(REVIEWER)).containsExactly(ownerId);
    assertThat(notes.getPendingReviewers().byState(CC)).containsExactly(otherUserId);
    assertThat(notes.getPendingReviewers().byState(REMOVED)).isEmpty();
    assertThat(notes.getPendingReviewersByEmail().byState(REVIEWER)).containsExactly(adr1);
    assertThat(notes.getPendingReviewersByEmail().byState(CC)).containsExactly(adr2);
    assertThat(notes.getPendingReviewersByEmail().byState(REMOVED)).isEmpty();

    update = newUpdate(c, changeOwner);
    update.removeReviewer(ownerId);
    update.removeReviewerByEmail(adr1);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getPendingReviewers().byState(REVIEWER)).isEmpty();
    assertThat(notes.getPendingReviewers().byState(CC)).containsExactly(otherUserId);
    assertThat(notes.getPendingReviewers().byState(REMOVED)).containsExactly(ownerId);
    assertThat(notes.getPendingReviewersByEmail().byState(REVIEWER)).isEmpty();
    assertThat(notes.getPendingReviewersByEmail().byState(CC)).containsExactly(adr2);
    assertThat(notes.getPendingReviewersByEmail().byState(REMOVED)).containsExactly(adr1);

    update = newUpdate(c, changeOwner);
    update.setWorkInProgress(false);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getPendingReviewers().asTable()).isEmpty();
    assertThat(notes.getPendingReviewersByEmail().asTable()).isEmpty();

    update = newUpdate(c, changeOwner);
    update.putReviewer(ownerId, REVIEWER);
    update.putReviewerByEmail(adr1, REVIEWER);
    update.commit();
    notes = newNotes(c);
    assertThat(notes.getPendingReviewers().asTable()).isEmpty();
    assertThat(notes.getPendingReviewersByEmail().asTable()).isEmpty();
  }

  private boolean testJson() {
    return noteUtil.getWriteJson();
  }

  private String readNote(ChangeNotes notes, ObjectId noteId) throws Exception {
    ObjectId dataId = notes.revisionNoteMap.noteMap.getNote(noteId).getData();
    return new String(rw.getObjectReader().open(dataId, OBJ_BLOB).getCachedBytes(), UTF_8);
  }

  private ObjectId exactRefAllUsers(String refName) throws Exception {
    try (Repository allUsersRepo = repoManager.openRepository(allUsers)) {
      Ref ref = allUsersRepo.exactRef(refName);
      return ref != null ? ref.getObjectId() : null;
    }
  }

  private void assertCause(
      Throwable e, Class<? extends Throwable> expectedClass, String expectedMsg) {
    Throwable cause = null;
    for (Throwable t : Throwables.getCausalChain(e)) {
      if (expectedClass.isAssignableFrom(t.getClass())) {
        cause = t;
        break;
      }
    }
    assertThat(cause)
        .named(
            expectedClass.getSimpleName()
                + " in causal chain of:\n"
                + Throwables.getStackTraceAsString(e))
        .isNotNull();
    assertThat(cause.getMessage()).isEqualTo(expectedMsg);
  }

  private void incrementCurrentPatchSetFieldOnly(Change c) {
    TestChanges.incrementPatchSet(c);
  }

  private RevCommit incrementPatchSet(Change c) throws Exception {
    return incrementPatchSet(c, userFactory.create(c.getOwner()));
  }

  private RevCommit incrementPatchSet(Change c, IdentifiedUser user) throws Exception {
    incrementCurrentPatchSetFieldOnly(c);
    RevCommit commit = tr.commit().message("PS" + c.currentPatchSetId().get()).create();
    ChangeUpdate update = newUpdate(c, user);
    update.setCommit(rw, commit);
    update.commit();
    return tr.parseBody(commit);
  }
}
