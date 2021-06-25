// Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.entities.LabelId.CODE_REVIEW;
import static com.google.gerrit.entities.LabelId.VERIFIED;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REMOVED;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.notedb.CommitRewriter.BackfillResult;
import com.google.gerrit.server.notedb.CommitRewriter.RunOptions;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CommitRewriter} */
public class CommitRewriterTest extends AbstractChangeNotesTest {

  private @Inject CommitRewriter rewriter;
  @Inject private ChangeNoteUtil changeNoteUtil;

  @Before
  public void setUp() throws Exception {}

  @Test
  public void validHistoryNoOp() throws Exception {
    String tag = "jenkins";
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("verification from jenkins");
    update.setTag(tag);
    update.commit();

    ChangeUpdate updateWithSubject = newUpdate(c, changeOwner);
    updateWithSubject.setSubjectForCommit("Update with subject");
    updateWithSubject.commit();

    ChangeNotes notesBeforeRewrite = newNotes(c);
    Ref metaRefBefore = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult backfillResult = rewriter.backfillProject(project, repo, options);
    ChangeNotes notesAfterRewrite = newNotes(c);
    Ref metaRefAfter = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    assertThat(notesBeforeRewrite.getMetaId()).isEqualTo(notesAfterRewrite.getMetaId());
    assertThat(metaRefBefore.getObjectId()).isEqualTo(metaRefAfter.getObjectId());
    assertThat(backfillResult.fixedRefDiff).isEmpty();
  }

  @Test
  public void failedVerification() throws Exception {
    String tag = "jenkins";
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("Unknown commit " + changeOwner.getName());
    update.setTag(tag);
    update.commit();

    ChangeUpdate updateWithSubject = newUpdate(c, changeOwner);
    updateWithSubject.setSubjectForCommit("Update with subject");
    updateWithSubject.commit();

    ChangeNotes notesBeforeRewrite = newNotes(c);
    Ref metaRefBefore = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult backfillResult = rewriter.backfillProject(project, repo, options);
    assertThat(backfillResult.fixedRefDiff).isEmpty();
    assertThat(backfillResult.refsStillInvalidAfterFix)
        .containsExactly(RefNames.changeMetaRef(c.getId()));
    ChangeNotes notesAfterRewrite = newNotes(c);
    Ref metaRefAfter = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    assertThat(notesBeforeRewrite.getMetaId()).isEqualTo(notesAfterRewrite.getMetaId());
    assertThat(metaRefBefore.getObjectId()).isEqualTo(metaRefAfter.getObjectId());
  }

  @Test
  public void fixAuthorIdent() throws Exception {
    Change c = newChange();
    Timestamp when = TimeUtil.nowTs();
    PersonIdent invalidAuthorIdent =
        new PersonIdent(
            changeOwner.getName(),
            changeNoteUtil.getAccountIdAsEmailAddress(changeOwner.getAccountId()),
            when,
            serverIdent.getTimeZone());
    RevCommit invalidUpdateCommit =
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            getChangeUpdateBody(c, /*changeMessage=*/ null),
            invalidAuthorIdent);
    ChangeUpdate validUpdate = newUpdate(c, changeOwner);
    validUpdate.setChangeMessage("verification from jenkins");
    validUpdate.setTag("jenkins");
    validUpdate.commit();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);
    ChangeNotes notesBeforeRewrite = newNotes(c);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    ChangeNotes notesAfterRewrite = newNotes(c);

    assertThat(notesAfterRewrite.getChange().getOwner())
        .isEqualTo(notesBeforeRewrite.getChange().getOwner());
    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());
    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    int invalidCommitIndex = commitsBeforeRewrite.indexOf(invalidUpdateCommit);

    assertValidCommits(
        commitsBeforeRewrite, commitsAfterRewrite, ImmutableList.of(invalidCommitIndex));
    RevCommit fixedUpdateCommit = commitsAfterRewrite.get(invalidCommitIndex);
    PersonIdent originalAuthorIdent = invalidUpdateCommit.getAuthorIdent();
    PersonIdent fixedAuthorIdent = fixedUpdateCommit.getAuthorIdent();
    assertThat(originalAuthorIdent).isNotEqualTo(fixedAuthorIdent);
    assertThat(fixedUpdateCommit.getAuthorIdent().getName())
        .isEqualTo("Gerrit User " + changeOwner.getAccountId());
    assertThat(originalAuthorIdent.getEmailAddress()).isEqualTo(fixedAuthorIdent.getEmailAddress());
    assertThat(originalAuthorIdent.getWhen()).isEqualTo(fixedAuthorIdent.getWhen());
    assertThat(originalAuthorIdent.getTimeZone()).isEqualTo(fixedAuthorIdent.getTimeZone());
    assertThat(invalidUpdateCommit.getFullMessage()).isEqualTo(fixedUpdateCommit.getFullMessage());
    assertThat(invalidUpdateCommit.getCommitterIdent())
        .isEqualTo(fixedUpdateCommit.getCommitterIdent());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(changeOwner.getName());
    List<String> commitHistoryDiff = result.fixedRefDiff.get(RefNames.changeMetaRef(c.getId()));
    assertThat(commitHistoryDiff).hasSize(1);
    assertThat(commitHistoryDiff.get(0)).contains("-author Change Owner <1@gerrit>");
    assertThat(commitHistoryDiff.get(0)).contains("+author Gerrit User 1 <1@gerrit>");
  }

  @Test
  public void fixRealUserFooterIdent() throws Exception {
    Change c = newChange();

    String realUserIdentToFix = getAccountIdentToFix(otherUser.getAccount());
    RevCommit invalidUpdateCommit =
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            getChangeUpdateBody(c, "Comment on behalf of user", "Real-user: " + realUserIdentToFix),
            getAuthorIdent(changeOwner.getAccount()));

    IdentifiedUser impersonatedChangeOwner =
        this.userFactory.runAs(
            null, changeOwner.getAccountId(), requireNonNull(otherUser).getRealUser());
    ChangeUpdate impersonatedChangeMessageUpdate = newUpdate(c, impersonatedChangeOwner);
    impersonatedChangeMessageUpdate.setChangeMessage("Other comment on behalf of");
    impersonatedChangeMessageUpdate.commit();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    int invalidCommitIndex = commitsBeforeRewrite.indexOf(invalidUpdateCommit);
    ChangeNotes notesBeforeRewrite = newNotes(c);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    ChangeNotes notesAfterRewrite = newNotes(c);
    assertThat(changeMessages(notesBeforeRewrite))
        .containsExactly("Comment on behalf of user", "Other comment on behalf of");
    assertThat(notesBeforeRewrite.getChangeMessages().get(0).getAuthor())
        .isEqualTo(changeOwner.getAccountId());
    assertThat(notesBeforeRewrite.getChangeMessages().get(0).getRealAuthor())
        .isEqualTo(otherUser.getAccountId());
    assertThat(changeMessages(notesAfterRewrite))
        .containsExactly("Comment on behalf of user", "Other comment on behalf of");
    assertThat(notesBeforeRewrite.getChangeMessages().get(0).getAuthor())
        .isEqualTo(changeOwner.getAccountId());
    assertThat(notesBeforeRewrite.getChangeMessages().get(0).getRealAuthor())
        .isEqualTo(otherUser.getAccountId());

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(
        commitsBeforeRewrite, commitsAfterRewrite, ImmutableList.of(invalidCommitIndex));

    List<String> commitHistoryDiff = result.fixedRefDiff.get(RefNames.changeMetaRef(c.getId()));
    assertThat(commitHistoryDiff).hasSize(1);
    assertThat(commitHistoryDiff.get(0))
        .isEqualTo(
            "@@ -9 +9 @@\n"
                + "-Real-user: Other Account <2@gerrit>\n"
                + "+Real-user: Gerrit User 2 <2@gerrit>\n");
  }

  @Test
  public void fixReviewerFooterIdent() throws Exception {
    Change c = newChange();
    String reviewerIdentToFix = getAccountIdentToFix(otherUser.getAccount());
    ImmutableList<RevCommit> commitsToFix =
        new ImmutableList.Builder<RevCommit>()
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(
                        c, /*changeMessage=*/ null, "Reviewer: " + reviewerIdentToFix),
                    getAuthorIdent(changeOwner.getAccount())))
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(c, /*changeMessage=*/ null, "CC: " + reviewerIdentToFix),
                    getAuthorIdent(otherUser.getAccount())))
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(c, "Removed cc", "Removed: " + reviewerIdentToFix),
                    getAuthorIdent(changeOwner.getAccount())))
            .build();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    ImmutableList<Integer> invalidCommits =
        commitsToFix.stream()
            .map(commit -> commitsBeforeRewrite.indexOf(commit))
            .collect(toImmutableList());
    ChangeNotes notesBeforeRewrite = newNotes(c);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    Timestamp updateTimestamp = new Timestamp(serverIdent.getWhen().getTime());
    ImmutableList<ReviewerStatusUpdate> expectedReviewerUpdates =
        ImmutableList.of(
            ReviewerStatusUpdate.create(
                updateTimestamp, changeOwner.getAccountId(), otherUserId, REVIEWER),
            ReviewerStatusUpdate.create(updateTimestamp, otherUserId, otherUserId, CC),
            ReviewerStatusUpdate.create(
                updateTimestamp, changeOwner.getAccountId(), otherUserId, REMOVED));
    ChangeNotes notesAfterRewrite = newNotes(c);

    assertThat(notesBeforeRewrite.getReviewerUpdates()).isEqualTo(expectedReviewerUpdates);
    assertThat(notesAfterRewrite.getReviewerUpdates()).isEqualTo(expectedReviewerUpdates);

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);

    List<String> commitHistoryDiff = result.fixedRefDiff.get(RefNames.changeMetaRef(c.getId()));
    assertThat(commitHistoryDiff).hasSize(3);
    assertThat(commitHistoryDiff.get(0))
        .isEqualTo(
            "@@ -7 +7 @@\n"
                + "-Reviewer: Other Account <2@gerrit>\n"
                + "+Reviewer: Gerrit User 2 <2@gerrit>\n");
    assertThat(commitHistoryDiff.get(1))
        .isEqualTo(
            "@@ -7 +7 @@\n"
                + "-CC: Other Account <2@gerrit>\n"
                + "+CC: Gerrit User 2 <2@gerrit>\n");
    assertThat(commitHistoryDiff.get(2))
        .isEqualTo(
            "@@ -9 +9 @@\n"
                + "-Removed: Other Account <2@gerrit>\n"
                + "+Removed: Gerrit User 2 <2@gerrit>\n");
  }

  @Test
  public void fixReviewerMessage() throws Exception {
    Change c = newChange();
    ImmutableList.Builder<RevCommit> commitsToFix = new ImmutableList.Builder<>();
    ChangeUpdate addReviewerUpdate = newUpdate(c, changeOwner);
    addReviewerUpdate.putReviewer(otherUserId, REVIEWER);
    addReviewerUpdate.commit();

    commitsToFix.add(
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            getChangeUpdateBody(
                c,
                "Removed reviewer " + otherUser.getAccount().fullName(),
                "Removed: " + getValidIdentAsString(otherUser.getAccount())),
            getAuthorIdent(changeOwner.getAccount())));

    ChangeUpdate addCcUpdate = newUpdate(c, changeOwner);
    addCcUpdate.putReviewer(otherUserId, CC);
    addCcUpdate.commit();

    commitsToFix.add(
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            getChangeUpdateBody(
                c,
                "Removed cc " + otherUser.getAccount().fullName(),
                "Removed: " + getValidIdentAsString(otherUser.getAccount())),
            getAuthorIdent(changeOwner.getAccount())));

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    ImmutableList<Integer> invalidCommits =
        commitsToFix.build().stream()
            .map(commit -> commitsBeforeRewrite.indexOf(commit))
            .collect(toImmutableList());
    ChangeNotes notesBeforeRewrite = newNotes(c);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    Timestamp updateTimestamp = new Timestamp(serverIdent.getWhen().getTime());
    ImmutableList<ReviewerStatusUpdate> expectedReviewerUpdates =
        ImmutableList.of(
            ReviewerStatusUpdate.create(
                new Timestamp(addReviewerUpdate.when.getTime()),
                changeOwner.getAccountId(),
                otherUserId,
                REVIEWER),
            ReviewerStatusUpdate.create(
                updateTimestamp, changeOwner.getAccountId(), otherUserId, REMOVED),
            ReviewerStatusUpdate.create(
                new Timestamp(addCcUpdate.when.getTime()),
                changeOwner.getAccountId(),
                otherUserId,
                CC),
            ReviewerStatusUpdate.create(
                updateTimestamp, changeOwner.getAccountId(), otherUserId, REMOVED));
    ChangeNotes notesAfterRewrite = newNotes(c);

    assertThat(notesBeforeRewrite.getReviewerUpdates()).isEqualTo(expectedReviewerUpdates);
    assertThat(changeMessages(notesBeforeRewrite))
        .containsExactly("Removed reviewer Other Account", "Removed cc Other Account");
    assertThat(notesAfterRewrite.getReviewerUpdates()).isEqualTo(expectedReviewerUpdates);
    assertThat(changeMessages(notesAfterRewrite)).containsExactly("Removed reviewer", "Removed cc");

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);

    List<String> commitHistoryDiff = result.fixedRefDiff.get(RefNames.changeMetaRef(c.getId()));
    assertThat(commitHistoryDiff).hasSize(2);
    assertThat(commitHistoryDiff.get(0))
        .isEqualTo("@@ -6 +6 @@\n" + "-Removed reviewer Other Account\n" + "+Removed reviewer\n");
    assertThat(commitHistoryDiff.get(1))
        .isEqualTo("@@ -6 +6 @@\n" + "-Removed cc Other Account\n" + "+Removed cc\n");
  }

  @Test
  public void fixLabelFooterIdent() throws Exception {
    Change c = newChange();
    String approverIdentToFix = getAccountIdentToFix(otherUser.getAccount());
    String changeOwnerIdentToFix = getAccountIdentToFix(changeOwner.getAccount());
    ChangeUpdate approvalUpdateByOtherUser = newUpdate(c, otherUser);
    approvalUpdateByOtherUser.putApproval(VERIFIED, (short) -1);
    approvalUpdateByOtherUser.commit();

    ImmutableList<RevCommit> commitsToFix =
        new ImmutableList.Builder<RevCommit>()
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(
                        c,
                        /*changeMessage=*/ null,
                        "Label: -Verified " + approverIdentToFix,
                        "Label: Custom-Label-1=-1 " + approverIdentToFix,
                        "Label: Verified=+1",
                        "Label: Custom-Label-1=+1",
                        "Label: Custom-Label-2=+2 " + approverIdentToFix,
                        "Label: Custom-Label-3=0 " + approverIdentToFix),
                    getAuthorIdent(changeOwner.getAccount())))
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(
                        c,
                        /*changeMessage=*/ null,
                        "Label: -Verified " + changeOwnerIdentToFix,
                        "Label: Custom-Label-1=+1"),
                    getAuthorIdent(otherUser.getAccount())))
            .build();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    ImmutableList<Integer> invalidCommits =
        commitsToFix.stream()
            .map(commit -> commitsBeforeRewrite.indexOf(commit))
            .collect(toImmutableList());
    ChangeNotes notesBeforeRewrite = newNotes(c);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    Timestamp updateTimestamp = new Timestamp(serverIdent.getWhen().getTime());
    ImmutableList<PatchSetApproval> expectedApprovals =
        ImmutableList.of(
            PatchSetApproval.builder()
                .key(
                    PatchSetApproval.key(
                        c.currentPatchSetId(),
                        changeOwner.getAccountId(),
                        LabelId.create(VERIFIED)))
                .value(0)
                .granted(updateTimestamp)
                .build(),
            PatchSetApproval.builder()
                .key(
                    PatchSetApproval.key(
                        c.currentPatchSetId(),
                        changeOwner.getAccountId(),
                        LabelId.create("Custom-Label-1")))
                .value(+1)
                .granted(updateTimestamp)
                .build(),
            PatchSetApproval.builder()
                .key(
                    PatchSetApproval.key(
                        c.currentPatchSetId(), otherUserId, LabelId.create(VERIFIED)))
                .value(0)
                .granted(updateTimestamp)
                .build(),
            PatchSetApproval.builder()
                .key(
                    PatchSetApproval.key(
                        c.currentPatchSetId(), otherUserId, LabelId.create("Custom-Label-1")))
                .value(+1)
                .granted(updateTimestamp)
                .build(),
            PatchSetApproval.builder()
                .key(
                    PatchSetApproval.key(
                        c.currentPatchSetId(), otherUserId, LabelId.create("Custom-Label-2")))
                .value(+2)
                .granted(updateTimestamp)
                .build(),
            PatchSetApproval.builder()
                .key(
                    PatchSetApproval.key(
                        c.currentPatchSetId(), otherUserId, LabelId.create("Custom-Label-3")))
                .value(0)
                .granted(updateTimestamp)
                .build());
    ChangeNotes notesAfterRewrite = newNotes(c);

    assertThat(notesBeforeRewrite.getApprovals().get(c.currentPatchSetId()))
        .containsExactlyElementsIn(expectedApprovals);
    assertThat(notesAfterRewrite.getApprovals().get(c.currentPatchSetId()))
        .containsExactlyElementsIn(expectedApprovals);

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);

    List<String> commitHistoryDiff = result.fixedRefDiff.get(RefNames.changeMetaRef(c.getId()));
    assertThat(commitHistoryDiff).hasSize(2);
    assertThat(commitHistoryDiff.get(0))
        .isEqualTo(
            "@@ -7,2 +7,2 @@\n"
                + "-Label: -Verified Other Account <2@gerrit>\n"
                + "-Label: Custom-Label-1=-1 Other Account <2@gerrit>\n"
                + "+Label: -Verified Gerrit User 2 <2@gerrit>\n"
                + "+Label: Custom-Label-1=-1 Gerrit User 2 <2@gerrit>\n"
                + "@@ -11,2 +11,2 @@\n"
                + "-Label: Custom-Label-2=+2 Other Account <2@gerrit>\n"
                + "-Label: Custom-Label-3=0 Other Account <2@gerrit>\n"
                + "+Label: Custom-Label-2=+2 Gerrit User 2 <2@gerrit>\n"
                + "+Label: Custom-Label-3=0 Gerrit User 2 <2@gerrit>\n");
    assertThat(commitHistoryDiff.get(1))
        .isEqualTo(
            "@@ -7 +7 @@\n"
                + "-Label: -Verified Change Owner <1@gerrit>\n"
                + "+Label: -Verified Gerrit User 1 <1@gerrit>\n");
  }

  @Test
  public void fixRemoveVoteChangeMessage() throws Exception {
    Change c = newChange();
    String approverIdentToFix = getAccountIdentToFix(otherUser.getAccount());
    ChangeUpdate approvalUpdateByOtherUser = newUpdate(c, otherUser);
    approvalUpdateByOtherUser.putApproval(CODE_REVIEW, (short) +2);
    approvalUpdateByOtherUser.putApproval("Custom-Label", (short) -1);
    approvalUpdateByOtherUser.putApprovalFor(changeOwner.getAccountId(), VERIFIED, (short) -1);
    approvalUpdateByOtherUser.commit();

    ImmutableList<RevCommit> commitsToFix =
        new ImmutableList.Builder<RevCommit>()
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(
                        c,
                        /*changeMessage=*/ "Removed Code-Review+2 by " + otherUser.getNameEmail(),
                        "Label: -Code-Review " + approverIdentToFix),
                    getAuthorIdent(changeOwner.getAccount())))
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(
                        c,
                        /*changeMessage=*/ "Removed Custom-Label-1 by " + otherUser.getNameEmail(),
                        "Label: -Custom-Label " + getValidIdentAsString(otherUser.getAccount())),
                    getAuthorIdent(changeOwner.getAccount())))
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(
                        c,
                        /*changeMessage=*/ "Removed Verified+2 by " + changeOwner.getNameEmail(),
                        "Label: -Verified"),
                    getAuthorIdent(changeOwner.getAccount())))
            .build();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    ImmutableList<Integer> invalidCommits =
        commitsToFix.stream()
            .map(commit -> commitsBeforeRewrite.indexOf(commit))
            .collect(toImmutableList());
    ChangeNotes notesBeforeRewrite = newNotes(c);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    Timestamp updateTimestamp = new Timestamp(serverIdent.getWhen().getTime());
    ImmutableList<PatchSetApproval> expectedApprovals =
        ImmutableList.of(
            PatchSetApproval.builder()
                .key(
                    PatchSetApproval.key(
                        c.currentPatchSetId(),
                        changeOwner.getAccountId(),
                        LabelId.create(VERIFIED)))
                .value(0)
                .granted(updateTimestamp)
                .build(),
            PatchSetApproval.builder()
                .key(
                    PatchSetApproval.key(
                        c.currentPatchSetId(), otherUserId, LabelId.create("Custom-Label")))
                .value(0)
                .granted(updateTimestamp)
                .build(),
            PatchSetApproval.builder()
                .key(
                    PatchSetApproval.key(
                        c.currentPatchSetId(), otherUserId, LabelId.create(CODE_REVIEW)))
                .value(0)
                .granted(updateTimestamp)
                .build());
    ChangeNotes notesAfterRewrite = newNotes(c);
    assertThat(changeMessages(notesBeforeRewrite))
        .containsExactly(
            "Removed Code-Review+2 by Other Account <other@account.com>",
            "Removed Custom-Label-1 by Other Account <other@account.com>",
            "Removed Verified+2 by Change Owner <change@owner.com>");

    assertThat(notesBeforeRewrite.getApprovals().get(c.currentPatchSetId()))
        .containsExactlyElementsIn(expectedApprovals);
    assertThat(changeMessages(notesAfterRewrite))
        .containsExactly(
            "Removed Code-Review+2 by <GERRIT_ACCOUNT_2>",
            "Removed Custom-Label-1 by <GERRIT_ACCOUNT_2>",
            "Removed Verified+2 by <GERRIT_ACCOUNT_1>");
    assertThat(notesAfterRewrite.getApprovals().get(c.currentPatchSetId()))
        .containsExactlyElementsIn(expectedApprovals);

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);

    List<String> commitHistoryDiff = result.fixedRefDiff.get(RefNames.changeMetaRef(c.getId()));
    assertThat(commitHistoryDiff).hasSize(3);
    assertThat(commitHistoryDiff.get(0))
        .isEqualTo(
            "@@ -6 +6 @@\n"
                + "-Removed Code-Review+2 by Other Account <other@account.com>\n"
                + "+Removed Code-Review+2 by <GERRIT_ACCOUNT_2>\n"
                + "@@ -9 +9 @@\n"
                + "-Label: -Code-Review Other Account <2@gerrit>\n"
                + "+Label: -Code-Review Gerrit User 2 <2@gerrit>\n");
    assertThat(commitHistoryDiff.get(1))
        .isEqualTo(
            "@@ -6 +6 @@\n"
                + "-Removed Custom-Label-1 by Other Account <other@account.com>\n"
                + "+Removed Custom-Label-1 by <GERRIT_ACCOUNT_2>\n");
    assertThat(commitHistoryDiff.get(2))
        .isEqualTo(
            "@@ -6 +6 @@\n"
                + "-Removed Verified+2 by Change Owner <change@owner.com>\n"
                + "+Removed Verified+2 by <GERRIT_ACCOUNT_1>\n");
  }

  @Test
  public void fixAttentionFooterIdent() throws Exception {
    // TODO(mariasavtchouk): add once backfilling is implemented for this case.
  }

  @Test
  public void fixSubmitChangeMessage() throws Exception {
    Change c = newChange();
    ImmutableList.Builder<ObjectId> commitsToFix = new ImmutableList.Builder<>();
    ChangeUpdate invalidMergedMessageUpdate = newUpdate(c, changeOwner);
    invalidMergedMessageUpdate.setChangeMessage(
        "Change has been successfully merged by " + changeOwner.getName());
    invalidMergedMessageUpdate.setTag(ChangeMessagesUtil.TAG_MERGED);
    commitsToFix.add(invalidMergedMessageUpdate.commit());
    ChangeUpdate invalidCherryPickedMessageUpdate = newUpdate(c, changeOwner);
    invalidCherryPickedMessageUpdate.setChangeMessage(
        "Change has been successfully cherry-picked as e40dc1a50dc7f457a37579e2755374f3e1a5413b by "
            + changeOwner.getName());
    invalidCherryPickedMessageUpdate.setTag(ChangeMessagesUtil.TAG_MERGED);
    commitsToFix.add(invalidCherryPickedMessageUpdate.commit());
    ChangeUpdate invalidRebasedMessageUpdate = newUpdate(c, changeOwner);
    invalidRebasedMessageUpdate.setChangeMessage(
        "Change has been successfully rebased and submitted as e40dc1a50dc7f457a37579e2755374f3e1a5413b by "
            + changeOwner.getName());
    invalidRebasedMessageUpdate.setTag(ChangeMessagesUtil.TAG_MERGED);
    commitsToFix.add(invalidRebasedMessageUpdate.commit());
    ChangeUpdate validSubmitMessageUpdate = newUpdate(c, changeOwner);
    validSubmitMessageUpdate.setChangeMessage(
        "Change has been successfully rebased and submitted as e40dc1a50dc7f457a37579e2755374f3e1a5413b");
    validSubmitMessageUpdate.commit();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    ImmutableList<Integer> invalidCommits =
        commitsToFix.build().stream()
            .map(commit -> commitsBeforeRewrite.indexOf(commit))
            .collect(toImmutableList());
    ChangeNotes notesBeforeRewrite = newNotes(c);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    ChangeNotes notesAfterRewrite = newNotes(c);

    assertThat(changeMessages(notesBeforeRewrite))
        .containsExactly(
            "Change has been successfully merged by Change Owner",
            "Change has been successfully cherry-picked as e40dc1a50dc7f457a37579e2755374f3e1a5413b by Change Owner",
            "Change has been successfully rebased and submitted as e40dc1a50dc7f457a37579e2755374f3e1a5413b by Change Owner",
            "Change has been successfully rebased and submitted as e40dc1a50dc7f457a37579e2755374f3e1a5413b");
    assertThat(changeMessages(notesAfterRewrite))
        .containsExactly(
            "Change has been successfully merged",
            "Change has been successfully cherry-picked as e40dc1a50dc7f457a37579e2755374f3e1a5413b",
            "Change has been successfully rebased and submitted as e40dc1a50dc7f457a37579e2755374f3e1a5413b",
            "Change has been successfully rebased and submitted as e40dc1a50dc7f457a37579e2755374f3e1a5413b");

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);

    List<String> commitHistoryDiff = result.fixedRefDiff.get(RefNames.changeMetaRef(c.getId()));
    assertThat(commitHistoryDiff).hasSize(3);
    assertThat(commitHistoryDiff.get(0))
        .isEqualTo(
            "@@ -6 +6 @@\n"
                + "-Change has been successfully merged by Change Owner\n"
                + "+Change has been successfully merged\n");
    assertThat(commitHistoryDiff.get(1))
        .isEqualTo(
            "@@ -6 +6 @@\n"
                + "-Change has been successfully cherry-picked as e40dc1a50dc7f457a37579e2755374f3e1a5413b by Change Owner\n"
                + "+Change has been successfully cherry-picked as e40dc1a50dc7f457a37579e2755374f3e1a5413b\n");
    assertThat(commitHistoryDiff.get(2))
        .isEqualTo(
            "@@ -6 +6 @@\n"
                + "-Change has been successfully rebased and submitted as e40dc1a50dc7f457a37579e2755374f3e1a5413b by Change Owner\n"
                + "+Change has been successfully rebased and submitted as e40dc1a50dc7f457a37579e2755374f3e1a5413b\n");
  }

  @Test
  public void fixSubmittedWithFooterIdent() throws Exception {
    // TODO(mariasavtchouk): add once backfilling is implemented for this case.
  }

  @Test
  public void fixDeleteChangeMessageCommitMessage() throws Exception {
    Change c = newChange();
    ImmutableList.Builder<ObjectId> commitsToFix = new ImmutableList.Builder<>();
    ChangeUpdate invalidDeleteChangeMessageUpdate = newUpdate(c, changeOwner);
    invalidDeleteChangeMessageUpdate.setChangeMessage(
        "Change message removed by: " + changeOwner.getName());
    commitsToFix.add(invalidDeleteChangeMessageUpdate.commit());
    ChangeUpdate invalidDeleteChangeMessageUpdateWithReason = newUpdate(c, changeOwner);
    invalidDeleteChangeMessageUpdateWithReason.setChangeMessage(
        String.format(
            "Change message removed by: %s\nReason: %s",
            changeOwner.getName(), "contains confidential information"));
    commitsToFix.add(invalidDeleteChangeMessageUpdateWithReason.commit());
    ChangeUpdate validDeleteChangeMessageUpdate = newUpdate(c, changeOwner);
    validDeleteChangeMessageUpdate.setChangeMessage(
        "Change message removed by: <GERRIT_ACCOUNT_1>");
    validDeleteChangeMessageUpdate.commit();
    ChangeUpdate validDeleteChangeMessageUpdateWithReason = newUpdate(c, changeOwner);
    validDeleteChangeMessageUpdateWithReason.setChangeMessage(
        "Change message removed by: <GERRIT_ACCOUNT_1>\nReason: abusive language");
    validDeleteChangeMessageUpdateWithReason.commit();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    ImmutableList<Integer> invalidCommits =
        commitsToFix.build().stream()
            .map(commit -> commitsBeforeRewrite.indexOf(commit))
            .collect(toImmutableList());
    ChangeNotes notesBeforeRewrite = newNotes(c);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    ChangeNotes notesAfterRewrite = newNotes(c);

    assertThat(changeMessages(notesBeforeRewrite))
        .containsExactly(
            "Change message removed by: Change Owner",
            "Change message removed by: Change Owner\n"
                + "Reason: contains confidential information",
            "Change message removed by: <GERRIT_ACCOUNT_1>",
            "Change message removed by: <GERRIT_ACCOUNT_1>\n" + "Reason: abusive language");
    assertThat(changeMessages(notesAfterRewrite))
        .containsExactly(
            "Change message removed",
            "Change message removed\n" + "Reason: contains confidential information",
            "Change message removed by: <GERRIT_ACCOUNT_1>",
            "Change message removed by: <GERRIT_ACCOUNT_1>\n" + "Reason: abusive language");

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);

    List<String> commitHistoryDiff = result.fixedRefDiff.get(RefNames.changeMetaRef(c.getId()));
    assertThat(commitHistoryDiff).hasSize(2);
    assertThat(commitHistoryDiff.get(0))
        .isEqualTo(
            "@@ -6 +6 @@\n"
                + "-Change message removed by: Change Owner\n"
                + "+Change message removed\n");
    assertThat(commitHistoryDiff.get(1))
        .isEqualTo(
            "@@ -6 +6 @@\n"
                + "-Change message removed by: Change Owner\n"
                + "+Change message removed\n");
  }

  @Test
  public void fixCodeOwnersChangeMessage() throws Exception {
    // TODO(mariasavtchouk): add once backfilling is implemented for this case.
  }

  @Test
  public void fixAssigneeFooterIdent() throws Exception {
    Change c = newChange();

    String assigneeIdentToFix = getAccountIdentToFix(changeOwner.getAccount());
    RevCommit invalidUpdateCommit =
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            getChangeUpdateBody(c, "Assignee added", "Assignee: " + assigneeIdentToFix),
            getAuthorIdent(changeOwner.getAccount()));

    ChangeUpdate changeAssigneeUpdate = newUpdate(c, changeOwner);
    changeAssigneeUpdate.setAssignee(otherUserId);
    changeAssigneeUpdate.commit();

    ChangeUpdate removeAssigneeUpdate = newUpdate(c, changeOwner);
    removeAssigneeUpdate.removeAssignee();
    removeAssigneeUpdate.commit();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    int invalidCommitIndex = commitsBeforeRewrite.indexOf(invalidUpdateCommit);
    ChangeNotes notesBeforeRewrite = newNotes(c);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    ChangeNotes notesAfterRewrite = newNotes(c);
    assertThat(notesBeforeRewrite.getPastAssignees())
        .containsExactly(changeOwner.getAccountId(), otherUser.getAccountId());
    assertThat(notesBeforeRewrite.getChange().getAssignee()).isNull();
    assertThat(notesAfterRewrite.getPastAssignees())
        .containsExactly(changeOwner.getAccountId(), otherUser.getAccountId());
    assertThat(notesAfterRewrite.getChange().getAssignee()).isNull();

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(
        commitsBeforeRewrite, commitsAfterRewrite, ImmutableList.of(invalidCommitIndex));

    RevCommit fixedUpdateCommit = commitsAfterRewrite.get(invalidCommitIndex);
    assertThat(invalidUpdateCommit.getAuthorIdent()).isEqualTo(fixedUpdateCommit.getAuthorIdent());
    assertThat(invalidUpdateCommit.getCommitterIdent())
        .isEqualTo(fixedUpdateCommit.getCommitterIdent());
    assertThat(invalidUpdateCommit.getFullMessage())
        .isNotEqualTo(fixedUpdateCommit.getFullMessage());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(changeOwner.getName());
    assertThat(invalidUpdateCommit.getFullMessage()).contains(assigneeIdentToFix);
    String expectedFixedIdent = getValidIdentAsString(changeOwner.getAccount());
    assertThat(fixedUpdateCommit.getFullMessage()).contains(expectedFixedIdent);

    List<String> commitHistoryDiff = result.fixedRefDiff.get(RefNames.changeMetaRef(c.getId()));
    assertThat(commitHistoryDiff).hasSize(1);
    assertThat(commitHistoryDiff.get(0))
        .isEqualTo(
            "@@ -9 +9 @@\n"
                + "-Assignee: Change Owner <1@gerrit>\n"
                + "+Assignee: Gerrit User 1 <1@gerrit>\n");
  }

  @Test
  public void fixAssigneeChangeMessage() throws Exception {
    Change c = newChange();

    ImmutableList<RevCommit> commitsToFix =
        new ImmutableList.Builder<RevCommit>()
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(
                        c,
                        "Assignee added: " + changeOwner.getNameEmail(),
                        "Assignee: " + getValidIdentAsString(changeOwner.getAccount())),
                    getAuthorIdent(changeOwner.getAccount())))
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(
                        c,
                        String.format(
                            "Assignee changed from: %s to: %s",
                            changeOwner.getNameEmail(), otherUser.getNameEmail()),
                        "Assignee: " + getValidIdentAsString(otherUser.getAccount())),
                    getAuthorIdent(changeOwner.getAccount())))
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    getChangeUpdateBody(
                        c, "Assignee deleted: " + otherUser.getNameEmail(), "Assignee:"),
                    getAuthorIdent(changeOwner.getAccount())))
            .build();

    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    ImmutableList<Integer> invalidCommits =
        commitsToFix.stream()
            .map(commit -> commitsBeforeRewrite.indexOf(commit))
            .collect(toImmutableList());
    ChangeNotes notesBeforeRewrite = newNotes(c);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    ChangeNotes notesAfterRewrite = newNotes(c);
    assertThat(notesBeforeRewrite.getPastAssignees())
        .containsExactly(changeOwner.getAccountId(), otherUser.getAccountId());
    assertThat(notesBeforeRewrite.getChange().getAssignee()).isNull();
    assertThat(changeMessages(notesBeforeRewrite))
        .containsExactly(
            "Assignee added: Change Owner <change@owner.com>",
            "Assignee changed from: Change Owner <change@owner.com> to: Other Account <other@account.com>",
            "Assignee deleted: Other Account <other@account.com>");

    assertThat(notesAfterRewrite.getPastAssignees())
        .containsExactly(changeOwner.getAccountId(), otherUser.getAccountId());
    assertThat(notesAfterRewrite.getChange().getAssignee()).isNull();
    assertThat(changeMessages(notesAfterRewrite))
        .containsExactly(
            "Assignee added: " + ChangeMessagesUtil.getAccountTemplate(changeOwner.getAccountId()),
            String.format(
                "Assignee changed from: %s to: %s",
                ChangeMessagesUtil.getAccountTemplate(changeOwner.getAccountId()),
                ChangeMessagesUtil.getAccountTemplate(otherUser.getAccountId())),
            "Assignee deleted: " + ChangeMessagesUtil.getAccountTemplate(otherUser.getAccountId()));

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);
    List<String> commitHistoryDiff = result.fixedRefDiff.get(RefNames.changeMetaRef(c.getId()));
    assertThat(commitHistoryDiff).hasSize(3);
    assertThat(commitHistoryDiff.get(0))
        .isEqualTo(
            "@@ -6 +6 @@\n"
                + "-Assignee added: Change Owner <change@owner.com>\n"
                + "+Assignee added: <GERRIT_ACCOUNT_1>\n");
    assertThat(commitHistoryDiff.get(1))
        .isEqualTo(
            "@@ -6 +6 @@\n"
                + "-Assignee changed from: Change Owner <change@owner.com> to: Other Account <other@account.com>\n"
                + "+Assignee changed from: <GERRIT_ACCOUNT_1> to: <GERRIT_ACCOUNT_2>\n");
    assertThat(commitHistoryDiff.get(2))
        .isEqualTo(
            "@@ -6 +6 @@\n"
                + "-Assignee deleted: Other Account <other@account.com>\n"
                + "+Assignee deleted: <GERRIT_ACCOUNT_2>\n");
  }

  @Test
  public void singleRunFixesAll() throws Exception {
    Change c = newChange();
    Timestamp when = TimeUtil.nowTs();
    String assigneeIdentToFix = getAccountIdentToFix(otherUser.getAccount());
    PersonIdent authorIdentToFix =
        new PersonIdent(
            changeOwner.getName(),
            changeNoteUtil.getAccountIdAsEmailAddress(changeOwner.getAccountId()),
            when,
            serverIdent.getTimeZone());

    RevCommit invalidUpdateCommit =
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            getChangeUpdateBody(
                c,
                "Assignee added: Other Account <other@account.com>",
                "Assignee: " + assigneeIdentToFix),
            authorIdentToFix);
    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    int invalidCommitIndex = commitsBeforeRewrite.indexOf(invalidUpdateCommit);
    ChangeNotes notesBeforeRewrite = newNotes(c);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    ChangeNotes notesAfterRewrite = newNotes(c);
    assertThat(notesBeforeRewrite.getChange().getAssignee()).isEqualTo(otherUserId);
    assertThat(notesAfterRewrite.getChange().getAssignee()).isEqualTo(otherUserId);

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(
        commitsBeforeRewrite, commitsAfterRewrite, ImmutableList.of(invalidCommitIndex));

    RevCommit fixedUpdateCommit = commitsAfterRewrite.get(invalidCommitIndex);
    assertThat(invalidUpdateCommit.getAuthorIdent())
        .isNotEqualTo(fixedUpdateCommit.getAuthorIdent());
    assertThat(invalidUpdateCommit.getFullMessage()).contains(otherUser.getName());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(changeOwner.getName());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(otherUser.getName());

    List<String> commitHistoryDiff = result.fixedRefDiff.get(RefNames.changeMetaRef(c.getId()));
    assertThat(commitHistoryDiff).hasSize(1);
    assertThat(commitHistoryDiff.get(0)).contains("-author Change Owner <1@gerrit>");
    assertThat(commitHistoryDiff.get(0)).contains("+author Gerrit User 1 <1@gerrit>");
    assertThat(commitHistoryDiff.get(0))
        .contains(
            "@@ -6 +6 @@\n"
                + "-Assignee added: Other Account <other@account.com>\n"
                + "+Assignee added: <GERRIT_ACCOUNT_2>\n"
                + "@@ -9 +9 @@\n"
                + "-Assignee: Other Account <2@gerrit>\n"
                + "+Assignee: Gerrit User 2 <2@gerrit>");
  }

  private RevCommit writeUpdate(String metaRef, String body, PersonIdent author) throws Exception {
    return tr.branch(metaRef).commit().message(body).author(author).committer(serverIdent).create();
  }

  private String getChangeUpdateBody(Change change, String changeMessage, String... footers) {
    StringBuilder commitBody = new StringBuilder();
    commitBody.append("Update patch set " + change.currentPatchSetId().get());
    commitBody.append("\n\n");
    if (changeMessage != null) {
      commitBody.append(changeMessage);
      commitBody.append("\n\n");
    }
    commitBody.append("Patch-set: " + change.currentPatchSetId().get());
    commitBody.append("\n");
    for (String footer : footers) {
      commitBody.append(footer);
      commitBody.append("\n");
    }
    return commitBody.toString();
  }

  private ImmutableList<RevCommit> logMetaRef(Repository repo, Ref metaRef) throws Exception {
    try (RevWalk rw = new RevWalk(repo)) {
      rw.sort(RevSort.TOPO);
      rw.sort(RevSort.REVERSE);
      if (metaRef == null) {
        return ImmutableList.of();
      }
      rw.markStart(rw.parseCommit(metaRef.getObjectId()));
      return ImmutableList.copyOf(rw);
    }
  }

  private void assertValidCommits(
      ImmutableList<RevCommit> commitsBeforeRewrite,
      ImmutableList<RevCommit> commitsAfterRewrite,
      ImmutableList<Integer> invalidCommits) {
    ImmutableList<RevCommit> validCommitsBeforeRewrite =
        IntStream.range(0, commitsBeforeRewrite.size())
            .filter(i -> !invalidCommits.contains(i))
            .mapToObj(commitsBeforeRewrite::get)
            .collect(ImmutableList.toImmutableList());

    ImmutableList<RevCommit> validCommitsAfterRewrite =
        IntStream.range(0, commitsAfterRewrite.size())
            .filter(i -> !invalidCommits.contains(i))
            .mapToObj(commitsAfterRewrite::get)
            .collect(ImmutableList.toImmutableList());

    assertThat(validCommitsBeforeRewrite).hasSize(validCommitsAfterRewrite.size());
    for (int i = 0; i < validCommitsAfterRewrite.size(); i++) {
      RevCommit actual = validCommitsAfterRewrite.get(i);
      RevCommit expected = validCommitsBeforeRewrite.get(i);
      assertThat(actual.getAuthorIdent()).isEqualTo(expected.getAuthorIdent());
      assertThat(actual.getCommitterIdent()).isEqualTo(expected.getCommitterIdent());
      assertThat(actual.getFullMessage()).isEqualTo(expected.getFullMessage());
    }
  }

  private String getAccountIdentToFix(Account account) {
    return String.format("%s <%s>", account.getName(), account.id().get() + "@" + serverId);
  }

  private String getValidIdentAsString(Account account) {
    return String.format(
        "%s <%s>",
        ChangeNoteUtil.getAccountIdAsUsername(account.id()), account.id().get() + "@" + serverId);
  }

  private ImmutableList<String> changeMessages(ChangeNotes changeNotes) {
    return changeNotes.getChangeMessages().stream()
        .map(ChangeMessage::getMessage)
        .collect(toImmutableList());
  }

  private PersonIdent getAuthorIdent(Account account) {
    Timestamp when = TimeUtil.nowTs();
    return changeNoteUtil.newAccountIdIdent(account.id(), when, serverIdent);
  }
}
