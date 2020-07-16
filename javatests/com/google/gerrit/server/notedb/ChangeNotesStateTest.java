// Copyright (C) 2018 The Android Open Source Project
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
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;
import static com.google.gerrit.server.notedb.ChangeNotesState.Serializer.toByteString;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.converter.ChangeMessageProtoConverter;
import com.google.gerrit.entities.converter.PatchSetApprovalProtoConverter;
import com.google.gerrit.entities.converter.PatchSetProtoConverter;
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
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.notedb.ChangeNotesState.ChangeColumns;
import com.google.gerrit.server.notedb.ChangeNotesState.Serializer;
import com.google.inject.TypeLiteral;
import com.google.protobuf.ByteString;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

public class ChangeNotesStateTest {

  private static final Change.Id ID = Change.id(123);
  private static final ObjectId SHA =
      ObjectId.fromString("1234567812345678123456781234567812345678");
  private static final ByteString SHA_BYTES = ObjectIdConverter.create().toByteString(SHA);
  private static final String CHANGE_KEY = "Iabcdabcdabcdabcdabcdabcdabcdabcdabcdabcd";
  private static final String DEFAULT_SERVER_ID = UUID.randomUUID().toString();

  private ChangeColumns cols;
  private ChangeColumnsProto colsProto;

  @Before
  public void setUp() throws Exception {
    cols =
        ChangeColumns.builder()
            .changeKey(Change.key(CHANGE_KEY))
            .createdOn(new Timestamp(123456L))
            .lastUpdatedOn(new Timestamp(234567L))
            .owner(Account.id(1000))
            .branch("refs/heads/master")
            .subject("Test change")
            .isPrivate(false)
            .workInProgress(false)
            .reviewStarted(true)
            .build();
    colsProto = toProto(newBuilder().build()).getColumns();
  }

  private ChangeNotesState.Builder newBuilder() {
    return ChangeNotesState.Builder.empty(ID).metaId(SHA).columns(cols);
  }

  private ChangeNotesStateProto.Builder newProtoBuilder() {
    return ChangeNotesStateProto.newBuilder()
        .setMetaId(SHA_BYTES)
        .setChangeId(ID.get())
        .setColumns(colsProto);
  }

  @Test
  public void serializeChangeKey() throws Exception {
    assertRoundTrip(
        newBuilder()
            .columns(
                cols.toBuilder()
                    .changeKey(Change.key("Ieeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"))
                    .build())
            .build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(
                colsProto.toBuilder().setChangeKey("Ieeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"))
            .build());
  }

  @Test
  public void serializeCreatedOn() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().createdOn(new Timestamp(98765L)).build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setCreatedOnMillis(98765L))
            .build());
  }

  @Test
  public void serializeLastUpdatedOn() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().lastUpdatedOn(new Timestamp(98765L)).build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setLastUpdatedOnMillis(98765L))
            .build());
  }

  @Test
  public void serializeOwner() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().owner(Account.id(7777)).build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setOwner(7777))
            .build());
  }

  @Test
  public void serializeBranch() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().branch("refs/heads/bar").build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setBranch("refs/heads/bar"))
            .build());
  }

  @Test
  public void serializeSubject() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().subject("A different test change").build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setSubject("A different test change"))
            .build());
  }

  @Test
  public void serializeCurrentPatchSetId() throws Exception {
    assertRoundTrip(
        newBuilder()
            .columns(cols.toBuilder().currentPatchSetId(PatchSet.id(ID, 2)).build())
            .build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setCurrentPatchSetId(2).setHasCurrentPatchSetId(true))
            .build());
  }

  @Test
  public void serializeNullTopic() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().topic(null).build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .build());
  }

  @Test
  public void serializeEmptyTopic() throws Exception {
    ChangeNotesState state = newBuilder().columns(cols.toBuilder().topic("").build()).build();
    assertRoundTrip(
        state,
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setTopic("").setHasTopic(true))
            .build());
  }

  @Test
  public void serializeNonEmptyTopic() throws Exception {
    ChangeNotesState state = newBuilder().columns(cols.toBuilder().topic("topic").build()).build();
    assertRoundTrip(
        state,
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setTopic("topic").setHasTopic(true))
            .build());
  }

  @Test
  public void serializeOriginalSubject() throws Exception {
    assertRoundTrip(
        newBuilder()
            .columns(cols.toBuilder().originalSubject("The first patch set").build())
            .build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(
                colsProto
                    .toBuilder()
                    .setOriginalSubject("The first patch set")
                    .setHasOriginalSubject(true))
            .build());
  }

  @Test
  public void serializeSubmissionId() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().submissionId("xyz").build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setSubmissionId("xyz").setHasSubmissionId(true))
            .build());
  }

  @Test
  public void serializeStatus() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().status(Change.Status.MERGED).build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setStatus("MERGED").setHasStatus(true))
            .build());
  }

  @Test
  public void serializeIsPrivate() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().isPrivate(true).build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setIsPrivate(true))
            .build());
  }

  @Test
  public void serializeIsWorkInProgress() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().workInProgress(true).build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setWorkInProgress(true))
            .build());
  }

  @Test
  public void serializeHasReviewStarted() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().reviewStarted(true).build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setReviewStarted(true))
            .build());
  }

  @Test
  public void serializeRevertOf() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().revertOf(Change.id(999)).build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setRevertOf(999).setHasRevertOf(true))
            .build());
  }

  @Test
  public void serializeHashtags() throws Exception {
    assertRoundTrip(
        newBuilder().hashtags(ImmutableSet.of("tag2", "tag1")).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addHashtag("tag2")
            .addHashtag("tag1")
            .build());
  }

  @Test
  public void serializePatchSets() throws Exception {
    PatchSet ps1 =
        PatchSet.builder()
            .id(PatchSet.id(ID, 1))
            .commitId(ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .uploader(Account.id(2000))
            .createdOn(cols.createdOn())
            .build();
    ByteString ps1Bytes = toByteString(ps1, PatchSetProtoConverter.INSTANCE);
    assertThat(ps1Bytes.size()).isEqualTo(66);

    PatchSet ps2 =
        PatchSet.builder()
            .id(PatchSet.id(ID, 2))
            .commitId(ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
            .uploader(Account.id(3000))
            .createdOn(cols.lastUpdatedOn())
            .build();
    ByteString ps2Bytes = toByteString(ps2, PatchSetProtoConverter.INSTANCE);
    assertThat(ps2Bytes.size()).isEqualTo(66);
    assertThat(ps2Bytes).isNotEqualTo(ps1Bytes);

    assertRoundTrip(
        newBuilder().patchSets(ImmutableMap.of(ps2.id(), ps2, ps1.id(), ps1).entrySet()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addPatchSet(ps2Bytes)
            .addPatchSet(ps1Bytes)
            .build());
  }

  @Test
  public void serializeApprovals() throws Exception {
    PatchSetApproval a1 =
        PatchSetApproval.builder()
            .key(
                PatchSetApproval.key(
                    PatchSet.id(ID, 1), Account.id(2001), LabelId.create("Code-Review")))
            .value(1)
            .granted(new Timestamp(1212L))
            .build();
    ByteString a1Bytes = toByteString(a1, PatchSetApprovalProtoConverter.INSTANCE);
    assertThat(a1Bytes.size()).isEqualTo(43);

    PatchSetApproval a2 =
        PatchSetApproval.builder()
            .key(
                PatchSetApproval.key(
                    PatchSet.id(ID, 1), Account.id(2002), LabelId.create("Verified")))
            .value(-1)
            .granted(new Timestamp(3434L))
            .build();
    ByteString a2Bytes = toByteString(a2, PatchSetApprovalProtoConverter.INSTANCE);
    assertThat(a2Bytes.size()).isEqualTo(49);
    assertThat(a2Bytes).isNotEqualTo(a1Bytes);

    assertRoundTrip(
        newBuilder()
            .approvals(ImmutableListMultimap.of(a2.patchSetId(), a2, a1.patchSetId(), a1).entries())
            .build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addApproval(a2Bytes)
            .addApproval(a1Bytes)
            .build());
  }

  @Test
  public void serializeReviewers() throws Exception {
    assertRoundTrip(
        newBuilder()
            .reviewers(
                ReviewerSet.fromTable(
                    ImmutableTable.<ReviewerStateInternal, Account.Id, Timestamp>builder()
                        .put(ReviewerStateInternal.CC, Account.id(2001), new Timestamp(1212L))
                        .put(ReviewerStateInternal.REVIEWER, Account.id(2002), new Timestamp(3434L))
                        .build()))
            .build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addReviewer(
                ReviewerSetEntryProto.newBuilder()
                    .setState("CC")
                    .setAccountId(2001)
                    .setTimestampMillis(1212L))
            .addReviewer(
                ReviewerSetEntryProto.newBuilder()
                    .setState("REVIEWER")
                    .setAccountId(2002)
                    .setTimestampMillis(3434L))
            .build());
  }

  @Test
  public void serializeReviewersByEmail() throws Exception {
    assertRoundTrip(
        newBuilder()
            .reviewersByEmail(
                ReviewerByEmailSet.fromTable(
                    ImmutableTable.<ReviewerStateInternal, Address, Timestamp>builder()
                        .put(
                            ReviewerStateInternal.CC,
                            Address.create("Name1", "email1@example.com"),
                            new Timestamp(1212L))
                        .put(
                            ReviewerStateInternal.REVIEWER,
                            Address.create("Name2", "email2@example.com"),
                            new Timestamp(3434L))
                        .build()))
            .build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addReviewerByEmail(
                ReviewerByEmailSetEntryProto.newBuilder()
                    .setState("CC")
                    .setAddress("Name1 <email1@example.com>")
                    .setTimestampMillis(1212L))
            .addReviewerByEmail(
                ReviewerByEmailSetEntryProto.newBuilder()
                    .setState("REVIEWER")
                    .setAddress("Name2 <email2@example.com>")
                    .setTimestampMillis(3434L))
            .build());
  }

  @Test
  public void serializeReviewersByEmailWithNullName() throws Exception {
    ChangeNotesState actual =
        assertRoundTrip(
            newBuilder()
                .reviewersByEmail(
                    ReviewerByEmailSet.fromTable(
                        ImmutableTable.of(
                            ReviewerStateInternal.CC,
                            Address.create("emailonly@example.com"),
                            new Timestamp(1212L))))
                .build(),
            ChangeNotesStateProto.newBuilder()
                .setMetaId(SHA_BYTES)
                .setChangeId(ID.get())
                .setColumns(colsProto)
                .addReviewerByEmail(
                    ReviewerByEmailSetEntryProto.newBuilder()
                        .setState("CC")
                        .setAddress("emailonly@example.com")
                        .setTimestampMillis(1212L))
                .build());

    // Address doesn't consider the name field in equals, so we have to check it manually.
    // TODO(dborowitz): Fix Address#equals.
    ImmutableSet<Address> ccs = actual.reviewersByEmail().byState(ReviewerStateInternal.CC);
    assertThat(ccs).hasSize(1);
    Address address = Iterables.getOnlyElement(ccs);
    assertThat(address.name()).isNull();
    assertThat(address.email()).isEqualTo("emailonly@example.com");
  }

  @Test
  public void serializePendingReviewers() throws Exception {
    assertRoundTrip(
        newBuilder()
            .pendingReviewers(
                ReviewerSet.fromTable(
                    ImmutableTable.<ReviewerStateInternal, Account.Id, Timestamp>builder()
                        .put(ReviewerStateInternal.CC, Account.id(2001), new Timestamp(1212L))
                        .put(ReviewerStateInternal.REVIEWER, Account.id(2002), new Timestamp(3434L))
                        .build()))
            .build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addPendingReviewer(
                ReviewerSetEntryProto.newBuilder()
                    .setState("CC")
                    .setAccountId(2001)
                    .setTimestampMillis(1212L))
            .addPendingReviewer(
                ReviewerSetEntryProto.newBuilder()
                    .setState("REVIEWER")
                    .setAccountId(2002)
                    .setTimestampMillis(3434L))
            .build());
  }

  @Test
  public void serializePendingReviewersByEmail() throws Exception {
    assertRoundTrip(
        newBuilder()
            .pendingReviewersByEmail(
                ReviewerByEmailSet.fromTable(
                    ImmutableTable.<ReviewerStateInternal, Address, Timestamp>builder()
                        .put(
                            ReviewerStateInternal.CC,
                            Address.create("Name1", "email1@example.com"),
                            new Timestamp(1212L))
                        .put(
                            ReviewerStateInternal.REVIEWER,
                            Address.create("Name2", "email2@example.com"),
                            new Timestamp(3434L))
                        .build()))
            .build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addPendingReviewerByEmail(
                ReviewerByEmailSetEntryProto.newBuilder()
                    .setState("CC")
                    .setAddress("Name1 <email1@example.com>")
                    .setTimestampMillis(1212L))
            .addPendingReviewerByEmail(
                ReviewerByEmailSetEntryProto.newBuilder()
                    .setState("REVIEWER")
                    .setAddress("Name2 <email2@example.com>")
                    .setTimestampMillis(3434L))
            .build());
  }

  @Test
  public void serializeAllPastReviewers() throws Exception {
    assertRoundTrip(
        newBuilder().allPastReviewers(ImmutableList.of(Account.id(2002), Account.id(2001))).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addPastReviewer(2002)
            .addPastReviewer(2001)
            .build());
  }

  @Test
  public void serializeReviewerUpdates() throws Exception {
    assertRoundTrip(
        newBuilder()
            .reviewerUpdates(
                ImmutableList.of(
                    ReviewerStatusUpdate.create(
                        new Timestamp(1212L),
                        Account.id(1000),
                        Account.id(2002),
                        ReviewerStateInternal.CC),
                    ReviewerStatusUpdate.create(
                        new Timestamp(3434L),
                        Account.id(1000),
                        Account.id(2001),
                        ReviewerStateInternal.REVIEWER)))
            .build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addReviewerUpdate(
                ReviewerStatusUpdateProto.newBuilder()
                    .setTimestampMillis(1212L)
                    .setUpdatedBy(1000)
                    .setReviewer(2002)
                    .setState("CC"))
            .addReviewerUpdate(
                ReviewerStatusUpdateProto.newBuilder()
                    .setTimestampMillis(3434L)
                    .setUpdatedBy(1000)
                    .setReviewer(2001)
                    .setState("REVIEWER"))
            .build());
  }

  @Test
  public void serializeAttentionSetUpdates() throws Exception {
    assertRoundTrip(
        newBuilder()
            .attentionSet(
                ImmutableSet.of(
                    AttentionSetUpdate.createFromRead(
                        Instant.EPOCH.plusSeconds(23),
                        Account.id(1000),
                        AttentionSetUpdate.Operation.ADD,
                        "reason 1"),
                    AttentionSetUpdate.createFromRead(
                        Instant.EPOCH.plusSeconds(42),
                        Account.id(2000),
                        AttentionSetUpdate.Operation.REMOVE,
                        "reason 2")))
            .build(),
        newProtoBuilder()
            .addAttentionSetUpdate(
                AttentionSetUpdateProto.newBuilder()
                    .setTimestampMillis(23_000) // epoch millis
                    .setAccount(1000)
                    .setOperation("ADD")
                    .setReason("reason 1"))
            .addAttentionSetUpdate(
                AttentionSetUpdateProto.newBuilder()
                    .setTimestampMillis(42_000) // epoch millis
                    .setAccount(2000)
                    .setOperation("REMOVE")
                    .setReason("reason 2"))
            .build());
  }

  @Test
  public void serializeAssigneeUpdates() throws Exception {
    assertRoundTrip(
        newBuilder()
            .assigneeUpdates(
                ImmutableList.of(
                    AssigneeStatusUpdate.create(
                        new Timestamp(1212L), Account.id(1000), Optional.of(Account.id(2001))),
                    AssigneeStatusUpdate.create(
                        new Timestamp(3434L), Account.id(1000), Optional.empty())))
            .build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addAssigneeUpdate(
                AssigneeStatusUpdateProto.newBuilder()
                    .setTimestampMillis(1212L)
                    .setUpdatedBy(1000)
                    .setCurrentAssignee(2001)
                    .setHasCurrentAssignee(true))
            .addAssigneeUpdate(
                AssigneeStatusUpdateProto.newBuilder()
                    .setTimestampMillis(3434L)
                    .setUpdatedBy(1000)
                    .setHasCurrentAssignee(false))
            .build());
  }

  @Test
  public void serializeSubmitRecords() throws Exception {
    SubmitRecord sr1 = new SubmitRecord();
    sr1.status = SubmitRecord.Status.OK;

    SubmitRecord sr2 = new SubmitRecord();
    sr2.status = SubmitRecord.Status.FORCED;

    assertRoundTrip(
        newBuilder().submitRecords(ImmutableList.of(sr2, sr1)).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addSubmitRecord("{\"status\":\"FORCED\"}")
            .addSubmitRecord("{\"status\":\"OK\"}")
            .build());
  }

  @Test
  public void serializeChangeMessages() throws Exception {
    ChangeMessage m1 =
        new ChangeMessage(
            ChangeMessage.key(ID, "uuid1"),
            Account.id(1000),
            new Timestamp(1212L),
            PatchSet.id(ID, 1));
    ByteString m1Bytes = toByteString(m1, ChangeMessageProtoConverter.INSTANCE);
    assertThat(m1Bytes.size()).isEqualTo(35);

    ChangeMessage m2 =
        new ChangeMessage(
            ChangeMessage.key(ID, "uuid2"),
            Account.id(2000),
            new Timestamp(3434L),
            PatchSet.id(ID, 2));
    ByteString m2Bytes = toByteString(m2, ChangeMessageProtoConverter.INSTANCE);
    assertThat(m2Bytes.size()).isEqualTo(35);
    assertThat(m2Bytes).isNotEqualTo(m1Bytes);

    assertRoundTrip(
        newBuilder().changeMessages(ImmutableList.of(m2, m1)).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addChangeMessage(m2Bytes)
            .addChangeMessage(m1Bytes)
            .build());
  }

  @Test
  public void serializePublishedComments() throws Exception {
    HumanComment c1 =
        new HumanComment(
            new Comment.Key("uuid1", "file1", 1),
            Account.id(1001),
            new Timestamp(1212L),
            (short) 1,
            "message 1",
            "serverId",
            false);
    c1.setCommitId(ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    String c1Json = Serializer.GSON.toJson(c1);

    HumanComment c2 =
        new HumanComment(
            new Comment.Key("uuid2", "file2", 2),
            Account.id(1002),
            new Timestamp(3434L),
            (short) 2,
            "message 2",
            "serverId",
            true);
    c2.setCommitId(ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
    String c2Json = Serializer.GSON.toJson(c2);

    assertRoundTrip(
        newBuilder()
            .publishedComments(ImmutableListMultimap.of(c2.getCommitId(), c2, c1.getCommitId(), c1))
            .build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addPublishedComment(c2Json)
            .addPublishedComment(c1Json)
            .build());
  }

  @Test
  public void serializeUpdateCount() throws Exception {
    assertRoundTrip(
        newBuilder().updateCount(234).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .setUpdateCount(234)
            .build());
  }

  @Test
  public void changeNotesStateMethods() throws Exception {
    assertThatSerializedClass(ChangeNotesState.class)
        .hasAutoValueMethods(
            ImmutableMap.<String, Type>builder()
                .put("metaId", ObjectId.class)
                .put("changeId", Change.Id.class)
                .put("serverId", String.class)
                .put("columns", ChangeColumns.class)
                .put("hashtags", new TypeLiteral<ImmutableSet<String>>() {}.getType())
                .put(
                    "patchSets",
                    new TypeLiteral<ImmutableList<Map.Entry<PatchSet.Id, PatchSet>>>() {}.getType())
                .put(
                    "approvals",
                    new TypeLiteral<
                        ImmutableList<Map.Entry<PatchSet.Id, PatchSetApproval>>>() {}.getType())
                .put("reviewers", ReviewerSet.class)
                .put("reviewersByEmail", ReviewerByEmailSet.class)
                .put("pendingReviewers", ReviewerSet.class)
                .put("pendingReviewersByEmail", ReviewerByEmailSet.class)
                .put("allPastReviewers", new TypeLiteral<ImmutableList<Account.Id>>() {}.getType())
                .put(
                    "reviewerUpdates",
                    new TypeLiteral<ImmutableList<ReviewerStatusUpdate>>() {}.getType())
                .put(
                    "attentionSet",
                    new TypeLiteral<ImmutableSet<AttentionSetUpdate>>() {}.getType())
                .put(
                    "assigneeUpdates",
                    new TypeLiteral<ImmutableList<AssigneeStatusUpdate>>() {}.getType())
                .put("submitRecords", new TypeLiteral<ImmutableList<SubmitRecord>>() {}.getType())
                .put("changeMessages", new TypeLiteral<ImmutableList<ChangeMessage>>() {}.getType())
                .put(
                    "publishedComments",
                    new TypeLiteral<ImmutableListMultimap<ObjectId, HumanComment>>() {}.getType())
                .put("updateCount", int.class)
                .build());
  }

  @Test
  public void changeColumnsMethods() throws Exception {
    assertThatSerializedClass(ChangeColumns.class)
        .hasAutoValueMethods(
            ImmutableMap.<String, Type>builder()
                .put("changeKey", Change.Key.class)
                .put("createdOn", Timestamp.class)
                .put("lastUpdatedOn", Timestamp.class)
                .put("owner", Account.Id.class)
                .put("branch", String.class)
                .put("currentPatchSetId", PatchSet.Id.class)
                .put("subject", String.class)
                .put("topic", String.class)
                .put("originalSubject", String.class)
                .put("submissionId", String.class)
                .put("status", Change.Status.class)
                .put("isPrivate", boolean.class)
                .put("workInProgress", boolean.class)
                .put("reviewStarted", boolean.class)
                .put("revertOf", Change.Id.class)
                .put("cherryPickOf", PatchSet.Id.class)
                .put("toBuilder", ChangeNotesState.ChangeColumns.Builder.class)
                .build());
  }

  @Test
  public void patchSetFields() throws Exception {
    assertThatSerializedClass(PatchSet.class)
        .hasAutoValueMethods(
            ImmutableMap.<String, Type>builder()
                .put("id", PatchSet.Id.class)
                .put("commitId", ObjectId.class)
                .put("uploader", Account.Id.class)
                .put("createdOn", Timestamp.class)
                .put("groups", new TypeLiteral<ImmutableList<String>>() {}.getType())
                .put("pushCertificate", new TypeLiteral<Optional<String>>() {}.getType())
                .put("description", new TypeLiteral<Optional<String>>() {}.getType())
                .build());
  }

  @Test
  public void patchSetApprovalFields() throws Exception {
    assertThatSerializedClass(PatchSetApproval.Key.class)
        .hasAutoValueMethods(
            ImmutableMap.<String, Type>builder()
                .put("patchSetId", PatchSet.Id.class)
                .put("accountId", Account.Id.class)
                .put("labelId", LabelId.class)
                .build());
    assertThatSerializedClass(PatchSetApproval.class)
        .hasAutoValueMethods(
            ImmutableMap.<String, Type>builder()
                .put("key", PatchSetApproval.Key.class)
                .put("value", short.class)
                .put("granted", Timestamp.class)
                .put("tag", new TypeLiteral<Optional<String>>() {}.getType())
                .put("realAccountId", Account.Id.class)
                .put("postSubmit", boolean.class)
                .put("toBuilder", PatchSetApproval.Builder.class)
                .build());
  }

  @Test
  public void reviewerSetFields() throws Exception {
    assertThatSerializedClass(ReviewerSet.class)
        .hasFields(
            ImmutableMap.of(
                "table",
                new TypeLiteral<
                    ImmutableTable<ReviewerStateInternal, Account.Id, Timestamp>>() {}.getType(),
                "accounts",
                new TypeLiteral<ImmutableSet<Account.Id>>() {}.getType()));
  }

  @Test
  public void reviewerByEmailSetFields() throws Exception {
    assertThatSerializedClass(ReviewerByEmailSet.class)
        .hasFields(
            ImmutableMap.of(
                "table",
                new TypeLiteral<
                    ImmutableTable<ReviewerStateInternal, Address, Timestamp>>() {}.getType(),
                "users",
                new TypeLiteral<ImmutableSet<Address>>() {}.getType()));
  }

  @Test
  public void reviewerStatusUpdateMethods() throws Exception {
    assertThatSerializedClass(ReviewerStatusUpdate.class)
        .hasAutoValueMethods(
            ImmutableMap.of(
                "date", Timestamp.class,
                "updatedBy", Account.Id.class,
                "reviewer", Account.Id.class,
                "state", ReviewerStateInternal.class));
  }

  @Test
  public void assigneeStatusUpdateMethods() throws Exception {
    assertThatSerializedClass(AssigneeStatusUpdate.class)
        .hasAutoValueMethods(
            ImmutableMap.of(
                "date",
                Timestamp.class,
                "updatedBy",
                Account.Id.class,
                "currentAssignee",
                new TypeLiteral<Optional<Account.Id>>() {}.getType()));
  }

  @Test
  public void submitRecordFields() throws Exception {
    assertThatSerializedClass(SubmitRecord.class)
        .hasFields(
            ImmutableMap.of(
                "status",
                SubmitRecord.Status.class,
                "labels",
                new TypeLiteral<List<SubmitRecord.Label>>() {}.getType(),
                "requirements",
                new TypeLiteral<List<SubmitRequirement>>() {}.getType(),
                "errorMessage",
                String.class));
    assertThatSerializedClass(SubmitRecord.Label.class)
        .hasFields(
            ImmutableMap.of(
                "label", String.class,
                "status", SubmitRecord.Label.Status.class,
                "appliedBy", Account.Id.class));
    assertThatSerializedClass(SubmitRequirement.class)
        .hasAutoValueMethods(
            ImmutableMap.of(
                "fallbackText", String.class,
                "type", String.class));
  }

  @Test
  public void changeMessageFields() throws Exception {
    assertThatSerializedClass(ChangeMessage.Key.class)
        .hasAutoValueMethods(ImmutableMap.of("changeId", Change.Id.class, "uuid", String.class));
    assertThatSerializedClass(ChangeMessage.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("key", ChangeMessage.Key.class)
                .put("author", Account.Id.class)
                .put("writtenOn", Timestamp.class)
                .put("message", String.class)
                .put("patchset", PatchSet.Id.class)
                .put("tag", String.class)
                .put("realAuthor", Account.Id.class)
                .build());
  }

  @Test
  public void commentFields() throws Exception {
    assertThatSerializedClass(Comment.Key.class)
        .hasFields(
            ImmutableMap.of(
                "uuid", String.class, "filename", String.class, "patchSetId", int.class));
    assertThatSerializedClass(Comment.Identity.class).hasFields(ImmutableMap.of("id", int.class));
    assertThatSerializedClass(Comment.Range.class)
        .hasFields(
            ImmutableMap.of(
                "startLine", int.class,
                "startChar", int.class,
                "endLine", int.class,
                "endChar", int.class));
    assertThatSerializedClass(HumanComment.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("key", Comment.Key.class)
                .put("lineNbr", int.class)
                .put("author", Comment.Identity.class)
                .put("realAuthor", Comment.Identity.class)
                .put("writtenOn", Timestamp.class)
                .put("side", short.class)
                .put("message", String.class)
                .put("parentUuid", String.class)
                .put("range", Comment.Range.class)
                .put("tag", String.class)
                .put("revId", String.class)
                .put("serverId", String.class)
                .put("unresolved", boolean.class)
                .build());
  }

  @Test
  public void serializeServerId() throws Exception {
    assertRoundTrip(
        newBuilder().serverId(DEFAULT_SERVER_ID).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setServerId(DEFAULT_SERVER_ID)
            .setHasServerId(true)
            .setColumns(colsProto.toBuilder())
            .build());
  }

  private static ChangeNotesStateProto toProto(ChangeNotesState state) throws Exception {
    return ChangeNotesStateProto.parseFrom(Serializer.INSTANCE.serialize(state));
  }

  private static ChangeNotesState assertRoundTrip(
      ChangeNotesState state, ChangeNotesStateProto expectedProto) throws Exception {
    ChangeNotesStateProto actualProto = toProto(state);
    assertThat(actualProto).isEqualTo(expectedProto);
    ChangeNotesState actual = Serializer.INSTANCE.deserialize(Serializer.INSTANCE.serialize(state));
    assertThat(actual).isEqualTo(state);
    // It's possible that ChangeNotesState contains objects which implement equals without taking
    // into account all fields. Return the actual deserialized instance so that callers can perform
    // additional assertions if necessary.
    return actual;
  }
}
