// Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;

/**
 * Immutable state associated with a change meta ref at a given commit.
 *
 * <p>One instance is the output of a single {@link ChangeNotesParser}, and contains types required
 * to support public methods on {@link ChangeNotes}. It is intended to be cached in-process.
 *
 * <p>Note that {@link ChangeNotes} contains more than just a single {@code ChangeNoteState}, such
 * as per-draft information, so that class is not cached directly.
 */
@AutoValue
public abstract class ChangeNotesState {
  static ChangeNotesState empty(Change change) {
    return Builder.empty(change.getId()).build();
  }

  static Builder builder() {
    return new AutoValue_ChangeNotesState.Builder();
  }

  static ChangeNotesState create(
      ObjectId metaId,
      Change.Id changeId,
      Change.Key changeKey,
      Timestamp createdOn,
      Timestamp lastUpdatedOn,
      Account.Id owner,
      String branch,
      @Nullable PatchSet.Id currentPatchSetId,
      String subject,
      @Nullable String topic,
      @Nullable String originalSubject,
      @Nullable String submissionId,
      @Nullable Account.Id assignee,
      @Nullable Change.Status status,
      Set<Account.Id> pastAssignees,
      Set<String> hashtags,
      Map<PatchSet.Id, PatchSet> patchSets,
      ListMultimap<PatchSet.Id, PatchSetApproval> approvals,
      ReviewerSet reviewers,
      ReviewerByEmailSet reviewersByEmail,
      ReviewerSet pendingReviewers,
      ReviewerByEmailSet pendingReviewersByEmail,
      List<Account.Id> allPastReviewers,
      List<ReviewerStatusUpdate> reviewerUpdates,
      List<SubmitRecord> submitRecords,
      List<ChangeMessage> changeMessages,
      ListMultimap<RevId, Comment> publishedComments,
      @Nullable Timestamp readOnlyUntil,
      boolean isPrivate,
      boolean workInProgress,
      boolean reviewStarted,
      @Nullable Change.Id revertOf) {
    checkNotNull(
        metaId,
        "metaId is required when passing arguments to create(...). To create an empty %s without"
            + " NoteDb data, use empty(...) instead",
        ChangeNotesState.class.getSimpleName());
    return builder()
        .metaId(metaId)
        .changeId(changeId)
        .columns(
            new AutoValue_ChangeNotesState_ChangeColumns.Builder()
                .changeKey(changeKey)
                .createdOn(createdOn)
                .lastUpdatedOn(lastUpdatedOn)
                .owner(owner)
                .branch(branch)
                .status(status)
                .currentPatchSetId(currentPatchSetId)
                .subject(subject)
                .topic(topic)
                .originalSubject(originalSubject)
                .submissionId(submissionId)
                .assignee(assignee)
                .isPrivate(isPrivate)
                .workInProgress(workInProgress)
                .reviewStarted(reviewStarted)
                .revertOf(revertOf)
                .build())
        .pastAssignees(pastAssignees)
        .hashtags(hashtags)
        .patchSets(patchSets.entrySet())
        .approvals(approvals.entries())
        .reviewers(reviewers)
        .reviewersByEmail(reviewersByEmail)
        .pendingReviewers(pendingReviewers)
        .pendingReviewersByEmail(pendingReviewersByEmail)
        .allPastReviewers(allPastReviewers)
        .reviewerUpdates(reviewerUpdates)
        .submitRecords(submitRecords)
        .changeMessages(changeMessages)
        .publishedComments(publishedComments)
        .readOnlyUntil(readOnlyUntil)
        .build();
  }

  /**
   * Subset of Change columns that can be represented in NoteDb.
   *
   * <p>Notable exceptions include rowVersion and noteDbState, which are only make sense when read
   * from NoteDb, so they cannot be cached.
   *
   * <p>Fields should match the column names in {@link Change}, and are in listed column order.
   */
  @AutoValue
  abstract static class ChangeColumns {
    abstract Change.Key changeKey();

    abstract Timestamp createdOn();

    abstract Timestamp lastUpdatedOn();

    abstract Account.Id owner();

    // Project not included, as it's not stored anywhere in the meta ref.
    abstract String branch();

    // TODO(dborowitz): Use a sensible default other than null
    @Nullable
    abstract Change.Status status();

    @Nullable
    abstract PatchSet.Id currentPatchSetId();

    abstract String subject();

    @Nullable
    abstract String topic();

    @Nullable
    abstract String originalSubject();

    @Nullable
    abstract String submissionId();

    @Nullable
    abstract Account.Id assignee();

    abstract boolean isPrivate();

    abstract boolean workInProgress();

    abstract boolean reviewStarted();

    @Nullable
    abstract Change.Id revertOf();

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder changeKey(Change.Key changeKey);

      abstract Builder createdOn(Timestamp createdOn);

      abstract Builder lastUpdatedOn(Timestamp lastUpdatedOn);

      abstract Builder owner(Account.Id owner);

      abstract Builder branch(String branch);

      abstract Builder currentPatchSetId(@Nullable PatchSet.Id currentPatchSetId);

      abstract Builder subject(String subject);

      abstract Builder topic(@Nullable String topic);

      abstract Builder originalSubject(@Nullable String originalSubject);

      abstract Builder submissionId(@Nullable String submissionId);

      abstract Builder assignee(@Nullable Account.Id assignee);

      abstract Builder status(@Nullable Change.Status status);

      abstract Builder isPrivate(boolean isPrivate);

      abstract Builder workInProgress(boolean workInProgress);

      abstract Builder reviewStarted(boolean reviewStarted);

      abstract Builder revertOf(@Nullable Change.Id revertOf);

      abstract ChangeColumns build();
    }
  }

  // Only null if NoteDb is disabled.
  @Nullable
  abstract ObjectId metaId();

  abstract Change.Id changeId();

  // Only null if NoteDb is disabled.
  @Nullable
  abstract ChangeColumns columns();

  // Other related to this Change.
  abstract ImmutableSet<Account.Id> pastAssignees();

  abstract ImmutableSet<String> hashtags();

  abstract ImmutableList<Map.Entry<PatchSet.Id, PatchSet>> patchSets();

  abstract ImmutableList<Map.Entry<PatchSet.Id, PatchSetApproval>> approvals();

  abstract ReviewerSet reviewers();

  abstract ReviewerByEmailSet reviewersByEmail();

  abstract ReviewerSet pendingReviewers();

  abstract ReviewerByEmailSet pendingReviewersByEmail();

  abstract ImmutableList<Account.Id> allPastReviewers();

  abstract ImmutableList<ReviewerStatusUpdate> reviewerUpdates();

  abstract ImmutableList<SubmitRecord> submitRecords();

  abstract ImmutableList<ChangeMessage> changeMessages();

  abstract ImmutableListMultimap<RevId, Comment> publishedComments();

  @Nullable
  abstract Timestamp readOnlyUntil();

  Change newChange(Project.NameKey project) {
    ChangeColumns c = checkNotNull(columns(), "columns are required");
    Change change =
        new Change(
            c.changeKey(),
            changeId(),
            c.owner(),
            new Branch.NameKey(project, c.branch()),
            c.createdOn());
    copyNonConstructorColumnsTo(change);
    change.setNoteDbState(NoteDbChangeState.NOTE_DB_PRIMARY_STATE);
    return change;
  }

  void copyColumnsTo(Change change) throws IOException {
    ChangeColumns c = columns();
    checkState(
        c != null && metaId() != null,
        "missing columns or metaId in ChangeNotesState; is NoteDb enabled? %s",
        this);
    checkMetaId(change);
    change.setKey(c.changeKey());
    change.setOwner(c.owner());
    change.setDest(new Branch.NameKey(change.getProject(), c.branch()));
    change.setCreatedOn(c.createdOn());
    copyNonConstructorColumnsTo(change);
  }

  private void checkMetaId(Change change) throws IOException {
    NoteDbChangeState state = NoteDbChangeState.parse(change);
    if (state == null) {
      return; // Can happen during small NoteDb tests.
    } else if (state.getPrimaryStorage() == PrimaryStorage.NOTE_DB) {
      return;
    }
    checkState(state.getRefState().isPresent(), "expected RefState: %s", state);
    ObjectId idFromState = state.getRefState().get().changeMetaId();
    if (!idFromState.equals(metaId())) {
      throw new IOException(
          "cannot copy ChangeNotesState into Change "
              + changeId()
              + "; this ChangeNotesState was created from "
              + metaId()
              + ", but change requires state "
              + idFromState);
    }
  }

  private void copyNonConstructorColumnsTo(Change change) {
    ChangeColumns c = checkNotNull(columns(), "columns are required");
    if (c.status() != null) {
      change.setStatus(c.status());
    }
    change.setTopic(Strings.emptyToNull(c.topic()));
    change.setLastUpdatedOn(c.lastUpdatedOn());
    change.setSubmissionId(c.submissionId());
    change.setAssignee(c.assignee());
    change.setPrivate(c.isPrivate());
    change.setWorkInProgress(c.workInProgress());
    change.setReviewStarted(c.reviewStarted());
    change.setRevertOf(c.revertOf());

    if (!patchSets().isEmpty()) {
      change.setCurrentPatchSet(c.currentPatchSetId(), c.subject(), c.originalSubject());
    } else {
      // TODO(dborowitz): This should be an error, but for now it's required for
      // some tests to pass.
      change.clearCurrentPatchSet();
    }
  }

  @AutoValue.Builder
  abstract static class Builder {
    static Builder empty(Change.Id changeId) {
      return new AutoValue_ChangeNotesState.Builder()
          .changeId(changeId)
          .pastAssignees(ImmutableSet.of())
          .hashtags(ImmutableSet.of())
          .patchSets(ImmutableList.of())
          .approvals(ImmutableList.of())
          .reviewers(ReviewerSet.empty())
          .reviewersByEmail(ReviewerByEmailSet.empty())
          .pendingReviewers(ReviewerSet.empty())
          .pendingReviewersByEmail(ReviewerByEmailSet.empty())
          .allPastReviewers(ImmutableList.of())
          .reviewerUpdates(ImmutableList.of())
          .submitRecords(ImmutableList.of())
          .changeMessages(ImmutableList.of())
          .publishedComments(ImmutableListMultimap.of());
    }

    abstract Builder metaId(ObjectId metaId);

    abstract Builder changeId(Change.Id changeId);

    abstract Builder columns(ChangeColumns columns);

    abstract Builder pastAssignees(Set<Account.Id> pastAssignees);

    abstract Builder hashtags(Set<String> hashtags);

    abstract Builder patchSets(Iterable<Map.Entry<PatchSet.Id, PatchSet>> patchSets);

    abstract Builder approvals(Iterable<Map.Entry<PatchSet.Id, PatchSetApproval>> approvals);

    abstract Builder reviewers(ReviewerSet reviewers);

    abstract Builder reviewersByEmail(ReviewerByEmailSet reviewersByEmail);

    abstract Builder pendingReviewers(ReviewerSet pendingReviewers);

    abstract Builder pendingReviewersByEmail(ReviewerByEmailSet pendingReviewersByEmail);

    abstract Builder allPastReviewers(List<Account.Id> allPastReviewers);

    abstract Builder reviewerUpdates(List<ReviewerStatusUpdate> reviewerUpdates);

    abstract Builder submitRecords(List<SubmitRecord> submitRecords);

    abstract Builder changeMessages(List<ChangeMessage> changeMessages);

    abstract Builder publishedComments(ListMultimap<RevId, Comment> publishedComments);

    abstract Builder readOnlyUntil(@Nullable Timestamp readOnlyUntil);

    abstract ChangeNotesState build();
  }
}
