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

import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchLineComment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.ReviewerSet;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable state associated with a change meta ref at a given commit.
 * <p>
 * One instance is the output of a single {@link ChangeNotesParser}, and
 * contains types required to support public methods on {@link ChangeNotes}. It
 * is intended to be cached in-process.
 * <p>
 * Note that {@link ChangeNotes} contains more than just a single {@code
 * ChangeNoteState}, such as per-draft information, so that class is not cached
 * directly.
 */
@AutoValue
public abstract class ChangeNotesState {
  static ChangeNotesState empty(Change change) {
    return new AutoValue_ChangeNotesState(
        change.getId(),
        null,
        ImmutableSet.<String>of(),
        ImmutableSortedMap.<PatchSet.Id, PatchSet>of(),
        ImmutableListMultimap.<PatchSet.Id, PatchSetApproval>of(),
        ReviewerSet.empty(),
        ImmutableList.<Account.Id>of(),
        ImmutableList.<SubmitRecord>of(),
        ImmutableList.<ChangeMessage>of(),
        ImmutableListMultimap.<PatchSet.Id, ChangeMessage>of(),
        ImmutableListMultimap.<RevId, PatchLineComment>of());
  }

  static ChangeNotesState create(
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
      @Nullable Change.Status status,
      @Nullable Set<String> hashtags,
      Map<PatchSet.Id, PatchSet> patchSets,
      Multimap<PatchSet.Id, PatchSetApproval> approvals,
      ReviewerSet reviewers,
      List<Account.Id> allPastReviewers,
      List<SubmitRecord> submitRecords,
      List<ChangeMessage> allChangeMessages,
      Multimap<PatchSet.Id, ChangeMessage> changeMessagesByPatchSet,
      Multimap<RevId, PatchLineComment> publishedComments) {
    if (hashtags == null) {
      hashtags = ImmutableSet.of();
    }
    return new AutoValue_ChangeNotesState(
        changeId,
        new AutoValue_ChangeNotesState_ChangeColumns(
            changeKey,
            createdOn,
            lastUpdatedOn,
            owner,
            branch,
            currentPatchSetId,
            subject,
            topic,
            originalSubject,
            submissionId,
            status),
        ImmutableSet.copyOf(hashtags),
        ImmutableSortedMap.copyOf(patchSets, ReviewDbUtil.intKeyOrdering()),
        ImmutableListMultimap.copyOf(approvals),
        reviewers,
        ImmutableList.copyOf(allPastReviewers),
        ImmutableList.copyOf(submitRecords),
        ImmutableList.copyOf(allChangeMessages),
        ImmutableListMultimap.copyOf(changeMessagesByPatchSet),
        ImmutableListMultimap.copyOf(publishedComments));
  }


  /**
   * Subset of Change columns that can be represented in NoteDb.
   * <p>
   * Notable exceptions include rowVersion and noteDbState, which are only make
   * sense when read from NoteDb, so they cannot be cached.
   * <p>
   * Fields are in listed column order.
   */
  @AutoValue
  abstract static class ChangeColumns {
    abstract Change.Key changeKey();
    abstract Timestamp createdOn();
    abstract Timestamp lastUpdatedOn();
    abstract Account.Id owner();
    abstract String branch(); // Project not included.
    @Nullable abstract PatchSet.Id currentPatchSetId();
    abstract String subject();
    @Nullable abstract String topic();
    @Nullable abstract String originalSubject();
    @Nullable abstract String submissionId();
    // TODO(dborowitz): Use a sensible default other than null
    @Nullable abstract Change.Status status();
  }

  abstract Change.Id changeId();

  @Nullable abstract ChangeColumns columns();

  // Other related to this Change.
  abstract ImmutableSet<String> hashtags();
  abstract ImmutableSortedMap<PatchSet.Id, PatchSet> patchSets();
  abstract ImmutableListMultimap<PatchSet.Id, PatchSetApproval> approvals();
  abstract ReviewerSet reviewers();
  abstract ImmutableList<Account.Id> allPastReviewers();
  abstract ImmutableList<SubmitRecord> submitRecords();
  abstract ImmutableList<ChangeMessage> allChangeMessages();
  abstract ImmutableListMultimap<PatchSet.Id, ChangeMessage>
      changeMessagesByPatchSet();
  abstract ImmutableListMultimap<RevId, PatchLineComment> publishedComments();

  void copyColumnsTo(Change change) {
    ChangeColumns c = checkNotNull(columns());
    if (c.status() != null) {
      change.setStatus(c.status());
    }
    change.setKey(c.changeKey());
    change.setDest(new Branch.NameKey(change.getProject(), c.branch()));
    change.setTopic(Strings.emptyToNull(c.topic()));
    change.setCreatedOn(c.createdOn());
    change.setLastUpdatedOn(c.lastUpdatedOn());
    change.setOwner(c.owner());
    change.setSubmissionId(c.submissionId());

    if (!patchSets().isEmpty()) {
      change.setCurrentPatchSet(
          c.currentPatchSetId(), c.subject(), c.originalSubject());
    } else {
      // TODO(dborowitz): This should be an error, but for now it's required for
      // some tests to pass.
      change.clearCurrentPatchSet();
    }
  }
}
