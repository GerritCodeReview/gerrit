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
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.AttentionSetUpdate.Operation;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.git.RefUpdateUtil;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.notedb.ChangeNoteUtil.AttentionStatusInNoteDb;
import com.google.gerrit.server.notedb.CommitRewriter.BackfillResult;
import com.google.gerrit.server.notedb.CommitRewriter.CommitDiff;
import com.google.gerrit.server.notedb.CommitRewriter.RunOptions;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link CommitRewriter} */
public class CommitRewriterTest extends AbstractChangeNotesTest {

  private @Inject CommitRewriter rewriter;
  @Inject private ChangeNoteUtil changeNoteUtil;

  private static final Gson gson = OutputFormat.JSON_COMPACT.newGson();

  @Before
  public void setUp() throws Exception {}

  @After
  public void cleanUp() throws Exception {
    BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
    bru.setAllowNonFastForwards(true);
    for (Ref ref : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES)) {
      Change.Id changeId = Change.Id.fromRef(ref.getName());
      if (changeId == null || !ref.getName().equals(RefNames.changeMetaRef(changeId))) {
        continue;
      }
      bru.addCommand(new ReceiveCommand(ref.getObjectId(), ObjectId.zeroId(), ref.getName()));
    }

    RefUpdateUtil.executeChecked(bru, repo);
  }

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
  public void outputDiffOff_refsReported() throws Exception {
    Change c = newChange();
    ChangeUpdate update = newUpdate(c, changeOwner);
    update.setChangeMessage("Change has been successfully merged by " + changeOwner.getName());
    ObjectId commitToFix = update.commit();

    ChangeUpdate updateWithSubject = newUpdate(c, changeOwner);
    updateWithSubject.setSubjectForCommit("Update with subject");
    updateWithSubject.commit();

    Ref metaRefBefore = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    RunOptions options = new RunOptions();
    options.dryRun = false;
    options.outputDiff = false;
    options.verifyCommits = false;
    BackfillResult backfillResult = rewriter.backfillProject(project, repo, options);
    assertThat(backfillResult.fixedRefDiff.keySet())
        .containsExactly(RefNames.changeMetaRef(c.getId()));
    Ref metaRefAfter = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    assertThat(metaRefBefore.getObjectId()).isNotEqualTo(metaRefAfter.getObjectId());

    assertFixedCommits(ImmutableList.of(commitToFix), backfillResult, c.getId());

    List<String> commitHistoryDiff = commitHistoryDiff(backfillResult, c.getId());
    assertThat(commitHistoryDiff).containsExactly("");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void numRefs_greater_maxRefsToUpdate_allFixed() throws Exception {
    int numberOfChanges = 12;
    ImmutableMap.Builder<String, Ref> refsToOldMetaBuilder = new ImmutableMap.Builder<>();
    for (int i = 0; i < numberOfChanges; i++) {
      Change c = newChange();
      ChangeUpdate update = newUpdate(c, changeOwner);
      update.setChangeMessage("Change has been successfully merged by " + changeOwner.getName());
      update.commit();
      ChangeUpdate updateWithSubject = newUpdate(c, changeOwner);
      updateWithSubject.setSubjectForCommit("Update with subject");
      updateWithSubject.commit();
      String refName = RefNames.changeMetaRef(c.getId());
      Ref metaRefBeforeRewrite = repo.exactRef(refName);
      refsToOldMetaBuilder.put(refName, metaRefBeforeRewrite);
    }
    ImmutableMap<String, Ref> refsToOldMeta = refsToOldMetaBuilder.build();

    RunOptions options = new RunOptions();
    options.dryRun = false;
    options.outputDiff = false;
    options.verifyCommits = false;
    options.maxRefsInBatch = 10;
    options.maxRefsToUpdate = 12;
    BackfillResult backfillResult = rewriter.backfillProject(project, repo, options);
    assertThat(backfillResult.fixedRefDiff.keySet()).isEqualTo(refsToOldMeta.keySet());
    for (Map.Entry<String, Ref> refEntry : refsToOldMeta.entrySet()) {
      Ref metaRefAfterRewrite = repo.exactRef(refEntry.getKey());
      assertThat(refEntry.getValue()).isNotEqualTo(metaRefAfterRewrite);
    }
  }

  @Test
  public void maxRefsToUpdate_coversAllInvalid_inMultipleBatches() throws Exception {
    testMaxRefsToUpdate(
        /*numberOfInvalidChanges=*/ 11,
        /*numberOfValidChanges=*/ 9,
        /*maxRefsToUpdate=*/ 12,
        /*maxRefsInBatch=*/ 2);
  }

  @Test
  public void maxRefsToUpdate_coversAllInvalid_inSingleBatch() throws Exception {
    testMaxRefsToUpdate(
        /*numberOfInvalidChanges=*/ 11,
        /*numberOfValidChanges=*/ 9,
        /*maxRefsToUpdate=*/ 12,
        /*maxRefsInBatch=*/ 12);
  }

  @Test
  public void moreInvalidRefs_thenMaxRefsToUpdate_inMultipleBatches() throws Exception {
    testMaxRefsToUpdate(
        /*numberOfInvalidChanges=*/ 11,
        /*numberOfValidChanges=*/ 9,
        /*maxRefsToUpdate=*/ 10,
        /*maxRefsInBatch=*/ 2);
  }

  @Test
  public void moreInvalidRefs_thenMaxRefsToUpdate_inSingleBatch() throws Exception {
    testMaxRefsToUpdate(
        /*numberOfInvalidChanges=*/ 11,
        /*numberOfValidChanges=*/ 9,
        /*maxRefsToUpdate=*/ 10,
        /*maxRefsInBatch=*/ 10);
  }

  private void testMaxRefsToUpdate(
      int numberOfInvalidChanges, int numberOfValidChanges, int maxRefsToUpdate, int maxRefsInBatch)
      throws Exception {
    ImmutableMap.Builder<String, ObjectId> expectedFixedRefsToOldMetaBuilder =
        new ImmutableMap.Builder<>();
    ImmutableMap.Builder<String, ObjectId> expectedSkippedRefsToOldMetaBuilder =
        new ImmutableMap.Builder<>();
    for (int i = 0; i < numberOfValidChanges; i++) {
      Change c = newChange();
      ChangeUpdate updateWithSubject = newUpdate(c, changeOwner);
      updateWithSubject.setSubjectForCommit("Update with subject");
      updateWithSubject.commit();
      String refName = RefNames.changeMetaRef(c.getId());
      Ref metaRefBeforeRewrite = repo.exactRef(refName);
      expectedSkippedRefsToOldMetaBuilder.put(refName, metaRefBeforeRewrite.getObjectId());
    }
    Set<String> invalidRefs = new HashSet<>();
    for (int i = 0; i < numberOfInvalidChanges; i++) {
      Change c = newChange();
      ChangeUpdate update = newUpdate(c, changeOwner);
      update.setChangeMessage("Change has been successfully merged by " + changeOwner.getName());
      update.commit();
      ChangeUpdate updateWithSubject = newUpdate(c, changeOwner);
      updateWithSubject.setSubjectForCommit("Update with subject");
      updateWithSubject.commit();
      String refName = RefNames.changeMetaRef(c.getId());
      invalidRefs.add(refName);
    }
    int i = 0;
    for (Ref ref : repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_CHANGES)) {
      Ref metaRefBeforeRewrite = repo.exactRef(ref.getName());
      if (!invalidRefs.contains(ref.getName())) {
        continue;
      }
      if (i < maxRefsToUpdate) {
        expectedFixedRefsToOldMetaBuilder.put(ref.getName(), metaRefBeforeRewrite.getObjectId());
      } else {
        expectedSkippedRefsToOldMetaBuilder.put(ref.getName(), metaRefBeforeRewrite.getObjectId());
      }
      i++;
    }
    ImmutableMap<String, ObjectId> expectedFixedRefsToOldMeta =
        expectedFixedRefsToOldMetaBuilder.build();
    ImmutableMap<String, ObjectId> expectedSkippedRefsToOldMeta =
        expectedSkippedRefsToOldMetaBuilder.build();
    RunOptions options = new RunOptions();
    options.dryRun = false;
    options.outputDiff = false;
    options.verifyCommits = false;
    options.maxRefsInBatch = maxRefsInBatch;
    options.maxRefsToUpdate = maxRefsToUpdate;
    BackfillResult backfillResult = rewriter.backfillProject(project, repo, options);
    assertThat(backfillResult.fixedRefDiff.keySet()).isEqualTo(expectedFixedRefsToOldMeta.keySet());
    for (Map.Entry<String, ObjectId> refEntry : expectedFixedRefsToOldMeta.entrySet()) {
      Ref metaRefAfterRewrite = repo.exactRef(refEntry.getKey());
      assertThat(refEntry.getValue()).isNotEqualTo(metaRefAfterRewrite.getObjectId());
    }
    for (Map.Entry<String, ObjectId> refEntry : expectedSkippedRefsToOldMeta.entrySet()) {
      Ref metaRefAfterRewrite = repo.exactRef(refEntry.getKey());
      assertThat(refEntry.getValue()).isEqualTo(metaRefAfterRewrite.getObjectId());
    }
    RunOptions secondRunOptions = new RunOptions();
    secondRunOptions.dryRun = false;
    secondRunOptions.outputDiff = false;
    secondRunOptions.verifyCommits = false;
    secondRunOptions.maxRefsInBatch = maxRefsInBatch;
    secondRunOptions.maxRefsToUpdate = numberOfInvalidChanges + numberOfValidChanges;
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    int expectedSecondRunResult =
        numberOfInvalidChanges > maxRefsToUpdate ? numberOfInvalidChanges - maxRefsToUpdate : 0;
    assertThat(secondRunResult.fixedRefDiff.keySet().size()).isEqualTo(expectedSecondRunResult);
  }

  @Test
  public void fixAuthorIdent() throws Exception {
    Change c = newChange();
    Instant when = TimeUtil.now();
    PersonIdent invalidAuthorIdent =
        new PersonIdent(
            changeOwner.getName(),
            changeNoteUtil.getAccountIdAsEmailAddress(changeOwner.getAccountId()),
            when,
            serverIdent.getZoneId());
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
    assertFixedCommits(ImmutableList.of(invalidUpdateCommit.getId()), result, c.getId());

    RevCommit fixedUpdateCommit = commitsAfterRewrite.get(invalidCommitIndex);
    PersonIdent originalAuthorIdent = invalidUpdateCommit.getAuthorIdent();
    PersonIdent fixedAuthorIdent = fixedUpdateCommit.getAuthorIdent();
    assertThat(originalAuthorIdent).isNotEqualTo(fixedAuthorIdent);
    assertThat(fixedUpdateCommit.getAuthorIdent().getName())
        .isEqualTo("Gerrit User " + changeOwner.getAccountId());
    assertThat(originalAuthorIdent.getEmailAddress()).isEqualTo(fixedAuthorIdent.getEmailAddress());
    assertThat(originalAuthorIdent.getWhenAsInstant())
        .isEqualTo(fixedAuthorIdent.getWhenAsInstant());
    assertThat(originalAuthorIdent.getTimeZone()).isEqualTo(fixedAuthorIdent.getTimeZone());
    assertThat(invalidUpdateCommit.getFullMessage()).isEqualTo(fixedUpdateCommit.getFullMessage());
    assertThat(invalidUpdateCommit.getCommitterIdent())
        .isEqualTo(fixedUpdateCommit.getCommitterIdent());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(changeOwner.getName());
    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff).hasSize(1);
    assertThat(commitHistoryDiff.get(0)).contains("-author Change Owner <1@gerrit>");
    assertThat(commitHistoryDiff.get(0)).contains("+author Gerrit User 1 <1@gerrit>");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixRealUserFooterIdent() throws Exception {
    Change c = newChange();

    String realUserIdentToFix = getAccountIdentToFix(otherUser.getAccount());
    ObjectId invalidUpdateCommit =
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
    assertFixedCommits(ImmutableList.of(invalidUpdateCommit), result, c.getId());

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -9 +9 @@\n"
                + "-Real-user: Other Account <2@gerrit>\n"
                + "+Real-user: Gerrit User 2 <2@gerrit>\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixReviewerFooterIdent() throws Exception {
    Change c = newChange();
    String reviewerIdentToFix = getAccountIdentToFix(otherUser.getAccount());
    ImmutableList<ObjectId> commitsToFix =
        new ImmutableList.Builder<ObjectId>()
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    // valid change message that should not be overwritten
                    getChangeUpdateBody(
                        c,
                        "Removed reviewer <GERRIT_ACCOUNT_1>.",
                        "Reviewer: " + reviewerIdentToFix),
                    getAuthorIdent(changeOwner.getAccount())))
            .add(
                writeUpdate(
                    RefNames.changeMetaRef(c.getId()),
                    // valid change message that should not be overwritten
                    getChangeUpdateBody(
                        c,
                        "Removed cc <GERRIT_ACCOUNT_2> with the following votes:\n\n * Code-Review+2 by <GERRIT_ACCOUNT_2>",
                        "CC: " + reviewerIdentToFix),
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

    Instant updateTimestamp = serverIdent.getWhenAsInstant();
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
    assertFixedCommits(commitsToFix, result, c.getId());

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -9 +9 @@\n"
                + "-Reviewer: Other Account <2@gerrit>\n"
                + "+Reviewer: Gerrit User 2 <2@gerrit>\n",
            "@@ -11 +11 @@\n"
                + "-CC: Other Account <2@gerrit>\n"
                + "+CC: Gerrit User 2 <2@gerrit>\n",
            "@@ -9 +9 @@\n"
                + "-Removed: Other Account <2@gerrit>\n"
                + "+Removed: Gerrit User 2 <2@gerrit>\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixReviewerMessage() throws Exception {
    Change c = newChange();
    ImmutableList.Builder<ObjectId> commitsToFix = new ImmutableList.Builder<>();
    ChangeUpdate addReviewerUpdate = newUpdate(c, changeOwner);
    addReviewerUpdate.putReviewer(otherUserId, REVIEWER);
    addReviewerUpdate.commit();

    commitsToFix.add(
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            getChangeUpdateBody(
                c,
                String.format("Removed reviewer %s.", otherUser.getAccount().fullName()),
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
                String.format(
                    "Removed cc %s with the following votes:\n\n * Code-Review+2",
                    otherUser.getAccount().fullName()),
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

    Instant updateTimestamp = serverIdent.getWhenAsInstant();
    ImmutableList<ReviewerStatusUpdate> expectedReviewerUpdates =
        ImmutableList.of(
            ReviewerStatusUpdate.create(
                addReviewerUpdate.when, changeOwner.getAccountId(), otherUserId, REVIEWER),
            ReviewerStatusUpdate.create(
                updateTimestamp, changeOwner.getAccountId(), otherUserId, REMOVED),
            ReviewerStatusUpdate.create(
                addCcUpdate.when, changeOwner.getAccountId(), otherUserId, CC),
            ReviewerStatusUpdate.create(
                updateTimestamp, changeOwner.getAccountId(), otherUserId, REMOVED));
    ChangeNotes notesAfterRewrite = newNotes(c);

    assertThat(notesBeforeRewrite.getReviewerUpdates()).isEqualTo(expectedReviewerUpdates);
    assertThat(changeMessages(notesBeforeRewrite))
        .containsExactly(
            "Removed reviewer Other Account.",
            "Removed cc Other Account with the following votes:\n\n * Code-Review+2");
    assertThat(notesAfterRewrite.getReviewerUpdates()).isEqualTo(expectedReviewerUpdates);
    assertThat(changeMessages(notesAfterRewrite)).containsExactly("Removed reviewer", "Removed cc");

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);
    assertFixedCommits(commitsToFix.build(), result, c.getId());

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -6 +6 @@\n" + "-Removed reviewer Other Account.\n" + "+Removed reviewer\n",
            "@@ -6,3 +6 @@\n"
                + "-Removed cc Other Account with the following votes:\n"
                + "-\n"
                + "- * Code-Review+2\n"
                + "+Removed cc\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixReviewerMessageNoReviewerFooter() throws Exception {
    Change c = newChange();

    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(
            c, String.format("Removed reviewer %s.", otherUser.getAccount().fullName())),
        getAuthorIdent(changeOwner.getAccount()));

    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(
            c,
            String.format(
                "Removed cc %s with the following votes:\n\n * Code-Review+2",
                otherUser.getAccount().fullName())),
        getAuthorIdent(changeOwner.getAccount()));

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -6 +6 @@\n" + "-Removed reviewer Other Account.\n" + "+Removed reviewer\n",
            "@@ -6,3 +6 @@\n"
                + "-Removed cc Other Account with the following votes:\n"
                + "-\n"
                + "- * Code-Review+2\n"
                + "+Removed cc\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixLabelFooterIdent() throws Exception {
    Change c = newChange();
    String approverIdentToFix = getAccountIdentToFix(otherUser.getAccount());
    String changeOwnerIdentToFix = getAccountIdentToFix(changeOwner.getAccount());
    ChangeUpdate approvalUpdateByOtherUser = newUpdate(c, otherUser);
    approvalUpdateByOtherUser.putApproval(VERIFIED, (short) -1);
    approvalUpdateByOtherUser.commit();

    ImmutableList<ObjectId> commitsToFix =
        new ImmutableList.Builder<ObjectId>()
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

    Instant updateTimestamp = serverIdent.getWhenAsInstant();
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

    assertThat(notesBeforeRewrite.getApprovals().all().get(c.currentPatchSetId()))
        .containsExactlyElementsIn(expectedApprovals);
    assertThat(notesAfterRewrite.getApprovals().all().get(c.currentPatchSetId()))
        .containsExactlyElementsIn(expectedApprovals);

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);
    assertFixedCommits(commitsToFix, result, c.getId());

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -7,2 +7,2 @@\n"
                + "-Label: -Verified Other Account <2@gerrit>\n"
                + "-Label: Custom-Label-1=-1 Other Account <2@gerrit>\n"
                + "+Label: -Verified Gerrit User 2 <2@gerrit>\n"
                + "+Label: Custom-Label-1=-1 Gerrit User 2 <2@gerrit>\n"
                + "@@ -11,2 +11,2 @@\n"
                + "-Label: Custom-Label-2=+2 Other Account <2@gerrit>\n"
                + "-Label: Custom-Label-3=0 Other Account <2@gerrit>\n"
                + "+Label: Custom-Label-2=+2 Gerrit User 2 <2@gerrit>\n"
                + "+Label: Custom-Label-3=0 Gerrit User 2 <2@gerrit>\n",
            "@@ -7 +7 @@\n"
                + "-Label: -Verified Change Owner <1@gerrit>\n"
                + "+Label: -Verified Gerrit User 1 <1@gerrit>\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
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

    ImmutableList<ObjectId> commitsToFix =
        new ImmutableList.Builder<ObjectId>()
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

    Instant updateTimestamp = serverIdent.getWhenAsInstant();
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

    assertThat(notesBeforeRewrite.getApprovals().all().get(c.currentPatchSetId()))
        .containsExactlyElementsIn(expectedApprovals);
    assertThat(changeMessages(notesAfterRewrite))
        .containsExactly(
            "Removed Code-Review+2 by <GERRIT_ACCOUNT_2>",
            "Removed Custom-Label-1 by <GERRIT_ACCOUNT_2>",
            "Removed Verified+2 by <GERRIT_ACCOUNT_1>");
    assertThat(notesAfterRewrite.getApprovals().all().get(c.currentPatchSetId()))
        .containsExactlyElementsIn(expectedApprovals);

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);
    assertFixedCommits(commitsToFix, result, c.getId());

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -6 +6 @@\n"
                + "-Removed Code-Review+2 by Other Account <other@account.com>\n"
                + "+Removed Code-Review+2 by <GERRIT_ACCOUNT_2>\n"
                + "@@ -9 +9 @@\n"
                + "-Label: -Code-Review Other Account <2@gerrit>\n"
                + "+Label: -Code-Review Gerrit User 2 <2@gerrit>\n",
            "@@ -6 +6 @@\n"
                + "-Removed Custom-Label-1 by Other Account <other@account.com>\n"
                + "+Removed Custom-Label-1 by <GERRIT_ACCOUNT_2>\n",
            "@@ -6 +6 @@\n"
                + "-Removed Verified+2 by Change Owner <change@owner.com>\n"
                + "+Removed Verified+2 by <GERRIT_ACCOUNT_1>\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixRemoveVoteChangeMessageWithUnparsableAuthorIdent() throws Exception {
    Change c = newChange();
    PersonIdent invalidAuthorIdent =
        new PersonIdent(
            changeOwner.getName(), "server@" + serverId, TimeUtil.now(), serverIdent.getZoneId());
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(
            c,
            /*changeMessage=*/ "Removed Verified+2 by " + otherUser.getNameEmail(),
            "Label: -Verified"),
        invalidAuthorIdent);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    // Other Account does not applier in any change updates, replaced with default
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -6 +6 @@\n"
                + "-Removed Verified+2 by Other Account <other@account.com>\n"
                + "+Removed Verified+2\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixRemoveVoteChangeMessageWithNoFooterLabel() throws Exception {
    Change c = newChange();
    ChangeUpdate approvalUpdate = newUpdate(c, changeOwner);
    approvalUpdate.putApproval(VERIFIED, (short) +2);

    approvalUpdate.putApprovalFor(otherUserId, VERIFIED, (short) -1);
    approvalUpdate.commit();
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),

        // Even though footer is missing, accounts are matched among the account in change updates.
        getChangeUpdateBody(c, /*changeMessage=*/ "Removed Verified-1 by Other Account (0002)"),
        getAuthorIdent(changeOwner.getAccount()));

    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(
            c, /*changeMessage=*/ "Removed Verified+2 by " + changeOwner.getNameEmail()),
        getAuthorIdent(changeOwner.getAccount()));

    // No rewrite for default
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(c, /*changeMessage=*/ "Removed Verified+2 by Gerrit Account"),
        getAuthorIdent(changeOwner.getAccount()));

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -6 +6 @@\n"
                + "-Removed Verified-1 by Other Account (0002)\n"
                + "+Removed Verified-1 by <GERRIT_ACCOUNT_2>\n",
            "@@ -6 +6 @@\n"
                + "-Removed Verified+2 by Change Owner <change@owner.com>\n"
                + "+Removed Verified+2 by <GERRIT_ACCOUNT_1>\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixRemoveVoteChangeMessageWithNoFooterLabel_matchByEmail() throws Exception {
    Change c = newChange();
    ChangeUpdate approvalUpdate = newUpdate(c, changeOwner);
    approvalUpdate.putApproval(VERIFIED, (short) +2);

    approvalUpdate.putApprovalFor(otherUserId, VERIFIED, (short) -1);
    approvalUpdate.commit();
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(
            c, /*changeMessage=*/ "Removed Verified+2 by Renamed Change Owner <change@owner.com>"),
        getAuthorIdent(changeOwner.getAccount()));

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -6 +6 @@\n"
                + "-Removed Verified+2 by Renamed Change Owner <change@owner.com>\n"
                + "+Removed Verified+2 by <GERRIT_ACCOUNT_1>\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixRemoveVoteChangeMessageWithNoFooterLabel_matchByName() throws Exception {
    Change c = newChange();
    ChangeUpdate approvalUpdate = newUpdate(c, changeOwner);
    approvalUpdate.putApproval(VERIFIED, (short) +2);

    approvalUpdate.putApprovalFor(otherUserId, VERIFIED, (short) -1);
    approvalUpdate.commit();
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(c, /*changeMessage=*/ "Removed Verified+2 by Change Owner"),
        getAuthorIdent(changeOwner.getAccount()));

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -6 +6 @@\n"
                + "-Removed Verified+2 by Change Owner\n"
                + "+Removed Verified+2 by <GERRIT_ACCOUNT_1>\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixRemoveVoteChangeMessageWithNoFooterLabel_matchDuplicateAccounts()
      throws Exception {
    Account duplicateCodeOwner =
        Account.builder(Account.id(4), TimeUtil.now())
            .setFullName(changeOwner.getName())
            .setPreferredEmail("other@test.com")
            .build();
    accountCache.put(duplicateCodeOwner);
    Change c = newChange();
    ChangeUpdate approvalUpdate = newUpdate(c, changeOwner);
    approvalUpdate.putApproval(VERIFIED, (short) +2);

    approvalUpdate.putApprovalFor(duplicateCodeOwner.id(), VERIFIED, (short) -1);
    approvalUpdate.commit();
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(
            c, /*changeMessage=*/ "Removed Verified+2 by Change Owner <other@test.com>"),
        getAuthorIdent(changeOwner.getAccount()));
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(
            c, /*changeMessage=*/ "Removed Verified+2 by Change Owner <change@owner.com>"),
        getAuthorIdent(changeOwner.getAccount()));
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(
            c, /*changeMessage=*/ "Removed Verified-1 by Change Owner <other@test.com>"),
        getAuthorIdent(changeOwner.getAccount()));

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -6 +6 @@\n"
                + "-Removed Verified+2 by Change Owner <other@test.com>\n"
                + "+Removed Verified+2 by <GERRIT_ACCOUNT_4>\n",
            "@@ -6 +6 @@\n"
                + "-Removed Verified+2 by Change Owner <change@owner.com>\n"
                + "+Removed Verified+2 by <GERRIT_ACCOUNT_1>\n",
            "@@ -6 +6 @@\n"
                + "-Removed Verified-1 by Change Owner <other@test.com>\n"
                + "+Removed Verified-1 by <GERRIT_ACCOUNT_4>\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixRemoveVotesChangeMessage() throws Exception {
    Change c = newChange();
    ChangeUpdate approvalUpdate = newUpdate(c, changeOwner);
    approvalUpdate.putApproval(VERIFIED, (short) +2);

    approvalUpdate.putApprovalFor(otherUserId, VERIFIED, (short) -1);
    approvalUpdate.commit();
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),

        // Even though footer is missing, accounts are matched among the account in change updates.
        getChangeUpdateBody(
            c,
            /*changeMessage=*/ "Removed the following votes:\n"
                + String.format("* Verified-1 by %s\n", otherUser.getNameEmail())),
        getAuthorIdent(changeOwner.getAccount()));

    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(
            c,
            /*changeMessage=*/ "Removed the following votes:\n"
                + String.format("* Verified+2 by %s\n", changeOwner.getNameEmail())
                + String.format("* Verified-1 by %s\n", changeOwner.getNameEmail())
                + String.format("* Code-Review by %s\n", otherUser.getNameEmail())),
        getAuthorIdent(changeOwner.getAccount()));

    // No rewrite for default
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(
            c,
            /*changeMessage=*/ "Removed the following votes:\n"
                + "* Verified+2 by Gerrit Account\n"
                + "* Verified-1 by <GERRIT_ACCOUNT_2>\n"),
        getAuthorIdent(changeOwner.getAccount()));

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -7 +7 @@\n"
                + "-* Verified-1 by Other Account <other@account.com>\n"
                + "+* Verified-1 by <GERRIT_ACCOUNT_2>\n",
            "@@ -7,3 +7,3 @@\n"
                + "-* Verified+2 by Change Owner <change@owner.com>\n"
                + "-* Verified-1 by Change Owner <change@owner.com>\n"
                + "-* Code-Review by Other Account <other@account.com>\n"
                + "+* Verified+2 by <GERRIT_ACCOUNT_1>\n"
                + "+* Verified-1 by <GERRIT_ACCOUNT_1>\n"
                + "+* Code-Review by <GERRIT_ACCOUNT_2>\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixAttentionFooter() throws Exception {
    Change c = newChange();
    ImmutableList.Builder<ObjectId> commitsToFix = new ImmutableList.Builder<>();
    // Only 'reason' fix is required
    ChangeUpdate invalidAttentionSetUpdate = newUpdate(c, changeOwner);
    invalidAttentionSetUpdate.putReviewer(otherUserId, REVIEWER);
    invalidAttentionSetUpdate.addToPlannedAttentionSetUpdates(
        AttentionSetUpdate.createForWrite(
            otherUserId,
            Operation.ADD,
            String.format("Added by %s using the hovercard menu", otherUser.getName())));
    commitsToFix.add(invalidAttentionSetUpdate.commit());
    ChangeUpdate invalidMultipleAttentionSetUpdate = newUpdate(c, changeOwner);
    invalidMultipleAttentionSetUpdate.addToPlannedAttentionSetUpdates(
        AttentionSetUpdate.createForWrite(
            changeOwner.getAccountId(),
            Operation.ADD,
            String.format("%s replied on the change", otherUser.getName())));
    invalidMultipleAttentionSetUpdate.addToPlannedAttentionSetUpdates(
        AttentionSetUpdate.createForWrite(
            otherUserId,
            Operation.REMOVE,
            String.format("Removed by %s using the hovercard menu", otherUser.getName())));
    commitsToFix.add(invalidMultipleAttentionSetUpdate.commit());
    String otherUserIdentToFix = getAccountIdentToFix(otherUser.getAccount());
    String changeOwnerIdentToFix = getAccountIdentToFix(changeOwner.getAccount());
    commitsToFix.add(
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            getChangeUpdateBody(
                c,
                /*changeMessage=*/ null,
                // Only 'person_ident' fix is required
                "Attention: "
                    + gson.toJson(
                        new AttentionStatusInNoteDb(
                            otherUserIdentToFix,
                            Operation.ADD,
                            "Added by someone using the hovercard menu")),
                // Both 'reason' and 'person_ident' fix is required
                "Attention: "
                    + gson.toJson(
                        new AttentionStatusInNoteDb(
                            changeOwnerIdentToFix,
                            Operation.REMOVE,
                            String.format("%s replied on the change", otherUser.getName())))),
            getAuthorIdent(changeOwner.getAccount())));

    ChangeUpdate validAttentionSetUpdate = newUpdate(c, changeOwner);
    validAttentionSetUpdate.addToPlannedAttentionSetUpdates(
        AttentionSetUpdate.createForWrite(otherUserId, Operation.REMOVE, "Removed by someone"));
    validAttentionSetUpdate.addToPlannedAttentionSetUpdates(
        AttentionSetUpdate.createForWrite(
            changeOwner.getAccountId(), Operation.ADD, "Added by someone"));
    validAttentionSetUpdate.commit();

    ChangeUpdate invalidRemovedByClickUpdate = newUpdate(c, changeOwner);
    invalidRemovedByClickUpdate.addToPlannedAttentionSetUpdates(
        AttentionSetUpdate.createForWrite(
            changeOwner.getAccountId(),
            Operation.REMOVE,
            String.format("Removed by %s by clicking the attention icon", otherUser.getName())));
    commitsToFix.add(invalidRemovedByClickUpdate.commit());

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
    notesBeforeRewrite.getAttentionSetUpdates();
    Instant updateTimestamp = serverIdent.getWhenAsInstant();
    ImmutableList<AttentionSetUpdate> attentionSetUpdatesBeforeRewrite =
        ImmutableList.of(
            AttentionSetUpdate.createFromRead(
                invalidRemovedByClickUpdate.getWhen(),
                changeOwner.getAccountId(),
                Operation.REMOVE,
                String.format("Removed by %s by clicking the attention icon", otherUser.getName())),
            AttentionSetUpdate.createFromRead(
                validAttentionSetUpdate.getWhen(),
                changeOwner.getAccountId(),
                Operation.ADD,
                "Added by someone"),
            AttentionSetUpdate.createFromRead(
                validAttentionSetUpdate.getWhen(),
                otherUserId,
                Operation.REMOVE,
                "Removed by someone"),
            AttentionSetUpdate.createFromRead(
                updateTimestamp,
                changeOwner.getAccountId(),
                Operation.REMOVE,
                String.format("%s replied on the change", otherUser.getName())),
            AttentionSetUpdate.createFromRead(
                updateTimestamp,
                otherUserId,
                Operation.ADD,
                "Added by someone using the hovercard menu"),
            AttentionSetUpdate.createFromRead(
                invalidMultipleAttentionSetUpdate.getWhen(),
                otherUserId,
                Operation.REMOVE,
                String.format("Removed by %s using the hovercard menu", otherUser.getName())),
            AttentionSetUpdate.createFromRead(
                invalidMultipleAttentionSetUpdate.getWhen(),
                changeOwner.getAccountId(),
                Operation.ADD,
                String.format("%s replied on the change", otherUser.getName())),
            AttentionSetUpdate.createFromRead(
                invalidAttentionSetUpdate.getWhen(),
                otherUserId,
                Operation.ADD,
                String.format("Added by %s using the hovercard menu", otherUser.getName())));

    ImmutableList<AttentionSetUpdate> attentionSetUpdatesAfterRewrite =
        ImmutableList.of(
            AttentionSetUpdate.createFromRead(
                invalidRemovedByClickUpdate.getWhen(),
                changeOwner.getAccountId(),
                Operation.REMOVE,
                "Removed by someone by clicking the attention icon"),
            AttentionSetUpdate.createFromRead(
                validAttentionSetUpdate.getWhen(),
                changeOwner.getAccountId(),
                Operation.ADD,
                "Added by someone"),
            AttentionSetUpdate.createFromRead(
                validAttentionSetUpdate.getWhen(),
                otherUserId,
                Operation.REMOVE,
                "Removed by someone"),
            AttentionSetUpdate.createFromRead(
                updateTimestamp,
                changeOwner.getAccountId(),
                Operation.REMOVE,
                "Someone replied on the change"),
            AttentionSetUpdate.createFromRead(
                updateTimestamp,
                otherUserId,
                Operation.ADD,
                "Added by someone using the hovercard menu"),
            AttentionSetUpdate.createFromRead(
                invalidMultipleAttentionSetUpdate.getWhen(),
                otherUserId,
                Operation.REMOVE,
                "Removed by someone using the hovercard menu"),
            AttentionSetUpdate.createFromRead(
                invalidMultipleAttentionSetUpdate.getWhen(),
                changeOwner.getAccountId(),
                Operation.ADD,
                "Someone replied on the change"),
            AttentionSetUpdate.createFromRead(
                invalidAttentionSetUpdate.getWhen(),
                otherUserId,
                Operation.ADD,
                "Added by someone using the hovercard menu"));

    ChangeNotes notesAfterRewrite = newNotes(c);

    assertThat(notesBeforeRewrite.getAttentionSetUpdates())
        .containsExactlyElementsIn(attentionSetUpdatesBeforeRewrite);
    assertThat(notesAfterRewrite.getAttentionSetUpdates())
        .containsExactlyElementsIn(attentionSetUpdatesAfterRewrite);

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);
    assertFixedCommits(commitsToFix.build(), result, c.getId());

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff).hasSize(4);
    assertThat(commitHistoryDiff.get(0))
        .isEqualTo(
            "@@ -8 +8 @@\n"
                + "-Attention: {\"person_ident\":\"Gerrit User 2 \\u003c2@gerrit\\u003e\",\"operation\":\"ADD\",\"reason\":\"Added by Other Account using the hovercard menu\"}\n"
                + "+Attention: {\"person_ident\":\"Gerrit User 2 \\u003c2@gerrit\\u003e\",\"operation\":\"ADD\",\"reason\":\"Added by someone using the hovercard menu\"}\n");
    assertThat(Arrays.asList(commitHistoryDiff.get(1).split("\n")))
        .containsExactly(
            "@@ -7,2 +7,2 @@",
            "-Attention: {\"person_ident\":\"Gerrit User 1 \\u003c1@gerrit\\u003e\",\"operation\":\"ADD\",\"reason\":\"Other Account replied on the change\"}",
            "-Attention: {\"person_ident\":\"Gerrit User 2 \\u003c2@gerrit\\u003e\",\"operation\":\"REMOVE\",\"reason\":\"Removed by Other Account using the hovercard menu\"}",
            "+Attention: {\"person_ident\":\"Gerrit User 1 \\u003c1@gerrit\\u003e\",\"operation\":\"ADD\",\"reason\":\"Someone replied on the change\"}",
            "+Attention: {\"person_ident\":\"Gerrit User 2 \\u003c2@gerrit\\u003e\",\"operation\":\"REMOVE\",\"reason\":\"Removed by someone using the hovercard menu\"}");
    assertThat(Arrays.asList(commitHistoryDiff.get(2).split("\n")))
        .containsExactly(
            "@@ -7,2 +7,2 @@",
            "-Attention: {\"person_ident\":\"Other Account \\u003c2@gerrit\\u003e\",\"operation\":\"ADD\",\"reason\":\"Added by someone using the hovercard menu\"}",
            "-Attention: {\"person_ident\":\"Change Owner \\u003c1@gerrit\\u003e\",\"operation\":\"REMOVE\",\"reason\":\"Other Account replied on the change\"}",
            "+Attention: {\"person_ident\":\"Gerrit User 2 \\u003c2@gerrit\\u003e\",\"operation\":\"ADD\",\"reason\":\"Added by someone using the hovercard menu\"}",
            "+Attention: {\"person_ident\":\"Gerrit User 1 \\u003c1@gerrit\\u003e\",\"operation\":\"REMOVE\",\"reason\":\"Someone replied on the change\"}");
    assertThat(commitHistoryDiff.get(3))
        .isEqualTo(
            "@@ -7 +7 @@\n"
                + "-Attention: {\"person_ident\":\"Gerrit User 1 \\u003c1@gerrit\\u003e\",\"operation\":\"REMOVE\",\"reason\":\"Removed by Other Account by clicking the attention icon\"}\n"
                + "+Attention: {\"person_ident\":\"Gerrit User 1 \\u003c1@gerrit\\u003e\",\"operation\":\"REMOVE\",\"reason\":\"Removed by someone by clicking the attention icon\"}\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixAttentionFooter_okReason_noRewrite() throws Exception {
    Change c = newChange();
    ImmutableList<String> okAccountNames =
        ImmutableList.of(
            "Someone",
            "Someone else",
            "someone",
            "someone else",
            "Anonymous",
            "anonymous",
            "<GERRIT_ACCOUNT_1>",
            "<GERRIT_ACCOUNT_2>");
    ImmutableList.Builder<AttentionSetUpdate> attentionSetUpdatesBeforeRewrite =
        new ImmutableList.Builder<>();
    for (String okAccountName : okAccountNames) {
      ChangeUpdate firstAttentionSetUpdate = newUpdate(c, changeOwner);
      firstAttentionSetUpdate.putReviewer(otherUserId, REVIEWER);
      firstAttentionSetUpdate.addToPlannedAttentionSetUpdates(
          AttentionSetUpdate.createForWrite(
              otherUserId,
              Operation.ADD,
              String.format("Added by %s using the hovercard menu", okAccountName)));
      firstAttentionSetUpdate.commit();
      ChangeUpdate secondAttentionSetUpdate = newUpdate(c, changeOwner);
      secondAttentionSetUpdate.addToPlannedAttentionSetUpdates(
          AttentionSetUpdate.createForWrite(
              changeOwner.getAccountId(),
              Operation.ADD,
              String.format("%s replied on the change", okAccountName)));
      secondAttentionSetUpdate.addToPlannedAttentionSetUpdates(
          AttentionSetUpdate.createForWrite(
              otherUserId,
              Operation.REMOVE,
              String.format("Removed by %s using the hovercard menu", okAccountName)));
      secondAttentionSetUpdate.commit();
      ChangeUpdate thirdAttentionSetUpdate = newUpdate(c, changeOwner);
      thirdAttentionSetUpdate.addToPlannedAttentionSetUpdates(
          AttentionSetUpdate.createForWrite(
              changeOwner.getAccountId(),
              Operation.REMOVE,
              String.format("Removed by %s by clicking the attention icon", okAccountName)));
      thirdAttentionSetUpdate.commit();
      attentionSetUpdatesBeforeRewrite.add(
          AttentionSetUpdate.createFromRead(
              thirdAttentionSetUpdate.getWhen(),
              changeOwner.getAccountId(),
              Operation.REMOVE,
              String.format("Removed by %s by clicking the attention icon", okAccountName)),
          AttentionSetUpdate.createFromRead(
              secondAttentionSetUpdate.getWhen(),
              otherUserId,
              Operation.REMOVE,
              String.format("Removed by %s using the hovercard menu", okAccountName)),
          AttentionSetUpdate.createFromRead(
              secondAttentionSetUpdate.getWhen(),
              changeOwner.getAccountId(),
              Operation.ADD,
              String.format("%s replied on the change", okAccountName)),
          AttentionSetUpdate.createFromRead(
              firstAttentionSetUpdate.getWhen(),
              otherUserId,
              Operation.ADD,
              String.format("Added by %s using the hovercard menu", okAccountName)));
    }

    ChangeNotes notesBeforeRewrite = newNotes(c);
    assertThat(notesBeforeRewrite.getAttentionSetUpdates())
        .containsExactlyElementsIn(attentionSetUpdatesBeforeRewrite.build());

    Ref metaRefBefore = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult backfillResult = rewriter.backfillProject(project, repo, options);
    ChangeNotes notesAfterRewrite = newNotes(c);
    Ref metaRefAfter = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    assertThat(notesBeforeRewrite.getMetaId()).isEqualTo(notesAfterRewrite.getMetaId());
    assertThat(metaRefBefore.getObjectId()).isEqualTo(metaRefAfter.getObjectId());
    assertThat(backfillResult.fixedRefDiff).isEmpty();
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixSubmitChangeMessage() throws Exception {
    Change c = newChange();
    ImmutableList.Builder<ObjectId> commitsToFix = new ImmutableList.Builder<>();
    ChangeUpdate invalidMergedMessageUpdate = newUpdate(c, changeOwner);
    invalidMergedMessageUpdate.setChangeMessage(
        "Change has been successfully merged by " + changeOwner.getName());
    invalidMergedMessageUpdate.setTopic("");

    commitsToFix.add(invalidMergedMessageUpdate.commit());
    ChangeUpdate invalidCherryPickedMessageUpdate = newUpdate(c, changeOwner);
    invalidCherryPickedMessageUpdate.setChangeMessage(
        "Change has been successfully cherry-picked as e40dc1a50dc7f457a37579e2755374f3e1a5413b by "
            + changeOwner.getName());

    commitsToFix.add(invalidCherryPickedMessageUpdate.commit());
    ChangeUpdate invalidRebasedMessageUpdate = newUpdate(c, changeOwner);
    invalidRebasedMessageUpdate.setChangeMessage(
        "Change has been successfully rebased and submitted as e40dc1a50dc7f457a37579e2755374f3e1a5413b by "
            + changeOwner.getName());

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
    assertFixedCommits(commitsToFix.build(), result, c.getId());

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -6 +6 @@\n"
                + "-Change has been successfully merged by Change Owner\n"
                + "+Change has been successfully merged\n",
            "@@ -6 +6 @@\n"
                + "-Change has been successfully cherry-picked as e40dc1a50dc7f457a37579e2755374f3e1a5413b by Change Owner\n"
                + "+Change has been successfully cherry-picked as e40dc1a50dc7f457a37579e2755374f3e1a5413b\n",
            "@@ -6 +6 @@\n"
                + "-Change has been successfully rebased and submitted as e40dc1a50dc7f457a37579e2755374f3e1a5413b by Change Owner\n"
                + "+Change has been successfully rebased and submitted as e40dc1a50dc7f457a37579e2755374f3e1a5413b\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixSubmitChangeMessageAndFooters() throws Exception {
    Change c = newChange();
    PersonIdent invalidAuthorIdent =
        new PersonIdent(
            changeOwner.getName(),
            changeNoteUtil.getAccountIdAsEmailAddress(changeOwner.getAccountId()),
            TimeUtil.now(),
            serverIdent.getZoneId());
    String changeOwnerIdentToFix = getAccountIdentToFix(changeOwner.getAccount());
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(
            c,
            "Change has been successfully merged by " + changeOwner.getName(),
            "Status: merged",
            "Tag: autogenerated:gerrit:merged",
            "Reviewer: " + changeOwnerIdentToFix,
            "Label: SUBM=+1",
            "Submission-id: 6310-1521542139810-cfb7e159",
            "Submitted-with: OK",
            "Submitted-with: OK: Code-Review: " + changeOwnerIdentToFix),
        invalidAuthorIdent);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -1 +1 @@\n"
                + "-author Change Owner <1@gerrit> 1254344405 -0700\n"
                + "+author Gerrit User 1 <1@gerrit> 1254344405 -0700\n"
                + "@@ -6 +6 @@\n"
                + "-Change has been successfully merged by Change Owner\n"
                + "+Change has been successfully merged\n"
                + "@@ -11 +11 @@\n"
                + "-Reviewer: Change Owner <1@gerrit>\n"
                + "+Reviewer: Gerrit User 1 <1@gerrit>\n"
                + "@@ -15 +15 @@\n"
                + "-Submitted-with: OK: Code-Review: Change Owner <1@gerrit>\n"
                + "+Submitted-with: OK: Code-Review: Gerrit User 1 <1@gerrit>\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixSubmittedWithFooterIdent() throws Exception {
    Change c = newChange();

    ChangeUpdate preSubmitUpdate = newUpdate(c, changeOwner);
    preSubmitUpdate.setChangeMessage("Per-submit update");
    preSubmitUpdate.commit();

    String otherUserIdentToFix = getAccountIdentToFix(otherUser.getAccount());
    String changeOwnerIdentToFix = getAccountIdentToFix(changeOwner.getAccount());
    RevCommit invalidUpdateCommit =
        writeUpdate(
            RefNames.changeMetaRef(c.getId()),
            getChangeUpdateBody(
                c,
                /*changeMessage=*/ null,
                "Label: SUBM=+1",
                "Submission-id: 5271-1496917120975-10a10df9",
                "Submitted-with: NOT_READY",
                "Submitted-with: NEED: Code-Review: " + otherUserIdentToFix,
                "Submitted-with: OK: Code-Style",
                "Submitted-with: OK: Verified: " + changeOwnerIdentToFix,
                "Submitted-with: FORCED with error"),
            getAuthorIdent(changeOwner.getAccount()));

    ChangeUpdate postSubmitUpdate = newUpdate(c, changeOwner);
    postSubmitUpdate.setChangeMessage("Per-submit update");
    postSubmitUpdate.commit();
    Ref metaRefBeforeRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));

    ImmutableList<RevCommit> commitsBeforeRewrite = logMetaRef(repo, metaRefBeforeRewrite);

    int invalidCommitIndex = commitsBeforeRewrite.indexOf(invalidUpdateCommit);
    ChangeNotes notesBeforeRewrite = newNotes(c);

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));

    ChangeNotes notesAfterRewrite = newNotes(c);
    ImmutableList<SubmitRecord> expectedRecords =
        ImmutableList.of(
            submitRecord(
                "NOT_READY",
                null,
                submitLabel(CODE_REVIEW, "NEED", otherUserId),
                submitLabel("Code-Style", "OK", null),
                submitLabel(VERIFIED, "OK", changeOwner.getAccountId())),
            submitRecord("FORCED", " with error"));
    assertThat(notesBeforeRewrite.getSubmitRecords()).isEqualTo(expectedRecords);
    assertThat(notesAfterRewrite.getSubmitRecords()).isEqualTo(expectedRecords);

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(
        commitsBeforeRewrite, commitsAfterRewrite, ImmutableList.of(invalidCommitIndex));
    assertFixedCommits(ImmutableList.of(invalidUpdateCommit.getId()), result, c.getId());

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -10 +10 @@\n"
                + "-Submitted-with: NEED: Code-Review: Other Account <2@gerrit>\n"
                + "+Submitted-with: NEED: Code-Review: Gerrit User 2 <2@gerrit>\n"
                + "@@ -12 +12 @@\n"
                + "-Submitted-with: OK: Verified: Change Owner <1@gerrit>\n"
                + "+Submitted-with: OK: Verified: Gerrit User 1 <1@gerrit>\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
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
    assertFixedCommits(commitsToFix.build(), result, c.getId());

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -6 +6 @@\n"
                + "-Change message removed by: Change Owner\n"
                + "+Change message removed\n",
            "@@ -6 +6 @@\n"
                + "-Change message removed by: Change Owner\n"
                + "+Change message removed\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixCodeOwnersOnAddReviewerChangeMessage() throws Exception {

    Account reviewer =
        Account.builder(Account.id(3), TimeUtil.now())
            .setFullName("Reviewer User")
            .setPreferredEmail("reviewer@account.com")
            .build();
    accountCache.put(reviewer);
    Account duplicateCodeOwner =
        Account.builder(Account.id(4), TimeUtil.now()).setFullName(changeOwner.getName()).build();
    accountCache.put(duplicateCodeOwner);
    Account duplicateReviewer =
        Account.builder(Account.id(5), TimeUtil.now()).setFullName(reviewer.getName()).build();
    accountCache.put(duplicateReviewer);
    Change c = newChange();
    ImmutableList.Builder<ObjectId> commitsToFix = new ImmutableList.Builder<>();
    ChangeUpdate addReviewerUpdate = newCodeOwnerAddReviewerUpdate(c, changeOwner);
    addReviewerUpdate.putReviewer(reviewer.id(), REVIEWER);
    addReviewerUpdate.commit();
    ChangeUpdate invalidOnAddReviewerUpdate = newCodeOwnerAddReviewerUpdate(c, changeOwner);
    invalidOnAddReviewerUpdate.setChangeMessage(
        "Reviewer User who was added as reviewer owns the following files:\n"
            + "   * file1.java\n"
            + "   * file2.ts\n");
    commitsToFix.add(invalidOnAddReviewerUpdate.commit());
    ChangeUpdate addOtherReviewerUpdate = newCodeOwnerAddReviewerUpdate(c, changeOwner);
    addOtherReviewerUpdate.putReviewer(otherUserId, REVIEWER);
    addOtherReviewerUpdate.commit();
    ChangeUpdate invalidOnAddReviewerMultipleReviewerUpdate =
        newCodeOwnerAddReviewerUpdate(c, changeOwner);
    invalidOnAddReviewerMultipleReviewerUpdate.setChangeMessage(
        "Reviewer User who was added as reviewer owns the following files:\n"
            + "   * file1.java\n"
            + "\nOther Account who was added as reviewer owns the following files:\n"
            + "   * file3.js\n"
            + "\nMissing Reviewer who was added as reviewer owns the following files:\n"
            + "   * file4.java\n");
    commitsToFix.add(invalidOnAddReviewerMultipleReviewerUpdate.commit());
    ChangeUpdate addDuplicateReviewerUpdate = newCodeOwnerAddReviewerUpdate(c, changeOwner);
    addDuplicateReviewerUpdate.putReviewer(duplicateReviewer.id(), REVIEWER);
    addDuplicateReviewerUpdate.commit();
    // Reviewer name resolves to multiple accounts in the same change
    ChangeUpdate onAddReviewerUpdateWithDuplicate = newCodeOwnerAddReviewerUpdate(c, changeOwner);
    onAddReviewerUpdateWithDuplicate.setChangeMessage(
        "Reviewer User who was added as reviewer owns the following files:\n"
            + "   * file6.java\n");
    commitsToFix.add(onAddReviewerUpdateWithDuplicate.commit());

    ChangeUpdate validOnAddReviewerUpdate = newCodeOwnerAddReviewerUpdate(c, changeOwner);
    validOnAddReviewerUpdate.setChangeMessage(
        "Gerrit Account who was added as reviewer owns the following files:\n"
            + "   * file1.java\n"
            + "\n<GERRIT_ACCOUNT_1> who was added as reviewer owns the following files:\n"
            + "   * file3.js\n");
    validOnAddReviewerUpdate.commit();

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

    assertThat(changeMessages(notesBeforeRewrite)).hasSize(4);
    assertThat(changeMessages(notesAfterRewrite))
        .containsExactly(
            "<GERRIT_ACCOUNT_3>, who was added as reviewer owns the following files:\n"
                + "   * file1.java\n"
                + "   * file2.ts\n",
            "<GERRIT_ACCOUNT_3>, who was added as reviewer owns the following files:\n"
                + "   * file1.java\n"
                + "\n<GERRIT_ACCOUNT_2>, who was added as reviewer owns the following files:\n"
                + "   * file3.js\n"
                + "\nAdded reviewer owns the following files:\n"
                + "   * file4.java\n",
            "Added reviewer owns the following files:\n" + "   * file6.java\n",
            "Gerrit Account who was added as reviewer owns the following files:\n"
                + "   * file1.java\n"
                + "\n<GERRIT_ACCOUNT_1> who was added as reviewer owns the following files:\n"
                + "   * file3.js\n");

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);
    assertFixedCommits(commitsToFix.build(), result, c.getId());

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -6 +6 @@\n"
                + "-Reviewer User who was added as reviewer owns the following files:\n"
                + "+<GERRIT_ACCOUNT_3>, who was added as reviewer owns the following files:\n",
            "@@ -6 +6 @@\n"
                + "-Reviewer User who was added as reviewer owns the following files:\n"
                + "+<GERRIT_ACCOUNT_3>, who was added as reviewer owns the following files:\n"
                + "@@ -9 +9 @@\n"
                + "-Other Account who was added as reviewer owns the following files:\n"
                + "+<GERRIT_ACCOUNT_2>, who was added as reviewer owns the following files:\n"
                + "@@ -12 +12 @@\n"
                + "-Missing Reviewer who was added as reviewer owns the following files:\n"
                + "+Added reviewer owns the following files:\n",
            "@@ -6 +6 @@\n"
                + "-Reviewer User who was added as reviewer owns the following files:\n"
                + "+Added reviewer owns the following files:\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixCodeOwnersOnReviewChangeMessage() throws Exception {

    Change c = newChange();
    ImmutableList.Builder<ObjectId> commitsToFix = new ImmutableList.Builder<>();

    ChangeUpdate invalidOnReviewUpdate = newUpdate(c, changeOwner);
    invalidOnReviewUpdate.setChangeMessage(
        "Patch Set 1: Any-Label+2 Other-Label+2 Code-Review+2\n\n"
            + "By voting Code-Review+2 the following files are now code-owner approved by Change Owner:\n"
            + "   * file1.java\n"
            + "   * file2.ts\n"
            + "By voting Any-Label+2 the code-owners submit requirement is overridden by Change Owner\n"
            + "By voting Other-Label+2 the code-owners submit requirement is still overridden by Change Owner\n");
    commitsToFix.add(invalidOnReviewUpdate.commit());

    ChangeUpdate invalidOnReviewUpdateAnyOrder = newUpdate(c, changeOwner);
    invalidOnReviewUpdateAnyOrder.setChangeMessage(
        "Patch Set 1: Any-Label+2 Other-Label+2 Code-Review+2\n\n"
            + "By voting Any-Label+2 the code-owners submit requirement is overridden by Change Owner\n"
            + "By voting Other-Label+2 the code-owners submit requirement is still overridden by Change Owner\n"
            + "By voting Code-Review+2 the following files are now code-owner approved by Change Owner:\n"
            + "   * file1.java\n"
            + "   * file2.ts\n");
    commitsToFix.add(invalidOnReviewUpdateAnyOrder.commit());
    ChangeUpdate invalidOnApprovalUpdate = newUpdate(c, otherUser);
    invalidOnApprovalUpdate.setChangeMessage(
        "Patch Set 1: -Code-Review\n\n"
            + "By removing the Code-Review+2 vote the following files are no longer explicitly code-owner approved by Other Account:\n"
            + "   * file1.java\n"
            + "   * file2.ts\n"
            + "\nThe listed files are still implicitly approved by Other Account.\n");
    commitsToFix.add(invalidOnApprovalUpdate.commit());

    ChangeUpdate invalidOnOverrideUpdate = newUpdate(c, changeOwner);
    invalidOnOverrideUpdate.setChangeMessage(
        "Patch Set 1: -Owners-Override\n\n"
            + "(1 comment)\n\n"
            + "By removing the Owners-Override+1 vote the code-owners submit requirement is no longer overridden by Change Owner\n");

    commitsToFix.add(invalidOnOverrideUpdate.commit());

    ChangeUpdate partiallyValidOnReviewUpdate = newUpdate(c, changeOwner);
    partiallyValidOnReviewUpdate.setChangeMessage(
        "Patch Set 1: Any-Label+2 Code-Review+2\n\n"
            + "By voting Code-Review+2 the following files are now code-owner approved by <GERRIT_ACCOUNT_1>:\n"
            + "   * file1.java\n"
            + "   * file2.ts\n"
            + "By voting Any-Label+2 the code-owners submit requirement is overridden by Change Owner\n");
    commitsToFix.add(partiallyValidOnReviewUpdate.commit());

    ChangeUpdate validOnApprovalUpdate = newUpdate(c, changeOwner);
    validOnApprovalUpdate.setChangeMessage(
        "Patch Set 1: Code-Review-2\n\n"
            + "By voting Code-Review-2 the following files are no longer explicitly code-owner approved by <GERRIT_ACCOUNT_1>:\n"
            + "   * file4.java\n");
    validOnApprovalUpdate.commit();

    ChangeUpdate validOnOverrideUpdate = newUpdate(c, changeOwner);
    validOnOverrideUpdate.setChangeMessage(
        "Patch Set 1: Owners-Override+1\n\n"
            + "By voting Owners-Override+1 the code-owners submit requirement is still overridden by <GERRIT_ACCOUNT_1>\n");
    validOnOverrideUpdate.commit();

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

    assertThat(changeMessages(notesBeforeRewrite)).hasSize(7);
    assertThat(changeMessages(notesAfterRewrite))
        .containsExactly(
            "Patch Set 1: Any-Label+2 Other-Label+2 Code-Review+2\n\n"
                + "By voting Code-Review+2 the following files are now code-owner approved by <GERRIT_ACCOUNT_1>:\n"
                + "   * file1.java\n"
                + "   * file2.ts\n"
                + "By voting Any-Label+2 the code-owners submit requirement is overridden by <GERRIT_ACCOUNT_1>\n"
                + "By voting Other-Label+2 the code-owners submit requirement is still overridden by <GERRIT_ACCOUNT_1>\n",
            "Patch Set 1: Any-Label+2 Other-Label+2 Code-Review+2\n\n"
                + "By voting Any-Label+2 the code-owners submit requirement is overridden by <GERRIT_ACCOUNT_1>\n"
                + "By voting Other-Label+2 the code-owners submit requirement is still overridden by <GERRIT_ACCOUNT_1>\n"
                + "By voting Code-Review+2 the following files are now code-owner approved by <GERRIT_ACCOUNT_1>:\n"
                + "   * file1.java\n"
                + "   * file2.ts\n",
            "Patch Set 1: -Code-Review\n"
                + "\n"
                + "By removing the Code-Review+2 vote the following files are no longer explicitly code-owner approved by <GERRIT_ACCOUNT_2>:\n"
                + "   * file1.java\n"
                + "   * file2.ts\n"
                + "\nThe listed files are still implicitly approved by <GERRIT_ACCOUNT_2>.\n",
            "Patch Set 1: -Owners-Override\n"
                + "\n"
                + "(1 comment)\n"
                + "\n"
                + "By removing the Owners-Override+1 vote the code-owners submit requirement is no longer overridden by <GERRIT_ACCOUNT_1>\n",
            "Patch Set 1: Any-Label+2 Code-Review+2\n\n"
                + "By voting Code-Review+2 the following files are now code-owner approved by <GERRIT_ACCOUNT_1>:\n"
                + "   * file1.java\n"
                + "   * file2.ts\n"
                + "By voting Any-Label+2 the code-owners submit requirement is overridden by <GERRIT_ACCOUNT_1>\n",
            "Patch Set 1: Code-Review-2\n\n"
                + "By voting Code-Review-2 the following files are no longer explicitly code-owner approved by <GERRIT_ACCOUNT_1>:\n"
                + "   * file4.java\n",
            "Patch Set 1: Owners-Override+1\n"
                + "\n"
                + "By voting Owners-Override+1 the code-owners submit requirement is still overridden by <GERRIT_ACCOUNT_1>\n");

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);
    assertFixedCommits(commitsToFix.build(), result, c.getId());

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -8 +8 @@\n"
                + "-By voting Code-Review+2 the following files are now code-owner approved by Change Owner:\n"
                + "+By voting Code-Review+2 the following files are now code-owner approved by <GERRIT_ACCOUNT_1>:\n"
                + "@@ -11,2 +11,2 @@\n"
                + "-By voting Any-Label+2 the code-owners submit requirement is overridden by Change Owner\n"
                + "-By voting Other-Label+2 the code-owners submit requirement is still overridden by Change Owner\n"
                + "+By voting Any-Label+2 the code-owners submit requirement is overridden by <GERRIT_ACCOUNT_1>\n"
                + "+By voting Other-Label+2 the code-owners submit requirement is still overridden by <GERRIT_ACCOUNT_1>\n",
            "@@ -8,3 +8,3 @@\n"
                + "-By voting Any-Label+2 the code-owners submit requirement is overridden by Change Owner\n"
                + "-By voting Other-Label+2 the code-owners submit requirement is still overridden by Change Owner\n"
                + "-By voting Code-Review+2 the following files are now code-owner approved by Change Owner:\n"
                + "+By voting Any-Label+2 the code-owners submit requirement is overridden by <GERRIT_ACCOUNT_1>\n"
                + "+By voting Other-Label+2 the code-owners submit requirement is still overridden by <GERRIT_ACCOUNT_1>\n"
                + "+By voting Code-Review+2 the following files are now code-owner approved by <GERRIT_ACCOUNT_1>:\n",
            "@@ -8 +8 @@\n"
                + "-By removing the Code-Review+2 vote the following files are no longer explicitly code-owner approved by Other Account:\n"
                + "+By removing the Code-Review+2 vote the following files are no longer explicitly code-owner approved by <GERRIT_ACCOUNT_2>:\n"
                + "@@ -12 +12 @@\n"
                + "-The listed files are still implicitly approved by Other Account.\n"
                + "+The listed files are still implicitly approved by <GERRIT_ACCOUNT_2>.\n",
            "@@ -10 +10 @@\n"
                + "-By removing the Owners-Override+1 vote the code-owners submit requirement is no longer overridden by Change Owner\n"
                + "+By removing the Owners-Override+1 vote the code-owners submit requirement is no longer overridden by <GERRIT_ACCOUNT_1>\n",
            "@@ -11 +11 @@\n"
                + "-By voting Any-Label+2 the code-owners submit requirement is overridden by Change Owner\n"
                + "+By voting Any-Label+2 the code-owners submit requirement is overridden by <GERRIT_ACCOUNT_1>\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
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
    assertFixedCommits(ImmutableList.of(invalidUpdateCommit.getId()), result, c.getId());

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

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -9 +9 @@\n"
                + "-Assignee: Change Owner <1@gerrit>\n"
                + "+Assignee: Gerrit User 1 <1@gerrit>\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixAssigneeChangeMessage() throws Exception {
    Change c = newChange();

    ImmutableList<ObjectId> commitsToFix =
        new ImmutableList.Builder<ObjectId>()
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
            "Assignee added: " + AccountTemplateUtil.getAccountTemplate(changeOwner.getAccountId()),
            String.format(
                "Assignee changed from: %s to: %s",
                AccountTemplateUtil.getAccountTemplate(changeOwner.getAccountId()),
                AccountTemplateUtil.getAccountTemplate(otherUser.getAccountId())),
            "Assignee deleted: "
                + AccountTemplateUtil.getAccountTemplate(otherUser.getAccountId()));

    Ref metaRefAfterRewrite = repo.exactRef(RefNames.changeMetaRef(c.getId()));
    assertThat(metaRefAfterRewrite.getObjectId()).isNotEqualTo(metaRefBeforeRewrite.getObjectId());

    ImmutableList<RevCommit> commitsAfterRewrite = logMetaRef(repo, metaRefAfterRewrite);
    assertValidCommits(commitsBeforeRewrite, commitsAfterRewrite, invalidCommits);
    assertFixedCommits(commitsToFix, result, c.getId());

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -6 +6 @@\n"
                + "-Assignee added: Change Owner <change@owner.com>\n"
                + "+Assignee added: <GERRIT_ACCOUNT_1>\n",
            "@@ -6 +6 @@\n"
                + "-Assignee changed from: Change Owner <change@owner.com> to: Other Account <other@account.com>\n"
                + "+Assignee changed from: <GERRIT_ACCOUNT_1> to: <GERRIT_ACCOUNT_2>\n",
            "@@ -6 +6 @@\n"
                + "-Assignee deleted: Other Account <other@account.com>\n"
                + "+Assignee deleted: <GERRIT_ACCOUNT_2>\n"
                // Both empty value and space are parsed as deleted assignee anyway.
                + "@@ -9 +9 @@\n"
                + "-Assignee:\n"
                + "+Assignee: \n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void fixAssigneeChangeMessageNoAssigneeFooter() throws Exception {
    Change c = newChange();
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(c, "Assignee added: " + changeOwner.getName()),
        getAuthorIdent(changeOwner.getAccount()));

    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(
            c,
            String.format(
                "Assignee changed from: %s to: %s",
                changeOwner.getNameEmail(), otherUser.getNameEmail())),
        getAuthorIdent(otherUser.getAccount()));
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(c, "Assignee deleted: " + otherUser.getName()),
        getAuthorIdent(changeOwner.getAccount()));
    Account reviewer =
        Account.builder(Account.id(3), TimeUtil.now())
            .setFullName("Reviewer User")
            .setPreferredEmail("reviewer@account.com")
            .build();
    accountCache.put(reviewer);
    // Even though account is present in the cache, it won't be used because it does not appear in
    // the history of this change.
    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(c, "Assignee added: " + reviewer.getName()),
        getAuthorIdent(changeOwner.getAccount()));

    writeUpdate(
        RefNames.changeMetaRef(c.getId()),
        getChangeUpdateBody(c, "Assignee deleted: Gerrit Account"),
        getAuthorIdent(changeOwner.getAccount()));

    RunOptions options = new RunOptions();
    options.dryRun = false;
    BackfillResult result = rewriter.backfillProject(project, repo, options);
    assertThat(result.fixedRefDiff.keySet()).containsExactly(RefNames.changeMetaRef(c.getId()));
    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
    assertThat(commitHistoryDiff)
        .containsExactly(
            "@@ -6 +6 @@\n"
                + "-Assignee added: Change Owner\n"
                + "+Assignee added: <GERRIT_ACCOUNT_1>\n",
            "@@ -6 +6 @@\n"
                + "-Assignee changed from: Change Owner <change@owner.com> to: Other Account <other@account.com>\n"
                + "+Assignee changed from: <GERRIT_ACCOUNT_1> to: <GERRIT_ACCOUNT_2>\n",
            "@@ -6 +6 @@\n"
                + "-Assignee deleted: Other Account\n"
                + "+Assignee deleted: <GERRIT_ACCOUNT_2>\n",
            "@@ -6 +6 @@\n" + "-Assignee added: Reviewer User\n" + "+Assignee was added.\n");
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  @Test
  public void singleRunFixesAll() throws Exception {
    Change c = newChange();
    Instant when = TimeUtil.now();
    String assigneeIdentToFix = getAccountIdentToFix(otherUser.getAccount());
    PersonIdent authorIdentToFix =
        new PersonIdent(
            changeOwner.getName(),
            changeNoteUtil.getAccountIdAsEmailAddress(changeOwner.getAccountId()),
            when,
            serverIdent.getZoneId());

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
    assertFixedCommits(ImmutableList.of(invalidUpdateCommit.getId()), result, c.getId());

    RevCommit fixedUpdateCommit = commitsAfterRewrite.get(invalidCommitIndex);
    assertThat(invalidUpdateCommit.getAuthorIdent())
        .isNotEqualTo(fixedUpdateCommit.getAuthorIdent());
    assertThat(invalidUpdateCommit.getFullMessage()).contains(otherUser.getName());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(changeOwner.getName());
    assertThat(fixedUpdateCommit.getFullMessage()).doesNotContain(otherUser.getName());

    List<String> commitHistoryDiff = commitHistoryDiff(result, c.getId());
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
    BackfillResult secondRunResult = rewriter.backfillProject(project, repo, options);
    assertThat(secondRunResult.fixedRefDiff.keySet()).isEmpty();
    assertThat(secondRunResult.refsFailedToFix).isEmpty();
  }

  private RevCommit writeUpdate(String metaRef, String body, PersonIdent author) throws Exception {
    return tr.branch(metaRef).commit().message(body).author(author).committer(serverIdent).create();
  }

  private String getChangeUpdateBody(Change change, String changeMessage, String... footers) {
    StringBuilder commitBody = new StringBuilder();
    commitBody.append("Update patch set ").append(change.currentPatchSetId().get());
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
            .collect(toImmutableList());

    ImmutableList<RevCommit> validCommitsAfterRewrite =
        IntStream.range(0, commitsAfterRewrite.size())
            .filter(i -> !invalidCommits.contains(i))
            .mapToObj(commitsAfterRewrite::get)
            .collect(toImmutableList());

    assertThat(validCommitsBeforeRewrite).hasSize(validCommitsAfterRewrite.size());
    for (int i = 0; i < validCommitsAfterRewrite.size(); i++) {
      RevCommit actual = validCommitsAfterRewrite.get(i);
      RevCommit expected = validCommitsBeforeRewrite.get(i);
      assertThat(actual.getAuthorIdent()).isEqualTo(expected.getAuthorIdent());
      assertThat(actual.getCommitterIdent()).isEqualTo(expected.getCommitterIdent());
      assertThat(actual.getFullMessage()).isEqualTo(expected.getFullMessage());
    }
  }

  private void assertFixedCommits(
      ImmutableList<ObjectId> expectedFixedCommits, BackfillResult result, Change.Id changeId) {
    assertThat(
            result.fixedRefDiff.get(RefNames.changeMetaRef(changeId)).stream()
                .map(CommitDiff::oldSha1)
                .collect(toImmutableList()))
        .containsExactlyElementsIn(expectedFixedCommits);
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

  protected ChangeUpdate newCodeOwnerAddReviewerUpdate(Change c, CurrentUser user)
      throws Exception {
    ChangeUpdate update = newUpdate(c, user, true);
    update.setTag("autogenerated:gerrit:code-owners:addReviewer");
    return update;
  }

  private ImmutableList<String> commitHistoryDiff(BackfillResult result, Change.Id changeId) {
    return result.fixedRefDiff.get(RefNames.changeMetaRef(changeId)).stream()
        .map(CommitDiff::diff)
        .collect(toImmutableList());
  }

  private PersonIdent getAuthorIdent(Account account) {
    return changeNoteUtil.newAccountIdIdent(account.id(), TimeUtil.now(), serverIdent);
  }
}
