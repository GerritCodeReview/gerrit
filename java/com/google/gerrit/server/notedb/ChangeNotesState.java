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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.converter.ChangeMessageProtoConverter;
import com.google.gerrit.entities.converter.PatchSetApprovalProtoConverter;
import com.google.gerrit.entities.converter.PatchSetProtoConverter;
import com.google.gerrit.entities.converter.ProtoConverter;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.AssigneeStatusUpdate;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.AssigneeStatusUpdateProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.AttentionSetUpdateProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.ChangeColumnsProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.ReviewerByEmailSetEntryProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.ReviewerSetEntryProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.ReviewerStatusUpdateProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.index.change.ChangeField.StoredSubmitRecord;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.protobuf.MessageLite;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

  private static Builder builder() {
    return new AutoValue_ChangeNotesState.Builder();
  }

  static ChangeNotesState create(
      ObjectId metaId,
      Change.Id changeId,
      Change.Key changeKey,
      Timestamp createdOn,
      Timestamp lastUpdatedOn,
      Account.Id owner,
      String serverId,
      String branch,
      @Nullable PatchSet.Id currentPatchSetId,
      String subject,
      @Nullable String topic,
      @Nullable String originalSubject,
      @Nullable String submissionId,
      @Nullable Change.Status status,
      Set<String> hashtags,
      Map<PatchSet.Id, PatchSet> patchSets,
      ListMultimap<PatchSet.Id, PatchSetApproval> approvals,
      ReviewerSet reviewers,
      ReviewerByEmailSet reviewersByEmail,
      ReviewerSet pendingReviewers,
      ReviewerByEmailSet pendingReviewersByEmail,
      List<Account.Id> allPastReviewers,
      List<ReviewerStatusUpdate> reviewerUpdates,
      Set<AttentionSetUpdate> attentionSetUpdates,
      List<AssigneeStatusUpdate> assigneeUpdates,
      List<SubmitRecord> submitRecords,
      List<ChangeMessage> changeMessages,
      ListMultimap<ObjectId, HumanComment> publishedComments,
      boolean isPrivate,
      boolean workInProgress,
      boolean reviewStarted,
      @Nullable Change.Id revertOf,
      @Nullable PatchSet.Id cherryPickOf,
      int updateCount) {
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
                .isPrivate(isPrivate)
                .workInProgress(workInProgress)
                .reviewStarted(reviewStarted)
                .revertOf(revertOf)
                .cherryPickOf(cherryPickOf)
                .build())
        .hashtags(hashtags)
        .serverId(serverId)
        .patchSets(patchSets.entrySet())
        .approvals(approvals.entries())
        .reviewers(reviewers)
        .reviewersByEmail(reviewersByEmail)
        .pendingReviewers(pendingReviewers)
        .pendingReviewersByEmail(pendingReviewersByEmail)
        .allPastReviewers(allPastReviewers)
        .reviewerUpdates(reviewerUpdates)
        .attentionSet(attentionSetUpdates)
        .assigneeUpdates(assigneeUpdates)
        .submitRecords(submitRecords)
        .changeMessages(changeMessages)
        .publishedComments(publishedComments)
        .updateCount(updateCount)
        .build();
  }

  /**
   * Subset of Change columns that can be represented in NoteDb.
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

    abstract boolean isPrivate();

    abstract boolean workInProgress();

    abstract boolean reviewStarted();

    @Nullable
    abstract Change.Id revertOf();

    @Nullable
    abstract PatchSet.Id cherryPickOf();

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

      abstract Builder status(@Nullable Change.Status status);

      abstract Builder isPrivate(boolean isPrivate);

      abstract Builder workInProgress(boolean workInProgress);

      abstract Builder reviewStarted(boolean reviewStarted);

      abstract Builder revertOf(@Nullable Change.Id revertOf);

      abstract Builder cherryPickOf(@Nullable PatchSet.Id cherryPickOf);

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
  abstract ImmutableSet<String> hashtags();

  @Nullable
  abstract String serverId();

  abstract ImmutableList<Map.Entry<PatchSet.Id, PatchSet>> patchSets();

  abstract ImmutableList<Map.Entry<PatchSet.Id, PatchSetApproval>> approvals();

  abstract ReviewerSet reviewers();

  abstract ReviewerByEmailSet reviewersByEmail();

  abstract ReviewerSet pendingReviewers();

  abstract ReviewerByEmailSet pendingReviewersByEmail();

  abstract ImmutableList<Account.Id> allPastReviewers();

  abstract ImmutableList<ReviewerStatusUpdate> reviewerUpdates();

  /** Returns the most recent update (i.e. current status status) per user. */
  abstract ImmutableSet<AttentionSetUpdate> attentionSet();

  abstract ImmutableList<AssigneeStatusUpdate> assigneeUpdates();

  abstract ImmutableList<SubmitRecord> submitRecords();

  abstract ImmutableList<ChangeMessage> changeMessages();

  abstract ImmutableListMultimap<ObjectId, HumanComment> publishedComments();

  abstract int updateCount();

  Change newChange(Project.NameKey project) {
    ChangeColumns c = requireNonNull(columns(), "columns are required");
    Change change =
        new Change(
            c.changeKey(),
            changeId(),
            c.owner(),
            BranchNameKey.create(project, c.branch()),
            c.createdOn());
    copyNonConstructorColumnsTo(change);
    return change;
  }

  void copyColumnsTo(Change change) {
    ChangeColumns c = columns();
    checkState(
        c != null && metaId() != null,
        "missing columns or metaId in ChangeNotesState; is NoteDb enabled? %s",
        this);
    change.setKey(c.changeKey());
    change.setOwner(c.owner());
    change.setDest(BranchNameKey.create(change.getProject(), c.branch()));
    change.setCreatedOn(c.createdOn());
    copyNonConstructorColumnsTo(change);
  }

  private void copyNonConstructorColumnsTo(Change change) {
    ChangeColumns c = requireNonNull(columns(), "columns are required");
    if (c.status() != null) {
      change.setStatus(c.status());
    }
    change.setTopic(Strings.emptyToNull(c.topic()));
    change.setLastUpdatedOn(c.lastUpdatedOn());
    change.setSubmissionId(c.submissionId());
    if (!assigneeUpdates().isEmpty()) {
      change.setAssignee(assigneeUpdates().get(0).currentAssignee().orElse(null));
    }
    change.setPrivate(c.isPrivate());
    change.setWorkInProgress(c.workInProgress());
    change.setReviewStarted(c.reviewStarted());
    change.setRevertOf(c.revertOf());
    change.setCherryPickOf(c.cherryPickOf());

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
          .hashtags(ImmutableSet.of())
          .patchSets(ImmutableList.of())
          .approvals(ImmutableList.of())
          .reviewers(ReviewerSet.empty())
          .reviewersByEmail(ReviewerByEmailSet.empty())
          .pendingReviewers(ReviewerSet.empty())
          .pendingReviewersByEmail(ReviewerByEmailSet.empty())
          .allPastReviewers(ImmutableList.of())
          .reviewerUpdates(ImmutableList.of())
          .attentionSet(ImmutableSet.of())
          .assigneeUpdates(ImmutableList.of())
          .submitRecords(ImmutableList.of())
          .changeMessages(ImmutableList.of())
          .publishedComments(ImmutableListMultimap.of())
          .updateCount(0);
    }

    abstract Builder metaId(ObjectId metaId);

    abstract Builder changeId(Change.Id changeId);

    abstract Builder columns(ChangeColumns columns);

    abstract Builder serverId(String serverId);

    abstract Builder hashtags(Iterable<String> hashtags);

    abstract Builder patchSets(Iterable<Map.Entry<PatchSet.Id, PatchSet>> patchSets);

    abstract Builder approvals(Iterable<Map.Entry<PatchSet.Id, PatchSetApproval>> approvals);

    abstract Builder reviewers(ReviewerSet reviewers);

    abstract Builder reviewersByEmail(ReviewerByEmailSet reviewersByEmail);

    abstract Builder pendingReviewers(ReviewerSet pendingReviewers);

    abstract Builder pendingReviewersByEmail(ReviewerByEmailSet pendingReviewersByEmail);

    abstract Builder allPastReviewers(List<Account.Id> allPastReviewers);

    abstract Builder reviewerUpdates(List<ReviewerStatusUpdate> reviewerUpdates);

    abstract Builder attentionSet(Set<AttentionSetUpdate> attentionSetUpdates);

    abstract Builder assigneeUpdates(List<AssigneeStatusUpdate> assigneeUpdates);

    abstract Builder submitRecords(List<SubmitRecord> submitRecords);

    abstract Builder changeMessages(List<ChangeMessage> changeMessages);

    abstract Builder publishedComments(ListMultimap<ObjectId, HumanComment> publishedComments);

    abstract Builder updateCount(int updateCount);

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

      if (object.serverId() != null) {
        b.setServerId(object.serverId());
        b.setHasServerId(true);
      }
      object.hashtags().forEach(b::addHashtag);
      object
          .patchSets()
          .forEach(e -> b.addPatchSet(toByteString(e.getValue(), PatchSetProtoConverter.INSTANCE)));
      object
          .approvals()
          .forEach(
              e ->
                  b.addApproval(
                      toByteString(e.getValue(), PatchSetApprovalProtoConverter.INSTANCE)));

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
      object.attentionSet().forEach(u -> b.addAttentionSetUpdate(toAttentionSetUpdateProto(u)));
      object.assigneeUpdates().forEach(u -> b.addAssigneeUpdate(toAssigneeStatusUpdateProto(u)));
      object
          .submitRecords()
          .forEach(r -> b.addSubmitRecord(GSON.toJson(new StoredSubmitRecord(r))));
      object
          .changeMessages()
          .forEach(m -> b.addChangeMessage(toByteString(m, ChangeMessageProtoConverter.INSTANCE)));
      object.publishedComments().values().forEach(c -> b.addPublishedComment(GSON.toJson(c)));
      b.setUpdateCount(object.updateCount());

      return Protos.toByteArray(b.build());
    }

    @VisibleForTesting
    static <T> ByteString toByteString(T object, ProtoConverter<?, T> converter) {
      MessageLite message = converter.toProto(object);
      return Protos.toByteString(message);
    }

    private static ChangeColumnsProto toChangeColumnsProto(ChangeColumns cols) {
      ChangeColumnsProto.Builder b =
          ChangeColumnsProto.newBuilder()
              .setChangeKey(cols.changeKey().get())
              .setCreatedOnMillis(cols.createdOn().getTime())
              .setLastUpdatedOnMillis(cols.lastUpdatedOn().getTime())
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
      if (cols.status() != null) {
        b.setStatus(STATUS_CONVERTER.reverse().convert(cols.status())).setHasStatus(true);
      }
      b.setIsPrivate(cols.isPrivate())
          .setWorkInProgress(cols.workInProgress())
          .setReviewStarted(cols.reviewStarted());
      if (cols.revertOf() != null) {
        b.setRevertOf(cols.revertOf().get()).setHasRevertOf(true);
      }
      if (cols.cherryPickOf() != null) {
        b.setCherryPickOf(cols.cherryPickOf().getCommaSeparatedChangeAndPatchSetId())
            .setHasCherryPickOf(true);
      }
      return b.build();
    }

    private static ReviewerSetEntryProto toReviewerSetEntry(
        Table.Cell<ReviewerStateInternal, Account.Id, Timestamp> c) {
      return ReviewerSetEntryProto.newBuilder()
          .setState(REVIEWER_STATE_CONVERTER.reverse().convert(c.getRowKey()))
          .setAccountId(c.getColumnKey().get())
          .setTimestampMillis(c.getValue().getTime())
          .build();
    }

    private static ReviewerByEmailSetEntryProto toReviewerByEmailSetEntry(
        Table.Cell<ReviewerStateInternal, Address, Timestamp> c) {
      return ReviewerByEmailSetEntryProto.newBuilder()
          .setState(REVIEWER_STATE_CONVERTER.reverse().convert(c.getRowKey()))
          .setAddress(c.getColumnKey().toHeaderString())
          .setTimestampMillis(c.getValue().getTime())
          .build();
    }

    private static ReviewerStatusUpdateProto toReviewerStatusUpdateProto(ReviewerStatusUpdate u) {
      return ReviewerStatusUpdateProto.newBuilder()
          .setTimestampMillis(u.date().getTime())
          .setUpdatedBy(u.updatedBy().get())
          .setReviewer(u.reviewer().get())
          .setState(REVIEWER_STATE_CONVERTER.reverse().convert(u.state()))
          .build();
    }

    private static AttentionSetUpdateProto toAttentionSetUpdateProto(
        AttentionSetUpdate attentionSetUpdate) {
      return AttentionSetUpdateProto.newBuilder()
          .setTimestampMillis(attentionSetUpdate.timestamp().toEpochMilli())
          .setAccount(attentionSetUpdate.account().get())
          .setOperation(attentionSetUpdate.operation().name())
          .setReason(attentionSetUpdate.reason())
          .build();
    }

    private static AssigneeStatusUpdateProto toAssigneeStatusUpdateProto(AssigneeStatusUpdate u) {
      AssigneeStatusUpdateProto.Builder builder =
          AssigneeStatusUpdateProto.newBuilder()
              .setTimestampMillis(u.date().getTime())
              .setUpdatedBy(u.updatedBy().get())
              .setHasCurrentAssignee(u.currentAssignee().isPresent());

      u.currentAssignee().ifPresent(assignee -> builder.setCurrentAssignee(assignee.get()));
      return builder.build();
    }

    @Override
    public ChangeNotesState deserialize(byte[] in) {
      ChangeNotesStateProto proto = Protos.parseUnchecked(ChangeNotesStateProto.parser(), in);
      Change.Id changeId = Change.id(proto.getChangeId());

      ChangeNotesState.Builder b =
          builder()
              .metaId(ObjectIdConverter.create().fromByteString(proto.getMetaId()))
              .changeId(changeId)
              .columns(toChangeColumns(changeId, proto.getColumns()))
              .serverId(proto.getHasServerId() ? proto.getServerId() : null)
              .hashtags(proto.getHashtagList())
              .patchSets(
                  proto.getPatchSetList().stream()
                      .map(bytes -> parseProtoFrom(PatchSetProtoConverter.INSTANCE, bytes))
                      .map(ps -> Maps.immutableEntry(ps.id(), ps))
                      .collect(toImmutableList()))
              .approvals(
                  proto.getApprovalList().stream()
                      .map(bytes -> parseProtoFrom(PatchSetApprovalProtoConverter.INSTANCE, bytes))
                      .map(a -> Maps.immutableEntry(a.patchSetId(), a))
                      .collect(toImmutableList()))
              .reviewers(toReviewerSet(proto.getReviewerList()))
              .reviewersByEmail(toReviewerByEmailSet(proto.getReviewerByEmailList()))
              .pendingReviewers(toReviewerSet(proto.getPendingReviewerList()))
              .pendingReviewersByEmail(toReviewerByEmailSet(proto.getPendingReviewerByEmailList()))
              .allPastReviewers(
                  proto.getPastReviewerList().stream().map(Account::id).collect(toImmutableList()))
              .reviewerUpdates(toReviewerStatusUpdateList(proto.getReviewerUpdateList()))
              .attentionSet(toAttentionSetUpdates(proto.getAttentionSetUpdateList()))
              .assigneeUpdates(toAssigneeStatusUpdateList(proto.getAssigneeUpdateList()))
              .submitRecords(
                  proto.getSubmitRecordList().stream()
                      .map(r -> GSON.fromJson(r, StoredSubmitRecord.class).toSubmitRecord())
                      .collect(toImmutableList()))
              .changeMessages(
                  proto.getChangeMessageList().stream()
                      .map(bytes -> parseProtoFrom(ChangeMessageProtoConverter.INSTANCE, bytes))
                      .collect(toImmutableList()))
              .publishedComments(
                  proto.getPublishedCommentList().stream()
                      .map(r -> GSON.fromJson(r, HumanComment.class))
                      .collect(toImmutableListMultimap(HumanComment::getCommitId, c -> c)))
              .updateCount(proto.getUpdateCount());
      return b.build();
    }

    private static <P extends MessageLite, T> T parseProtoFrom(
        ProtoConverter<P, T> converter, ByteString byteString) {
      P message = Protos.parseUnchecked(converter.getParser(), byteString);
      return converter.fromProto(message);
    }

    private static ChangeColumns toChangeColumns(Change.Id changeId, ChangeColumnsProto proto) {
      ChangeColumns.Builder b =
          ChangeColumns.builder()
              .changeKey(Change.key(proto.getChangeKey()))
              .createdOn(new Timestamp(proto.getCreatedOnMillis()))
              .lastUpdatedOn(new Timestamp(proto.getLastUpdatedOnMillis()))
              .owner(Account.id(proto.getOwner()))
              .branch(proto.getBranch());
      if (proto.getHasCurrentPatchSetId()) {
        b.currentPatchSetId(PatchSet.id(changeId, proto.getCurrentPatchSetId()));
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
      if (proto.getHasStatus()) {
        b.status(STATUS_CONVERTER.convert(proto.getStatus()));
      }
      b.isPrivate(proto.getIsPrivate())
          .workInProgress(proto.getWorkInProgress())
          .reviewStarted(proto.getReviewStarted());
      if (proto.getHasRevertOf()) {
        b.revertOf(Change.id(proto.getRevertOf()));
      }
      if (proto.getHasCherryPickOf()) {
        b.cherryPickOf(PatchSet.Id.parse(proto.getCherryPickOf()));
      }
      return b.build();
    }

    private static ReviewerSet toReviewerSet(List<ReviewerSetEntryProto> protos) {
      ImmutableTable.Builder<ReviewerStateInternal, Account.Id, Timestamp> b =
          ImmutableTable.builder();
      for (ReviewerSetEntryProto e : protos) {
        b.put(
            REVIEWER_STATE_CONVERTER.convert(e.getState()),
            Account.id(e.getAccountId()),
            new Timestamp(e.getTimestampMillis()));
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
            new Timestamp(e.getTimestampMillis()));
      }
      return ReviewerByEmailSet.fromTable(b.build());
    }

    private static ImmutableList<ReviewerStatusUpdate> toReviewerStatusUpdateList(
        List<ReviewerStatusUpdateProto> protos) {
      ImmutableList.Builder<ReviewerStatusUpdate> b = ImmutableList.builder();
      for (ReviewerStatusUpdateProto proto : protos) {
        b.add(
            ReviewerStatusUpdate.create(
                new Timestamp(proto.getTimestampMillis()),
                Account.id(proto.getUpdatedBy()),
                Account.id(proto.getReviewer()),
                REVIEWER_STATE_CONVERTER.convert(proto.getState())));
      }
      return b.build();
    }

    private static ImmutableSet<AttentionSetUpdate> toAttentionSetUpdates(
        List<AttentionSetUpdateProto> protos) {
      ImmutableSet.Builder<AttentionSetUpdate> b = ImmutableSet.builder();
      for (AttentionSetUpdateProto proto : protos) {
        b.add(
            AttentionSetUpdate.createFromRead(
                Instant.ofEpochMilli(proto.getTimestampMillis()),
                Account.id(proto.getAccount()),
                AttentionSetUpdate.Operation.valueOf(proto.getOperation()),
                proto.getReason()));
      }
      return b.build();
    }

    private static ImmutableList<AssigneeStatusUpdate> toAssigneeStatusUpdateList(
        List<AssigneeStatusUpdateProto> protos) {
      ImmutableList.Builder<AssigneeStatusUpdate> b = ImmutableList.builder();
      for (AssigneeStatusUpdateProto proto : protos) {
        b.add(
            AssigneeStatusUpdate.create(
                new Timestamp(proto.getTimestampMillis()),
                Account.id(proto.getUpdatedBy()),
                proto.getHasCurrentAssignee()
                    ? Optional.of(Account.id(proto.getCurrentAssignee()))
                    : Optional.empty()));
      }
      return b.build();
    }
  }
}
