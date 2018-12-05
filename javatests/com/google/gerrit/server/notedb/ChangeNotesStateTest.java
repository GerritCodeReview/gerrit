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
import static com.google.gerrit.reviewdb.server.ReviewDbCodecs.APPROVAL_CODEC;
import static com.google.gerrit.reviewdb.server.ReviewDbCodecs.MESSAGE_CODEC;
import static com.google.gerrit.reviewdb.server.ReviewDbCodecs.PATCH_SET_CODEC;
import static com.google.gerrit.server.cache.serialize.ProtoCacheSerializers.toByteString;
import static com.google.gerrit.server.cache.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitRequirement;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.Comment;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.ReviewerByEmailSet;
import com.google.gerrit.server.ReviewerSet;
import com.google.gerrit.server.ReviewerStatusUpdate;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.ChangeColumnsProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.ReviewerByEmailSetEntryProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.ReviewerSetEntryProto;
import com.google.gerrit.server.cache.proto.Cache.ChangeNotesStateProto.ReviewerStatusUpdateProto;
import com.google.gerrit.server.cache.serialize.ProtoCacheSerializers.ObjectIdConverter;
import com.google.gerrit.server.notedb.ChangeNotesState.ChangeColumns;
import com.google.gerrit.server.notedb.ChangeNotesState.Serializer;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;
import com.google.inject.TypeLiteral;
import com.google.protobuf.ByteString;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Before;
import org.junit.Test;

public class ChangeNotesStateTest {
  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  private static final Change.Id ID = new Change.Id(123);
  private static final ObjectId SHA =
      ObjectId.fromString("1234567812345678123456781234567812345678");
  private static final ByteString SHA_BYTES = ObjectIdConverter.create().toByteString(SHA);
  private static final String CHANGE_KEY = "Iabcdabcdabcdabcdabcdabcdabcdabcdabcdabcd";

  private ChangeColumns cols;
  private ChangeColumnsProto colsProto;

  @Before
  public void setUp() throws Exception {
    cols =
        ChangeColumns.builder()
            .changeKey(new Change.Key(CHANGE_KEY))
            .createdOn(new Timestamp(123456L))
            .lastUpdatedOn(new Timestamp(234567L))
            .owner(new Account.Id(1000))
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

  @Test
  public void serializeChangeKey() throws Exception {
    assertRoundTrip(
        newBuilder()
            .columns(
                cols.toBuilder()
                    .changeKey(new Change.Key("Ieeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"))
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
            .setColumns(colsProto.toBuilder().setCreatedOn(98765L))
            .build());
  }

  @Test
  public void serializeLastUpdatedOn() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().lastUpdatedOn(new Timestamp(98765L)).build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setLastUpdatedOn(98765L))
            .build());
  }

  @Test
  public void serializeOwner() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().owner(new Account.Id(7777)).build()).build(),
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
            .columns(cols.toBuilder().currentPatchSetId(new PatchSet.Id(ID, 2)).build())
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
  public void serializeAssignee() throws Exception {
    assertRoundTrip(
        newBuilder().columns(cols.toBuilder().assignee(new Account.Id(2000)).build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setAssignee(2000).setHasAssignee(true))
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
        newBuilder().columns(cols.toBuilder().revertOf(new Change.Id(999)).build()).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto.toBuilder().setRevertOf(999).setHasRevertOf(true))
            .build());
  }

  @Test
  public void serializePastAssignees() throws Exception {
    assertRoundTrip(
        newBuilder()
            .pastAssignees(ImmutableSet.of(new Account.Id(2002), new Account.Id(2001)))
            .build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addPastAssignee(2002)
            .addPastAssignee(2001)
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
    PatchSet ps1 = new PatchSet(new PatchSet.Id(ID, 1));
    ps1.setUploader(new Account.Id(2000));
    ps1.setRevision(new RevId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    ps1.setCreatedOn(cols.createdOn());
    ByteString ps1Bytes = toByteString(ps1, PATCH_SET_CODEC);
    assertThat(ps1Bytes.size()).isEqualTo(66);

    PatchSet ps2 = new PatchSet(new PatchSet.Id(ID, 2));
    ps2.setUploader(new Account.Id(3000));
    ps2.setRevision(new RevId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
    ps2.setCreatedOn(cols.lastUpdatedOn());
    ByteString ps2Bytes = toByteString(ps2, PATCH_SET_CODEC);
    assertThat(ps2Bytes.size()).isEqualTo(66);
    assertThat(ps2Bytes).isNotEqualTo(ps1Bytes);

    assertRoundTrip(
        newBuilder()
            .patchSets(ImmutableMap.of(ps2.getId(), ps2, ps1.getId(), ps1).entrySet())
            .build(),
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
        new PatchSetApproval(
            new PatchSetApproval.Key(
                new PatchSet.Id(ID, 1), new Account.Id(2001), new LabelId("Code-Review")),
            (short) 1,
            new Timestamp(1212L));
    ByteString a1Bytes = toByteString(a1, APPROVAL_CODEC);
    assertThat(a1Bytes.size()).isEqualTo(43);

    PatchSetApproval a2 =
        new PatchSetApproval(
            new PatchSetApproval.Key(
                new PatchSet.Id(ID, 1), new Account.Id(2002), new LabelId("Verified")),
            (short) -1,
            new Timestamp(3434L));
    ByteString a2Bytes = toByteString(a2, APPROVAL_CODEC);
    assertThat(a2Bytes.size()).isEqualTo(49);
    assertThat(a2Bytes).isNotEqualTo(a1Bytes);

    assertRoundTrip(
        newBuilder()
            .approvals(
                ImmutableListMultimap.of(a2.getPatchSetId(), a2, a1.getPatchSetId(), a1).entries())
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
                        .put(ReviewerStateInternal.CC, new Account.Id(2001), new Timestamp(1212L))
                        .put(
                            ReviewerStateInternal.REVIEWER,
                            new Account.Id(2002),
                            new Timestamp(3434L))
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
                    .setTimestamp(1212L))
            .addReviewer(
                ReviewerSetEntryProto.newBuilder()
                    .setState("REVIEWER")
                    .setAccountId(2002)
                    .setTimestamp(3434L))
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
                            new Address("Name1", "email1@example.com"),
                            new Timestamp(1212L))
                        .put(
                            ReviewerStateInternal.REVIEWER,
                            new Address("Name2", "email2@example.com"),
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
                    .setTimestamp(1212L))
            .addReviewerByEmail(
                ReviewerByEmailSetEntryProto.newBuilder()
                    .setState("REVIEWER")
                    .setAddress("Name2 <email2@example.com>")
                    .setTimestamp(3434L))
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
                            new Address("emailonly@example.com"),
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
                        .setTimestamp(1212L))
                .build());

    // Address doesn't consider the name field in equals, so we have to check it manually.
    // TODO(dborowitz): Fix Address#equals.
    ImmutableSet<Address> ccs = actual.reviewersByEmail().byState(ReviewerStateInternal.CC);
    assertThat(ccs).hasSize(1);
    Address address = Iterables.getOnlyElement(ccs);
    assertThat(address.getName()).isNull();
    assertThat(address.getEmail()).isEqualTo("emailonly@example.com");
  }

  @Test
  public void serializePendingReviewers() throws Exception {
    assertRoundTrip(
        newBuilder()
            .pendingReviewers(
                ReviewerSet.fromTable(
                    ImmutableTable.<ReviewerStateInternal, Account.Id, Timestamp>builder()
                        .put(ReviewerStateInternal.CC, new Account.Id(2001), new Timestamp(1212L))
                        .put(
                            ReviewerStateInternal.REVIEWER,
                            new Account.Id(2002),
                            new Timestamp(3434L))
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
                    .setTimestamp(1212L))
            .addPendingReviewer(
                ReviewerSetEntryProto.newBuilder()
                    .setState("REVIEWER")
                    .setAccountId(2002)
                    .setTimestamp(3434L))
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
                            new Address("Name1", "email1@example.com"),
                            new Timestamp(1212L))
                        .put(
                            ReviewerStateInternal.REVIEWER,
                            new Address("Name2", "email2@example.com"),
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
                    .setTimestamp(1212L))
            .addPendingReviewerByEmail(
                ReviewerByEmailSetEntryProto.newBuilder()
                    .setState("REVIEWER")
                    .setAddress("Name2 <email2@example.com>")
                    .setTimestamp(3434L))
            .build());
  }

  @Test
  public void serializeAllPastReviewers() throws Exception {
    assertRoundTrip(
        newBuilder()
            .allPastReviewers(ImmutableList.of(new Account.Id(2002), new Account.Id(2001)))
            .build(),
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
                        Optional.of(new Account.Id(1000)),
                        new Account.Id(2002),
                        ReviewerStateInternal.CC),
                    ReviewerStatusUpdate.create(
                        new Timestamp(3434L),
                        Optional.of(new Account.Id(1000)),
                        new Account.Id(2001),
                        ReviewerStateInternal.REVIEWER)))
            .build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .addReviewerUpdate(
                ReviewerStatusUpdateProto.newBuilder()
                    .setDate(1212L)
                    .setUpdatedBy(1000)
                    .setReviewer(2002)
                    .setState("CC"))
            .addReviewerUpdate(
                ReviewerStatusUpdateProto.newBuilder()
                    .setDate(3434L)
                    .setUpdatedBy(1000)
                    .setReviewer(2001)
                    .setState("REVIEWER"))
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
            new ChangeMessage.Key(ID, "uuid1"),
            new Account.Id(1000),
            new Timestamp(1212L),
            new PatchSet.Id(ID, 1));
    ByteString m1Bytes = toByteString(m1, MESSAGE_CODEC);
    assertThat(m1Bytes.size()).isEqualTo(35);

    ChangeMessage m2 =
        new ChangeMessage(
            new ChangeMessage.Key(ID, "uuid2"),
            new Account.Id(2000),
            new Timestamp(3434L),
            new PatchSet.Id(ID, 2));
    ByteString m2Bytes = toByteString(m2, MESSAGE_CODEC);
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
    Comment c1 =
        new Comment(
            new Comment.Key("uuid1", "file1", 1),
            new Account.Id(1001),
            new Timestamp(1212L),
            (short) 1,
            "message 1",
            "serverId",
            false);
    c1.setRevId(new RevId("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
    String c1Json = Serializer.GSON.toJson(c1);

    Comment c2 =
        new Comment(
            new Comment.Key("uuid2", "file2", 2),
            new Account.Id(1002),
            new Timestamp(3434L),
            (short) 2,
            "message 2",
            "serverId",
            true);
    c2.setRevId(new RevId("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
    String c2Json = Serializer.GSON.toJson(c2);

    assertRoundTrip(
        newBuilder()
            .publishedComments(
                ImmutableListMultimap.of(new RevId(c2.revId), c2, new RevId(c1.revId), c1))
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
  public void serializeReadOnlyUntil() throws Exception {
    assertRoundTrip(
        newBuilder().readOnlyUntil(new Timestamp(1212L)).build(),
        ChangeNotesStateProto.newBuilder()
            .setMetaId(SHA_BYTES)
            .setChangeId(ID.get())
            .setColumns(colsProto)
            .setReadOnlyUntil(1212L)
            .setHasReadOnlyUntil(true)
            .build());
  }

  @Test
  public void changeNotesStateMethods() throws Exception {
    assertThatSerializedClass(ChangeNotesState.class)
        .hasAutoValueMethods(
            ImmutableMap.<String, Type>builder()
                .put("metaId", ObjectId.class)
                .put("changeId", Change.Id.class)
                .put("columns", ChangeColumns.class)
                .put("pastAssignees", new TypeLiteral<ImmutableSet<Account.Id>>() {}.getType())
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
                .put("submitRecords", new TypeLiteral<ImmutableList<SubmitRecord>>() {}.getType())
                .put("changeMessages", new TypeLiteral<ImmutableList<ChangeMessage>>() {}.getType())
                .put(
                    "publishedComments",
                    new TypeLiteral<ImmutableListMultimap<RevId, Comment>>() {}.getType())
                .put("readOnlyUntil", Timestamp.class)
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
                .put("assignee", Account.Id.class)
                .put("status", Change.Status.class)
                .put("isPrivate", boolean.class)
                .put("workInProgress", boolean.class)
                .put("reviewStarted", boolean.class)
                .put("revertOf", Change.Id.class)
                .put("toBuilder", ChangeNotesState.ChangeColumns.Builder.class)
                .build());
  }

  @Test
  public void patchSetFields() throws Exception {
    assertThatSerializedClass(PatchSet.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("id", PatchSet.Id.class)
                .put("revision", RevId.class)
                .put("uploader", Account.Id.class)
                .put("createdOn", Timestamp.class)
                .put("groups", String.class)
                .put("pushCertificate", String.class)
                .put("description", String.class)
                .build());
  }

  @Test
  public void patchSetApprovalFields() throws Exception {
    assertThatSerializedClass(PatchSetApproval.Key.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("patchSetId", PatchSet.Id.class)
                .put("accountId", Account.Id.class)
                .put("categoryId", LabelId.class)
                .build());
    assertThatSerializedClass(PatchSetApproval.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("key", PatchSetApproval.Key.class)
                .put("value", short.class)
                .put("granted", Timestamp.class)
                .put("tag", String.class)
                .put("realAccountId", Account.Id.class)
                .put("postSubmit", boolean.class)
                .build());
  }

  @Test
  public void reviewerSetFields() throws Exception {
    assertThatSerializedClass(ReviewerSet.class)
        .hasFields(
            ImmutableMap.of(
                "table",
                    new TypeLiteral<
                        ImmutableTable<
                            ReviewerStateInternal, Account.Id, Timestamp>>() {}.getType(),
                "accounts", new TypeLiteral<ImmutableSet<Account.Id>>() {}.getType()));
  }

  @Test
  public void reviewerByEmailSetFields() throws Exception {
    assertThatSerializedClass(ReviewerByEmailSet.class)
        .hasFields(
            ImmutableMap.of(
                "table",
                    new TypeLiteral<
                        ImmutableTable<ReviewerStateInternal, Address, Timestamp>>() {}.getType(),
                "users", new TypeLiteral<ImmutableSet<Address>>() {}.getType()));
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
                "type", String.class,
                "data", new TypeLiteral<ImmutableMap<String, String>>() {}.getType()));
  }

  @Test
  public void changeMessageFields() throws Exception {
    assertThatSerializedClass(ChangeMessage.Key.class)
        .hasFields(ImmutableMap.of("changeId", Change.Id.class, "uuid", String.class));
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
    assertThatSerializedClass(Comment.class)
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
                .put("legacyFormat", boolean.class)
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
