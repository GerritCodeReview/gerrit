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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableListMultimap.toImmutableListMultimap;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.reviewdb.server.ReviewDbCodecs.APPROVAL_CODEC;
import static com.google.gerrit.reviewdb.server.ReviewDbCodecs.MESSAGE_CODEC;
import static com.google.gerrit.reviewdb.server.ReviewDbCodecs.PATCH_SET_CODEC;
import static com.google.gerrit.server.cache.serialize.ProtoCacheSerializers.toByteString;
import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.OutputFormat;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.ChangeColumnsProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.ReviewerByEmailSetEntryProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.ReviewerSetEntryProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.ReviewerStatusUpdateProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ProtoCacheSerializers;
import com.google.gerrit.server.cache.serialize.ProtoCacheSerializers.ObjectIdConverter;
import com.google.gerrit.server.index.change.ChangeField.StoredSubmitRecord;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gson.Gson;
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
    requireNonNull(
        metaId,
        () ->
            String.format(
                "metaId is required when passing arguments to create(...)."
                    + " To create an empty %s without"
                    + " NoteDb data, use empty(...) instead",
                ChangeNotesState.class.getSimpleName()));
    return builder()
        .metaId(metaId)
        .changeId(changeId)
        .columns(
            ChangeColumns.builder()
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
    static Builder builder() {
      return new AutoValue_ChangeNotesState_ChangeColumns.Builder();
    }

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

    abstract Builder toBuilder();

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
    ChangeColumns c = requireNonNull(columns(), "columns are required");
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
    ChangeColumns c = requireNonNull(columns(), "columns are required");
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

    abstract Builder hashtags(Iterable<String> hashtags);

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

  enum Serializer implements CacheSerializer<ChangeNotesState> {
    INSTANCE;

    @VisibleForTesting static final Gson GSON = OutputFormat.JSON_COMPACT.newGson();

    private static final Converter<String, Change.Status> STATUS_CONVERTER =
        Enums.stringConverter(Change.Status.class);
    private static final Converter<String, ReviewerStateInternal> REVIEWER_STATE_CONVERTER =
        Enums.stringConverter(ReviewerStateInternal.class);

    @Override
    public byte[] serialize(ChangeNotesState object) {
      checkArgument(object.metaId() != null, "meta ID is required in: %s", object);
      checkArgument(object.columns() != null, "ChangeColumns is required in: %s", object);
      ChangeNotesStateProto.Builder b = ChangeNotesStateProto.newBuilder();

      b.setMetaId(ObjectIdConverter.create().toByteString(object.metaId()))
          .setChangeId(object.changeId().get())
          .setColumns(toChangeColumnsProto(object.columns()));

      object.pastAssignees().forEach(a -> b.addPastAssignee(a.get()));
      object.hashtags().forEach(b::addHashtag);
      object.patchSets().forEach(e -> b.addPatchSet(toByteString(e.getValue(), PATCH_SET_CODEC)));
      object.approvals().forEach(e -> b.addApproval(toByteString(e.getValue(), APPROVAL_CODEC)));

      object.reviewers().asTable().cellSet().forEach(c -> b.addReviewer(toReviewerSetEntry(c)));
      object
          .reviewersByEmail()
          .asTable()
          .cellSet()
          .forEach(c -> b.addReviewerByEmail(toReviewerByEmailSetEntry(c)));
      object
          .pendingReviewers()
          .asTable()
          .cellSet()
          .forEach(c -> b.addPendingReviewer(toReviewerSetEntry(c)));
      object
          .pendingReviewersByEmail()
          .asTable()
          .cellSet()
          .forEach(c -> b.addPendingReviewerByEmail(toReviewerByEmailSetEntry(c)));

      object.allPastReviewers().forEach(a -> b.addPastReviewer(a.get()));
      object.reviewerUpdates().forEach(u -> b.addReviewerUpdate(toReviewerStatusUpdateProto(u)));
      object
          .submitRecords()
          .forEach(r -> b.addSubmitRecord(GSON.toJson(new StoredSubmitRecord(r))));
      object.changeMessages().forEach(m -> b.addChangeMessage(toByteString(m, MESSAGE_CODEC)));
      object.publishedComments().values().forEach(c -> b.addPublishedComment(GSON.toJson(c)));

      if (object.readOnlyUntil() != null) {
        b.setReadOnlyUntil(object.readOnlyUntil().getTime()).setHasReadOnlyUntil(true);
      }

      return ProtoCacheSerializers.toByteArray(b.build());
    }

    private static ChangeColumnsProto toChangeColumnsProto(ChangeColumns cols) {
      ChangeColumnsProto.Builder b =
          ChangeColumnsProto.newBuilder()
              .setChangeKey(cols.changeKey().get())
              .setCreatedOn(cols.createdOn().getTime())
              .setLastUpdatedOn(cols.lastUpdatedOn().getTime())
              .setOwner(cols.owner().get())
              .setBranch(cols.branch());
      if (cols.currentPatchSetId() != null) {
        b.setCurrentPatchSetId(cols.currentPatchSetId().get()).setHasCurrentPatchSetId(true);
      }
      b.setSubject(cols.subject());
      if (cols.topic() != null) {
        b.setTopic(cols.topic()).setHasTopic(true);
      }
      if (cols.originalSubject() != null) {
        b.setOriginalSubject(cols.originalSubject()).setHasOriginalSubject(true);
      }
      if (cols.submissionId() != null) {
        b.setSubmissionId(cols.submissionId()).setHasSubmissionId(true);
      }
      if (cols.assignee() != null) {
        b.setAssignee(cols.assignee().get()).setHasAssignee(true);
      }
      if (cols.status() != null) {
        b.setStatus(STATUS_CONVERTER.reverse().convert(cols.status())).setHasStatus(true);
      }
      b.setIsPrivate(cols.isPrivate())
          .setWorkInProgress(cols.workInProgress())
          .setReviewStarted(cols.reviewStarted());
      if (cols.revertOf() != null) {
        b.setRevertOf(cols.revertOf().get()).setHasRevertOf(true);
      }
      return b.build();
    }

    private static ReviewerSetEntryProto toReviewerSetEntry(
        Table.Cell<ReviewerStateInternal, Account.Id, Timestamp> c) {
      return ReviewerSetEntryProto.newBuilder()
          .setState(REVIEWER_STATE_CONVERTER.reverse().convert(c.getRowKey()))
          .setAccountId(c.getColumnKey().get())
          .setTimestamp(c.getValue().getTime())
          .build();
    }

    private static ReviewerByEmailSetEntryProto toReviewerByEmailSetEntry(
        Table.Cell<ReviewerStateInternal, Address, Timestamp> c) {
      return ReviewerByEmailSetEntryProto.newBuilder()
          .setState(REVIEWER_STATE_CONVERTER.reverse().convert(c.getRowKey()))
          .setAddress(c.getColumnKey().toHeaderString())
          .setTimestamp(c.getValue().getTime())
          .build();
    }

    private static ReviewerStatusUpdateProto toReviewerStatusUpdateProto(ReviewerStatusUpdate u) {
      return ReviewerStatusUpdateProto.newBuilder()
          .setDate(u.date().getTime())
          .setUpdatedBy(u.updatedBy().get())
          .setReviewer(u.reviewer().get())
          .setState(REVIEWER_STATE_CONVERTER.reverse().convert(u.state()))
          .build();
    }

    @Override
    public ChangeNotesState deserialize(byte[] in) {
      ChangeNotesStateProto proto =
          ProtoCacheSerializers.parseUnchecked(ChangeNotesStateProto.parser(), in);
      Change.Id changeId = new Change.Id(proto.getChangeId());

      ChangeNotesState.Builder b =
          builder()
              .metaId(ObjectIdConverter.create().fromByteString(proto.getMetaId()))
              .changeId(changeId)
              .columns(toChangeColumns(changeId, proto.getColumns()))
              .pastAssignees(
                  proto
                      .getPastAssigneeList()
                      .stream()
                      .map(Account.Id::new)
                      .collect(toImmutableSet()))
              .hashtags(proto.getHashtagList())
              .patchSets(
                  proto
                      .getPatchSetList()
                      .stream()
                      .map(PATCH_SET_CODEC::decode)
                      .map(ps -> Maps.immutableEntry(ps.getId(), ps))
                      .collect(toImmutableList()))
              .approvals(
                  proto
                      .getApprovalList()
                      .stream()
                      .map(APPROVAL_CODEC::decode)
                      .map(a -> Maps.immutableEntry(a.getPatchSetId(), a))
                      .collect(toImmutableList()))
              .reviewers(toReviewerSet(proto.getReviewerList()))
              .reviewersByEmail(toReviewerByEmailSet(proto.getReviewerByEmailList()))
              .pendingReviewers(toReviewerSet(proto.getPendingReviewerList()))
              .pendingReviewersByEmail(toReviewerByEmailSet(proto.getPendingReviewerByEmailList()))
              .allPastReviewers(
                  proto
                      .getPastReviewerList()
                      .stream()
                      .map(Account.Id::new)
                      .collect(toImmutableList()))
              .reviewerUpdates(toReviewerStatusUpdateList(proto.getReviewerUpdateList()))
              .submitRecords(
                  proto
                      .getSubmitRecordList()
                      .stream()
                      .map(r -> GSON.fromJson(r, StoredSubmitRecord.class).toSubmitRecord())
                      .collect(toImmutableList()))
              .changeMessages(
                  proto
                      .getChangeMessageList()
                      .stream()
                      .map(MESSAGE_CODEC::decode)
                      .collect(toImmutableList()))
              .publishedComments(
                  proto
                      .getPublishedCommentList()
                      .stream()
                      .map(r -> GSON.fromJson(r, Comment.class))
                      .collect(toImmutableListMultimap(c -> new RevId(c.revId), c -> c)));
      if (proto.getHasReadOnlyUntil()) {
        b.readOnlyUntil(new Timestamp(proto.getReadOnlyUntil()));
      }
      return b.build();
    }

    private static ChangeColumns toChangeColumns(Change.Id changeId, ChangeColumnsProto proto) {
      ChangeColumns.Builder b =
          ChangeColumns.builder()
              .changeKey(new Change.Key(proto.getChangeKey()))
              .createdOn(new Timestamp(proto.getCreatedOn()))
              .lastUpdatedOn(new Timestamp(proto.getLastUpdatedOn()))
              .owner(new Account.Id(proto.getOwner()))
              .branch(proto.getBranch());
      if (proto.getHasCurrentPatchSetId()) {
        b.currentPatchSetId(new PatchSet.Id(changeId, proto.getCurrentPatchSetId()));
      }
      b.subject(proto.getSubject());
      if (proto.getHasTopic()) {
        b.topic(proto.getTopic());
      }
      if (proto.getHasOriginalSubject()) {
        b.originalSubject(proto.getOriginalSubject());
      }
      if (proto.getHasSubmissionId()) {
        b.submissionId(proto.getSubmissionId());
      }
      if (proto.getHasAssignee()) {
        b.assignee(new Account.Id(proto.getAssignee()));
      }
      if (proto.getHasStatus()) {
        b.status(STATUS_CONVERTER.convert(proto.getStatus()));
      }
      b.isPrivate(proto.getIsPrivate())
          .workInProgress(proto.getWorkInProgress())
          .reviewStarted(proto.getReviewStarted());
      if (proto.getHasRevertOf()) {
        b.revertOf(new Change.Id(proto.getRevertOf()));
      }
      return b.build();
    }

    private static ReviewerSet toReviewerSet(List<ReviewerSetEntryProto> protos) {
      ImmutableTable.Builder<ReviewerStateInternal, Account.Id, Timestamp> b =
          ImmutableTable.builder();
      for (ReviewerSetEntryProto e : protos) {
        b.put(
            REVIEWER_STATE_CONVERTER.convert(e.getState()),
            new Account.Id(e.getAccountId()),
            new Timestamp(e.getTimestamp()));
      }
      return ReviewerSet.fromTable(b.build());
    }

    private static ReviewerByEmailSet toReviewerByEmailSet(
        List<ReviewerByEmailSetEntryProto> protos) {
      ImmutableTable.Builder<ReviewerStateInternal, Address, Timestamp> b =
          ImmutableTable.builder();
      for (ReviewerByEmailSetEntryProto e : protos) {
        b.put(
            REVIEWER_STATE_CONVERTER.convert(e.getState()),
            Address.parse(e.getAddress()),
            new Timestamp(e.getTimestamp()));
      }
      return ReviewerByEmailSet.fromTable(b.build());
    }

    private static ImmutableList<ReviewerStatusUpdate> toReviewerStatusUpdateList(
        List<ReviewerStatusUpdateProto> protos) {
      ImmutableList.Builder<ReviewerStatusUpdate> b = ImmutableList.builder();
      for (ReviewerStatusUpdateProto proto : protos) {
        b.add(
            ReviewerStatusUpdate.create(
                new Timestamp(proto.getDate()),
                new Account.Id(proto.getUpdatedBy()),
                new Account.Id(proto.getReviewer()),
                REVIEWER_STATE_CONVERTER.convert(proto.getState())));
      }
      return b.build();
    }
  }
}
