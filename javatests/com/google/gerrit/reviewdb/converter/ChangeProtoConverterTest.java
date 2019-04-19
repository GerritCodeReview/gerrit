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

package com.google.gerrit.reviewdb.converter;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.protobuf.Parser;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import org.junit.Test;

public class ChangeProtoConverterTest {
  private final ChangeProtoConverter changeProtoConverter = ChangeProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    Change change =
        new Change(
            Change.key("change 1"),
            new Change.Id(14),
            Account.id(35),
            Branch.nameKey(Project.nameKey("project 67"), "branch 74"),
            new Timestamp(987654L));
    change.setLastUpdatedOn(new Timestamp(1234567L));
    change.setStatus(Change.Status.MERGED);
    change.setCurrentPatchSet(
        PatchSet.id(new Change.Id(14), 23), "subject XYZ", "original subject ABC");
    change.setTopic("my topic");
    change.setSubmissionId("submission ID 234");
    change.setAssignee(Account.id(100001));
    change.setPrivate(true);
    change.setWorkInProgress(true);
    change.setReviewStarted(true);
    change.setRevertOf(new Change.Id(180));

    Entities.Change proto = changeProtoConverter.toProto(change);

    Entities.Change expectedProto =
        Entities.Change.newBuilder()
            .setChangeId(Entities.Change_Id.newBuilder().setId(14))
            .setChangeKey(Entities.Change_Key.newBuilder().setId("change 1"))
            .setRowVersion(0)
            .setCreatedOn(987654L)
            .setLastUpdatedOn(1234567L)
            .setOwnerAccountId(Entities.Account_Id.newBuilder().setId(35))
            .setDest(
                Entities.Branch_NameKey.newBuilder()
                    .setProject(Entities.Project_NameKey.newBuilder().setName("project 67"))
                    .setBranch("refs/heads/branch 74"))
            .setStatus(Change.STATUS_MERGED)
            .setCurrentPatchSetId(23)
            .setSubject("subject XYZ")
            .setTopic("my topic")
            .setOriginalSubject("original subject ABC")
            .setSubmissionId("submission ID 234")
            .setAssignee(Entities.Account_Id.newBuilder().setId(100001))
            .setIsPrivate(true)
            .setWorkInProgress(true)
            .setReviewStarted(true)
            .setRevertOf(Entities.Change_Id.newBuilder().setId(180))
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void mandatoryValuesConvertedToProto() {
    Change change =
        new Change(
            Change.key("change 1"),
            new Change.Id(14),
            Account.id(35),
            Branch.nameKey(Project.nameKey("project 67"), "branch-74"),
            new Timestamp(987654L));

    Entities.Change proto = changeProtoConverter.toProto(change);

    Entities.Change expectedProto =
        Entities.Change.newBuilder()
            .setChangeId(Entities.Change_Id.newBuilder().setId(14))
            .setChangeKey(Entities.Change_Key.newBuilder().setId("change 1"))
            .setCreatedOn(987654L)
            // Defaults to createdOn if not set.
            .setLastUpdatedOn(987654L)
            .setOwnerAccountId(Entities.Account_Id.newBuilder().setId(35))
            .setDest(
                Entities.Branch_NameKey.newBuilder()
                    .setProject(Entities.Project_NameKey.newBuilder().setName("project 67"))
                    .setBranch("refs/heads/branch-74"))
            // Default values which can't be unset.
            .setCurrentPatchSetId(0)
            .setRowVersion(0)
            .setStatus(Change.STATUS_NEW)
            .setIsPrivate(false)
            .setWorkInProgress(false)
            .setReviewStarted(false)
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  // This test documents a special behavior which is necessary to ensure binary compatibility.
  @Test
  public void currentPatchSetIsAlwaysSetWhenConvertedToProto() {
    Change change =
        new Change(
            Change.key("change 1"),
            new Change.Id(14),
            Account.id(35),
            Branch.nameKey(Project.nameKey("project 67"), "branch-74"),
            new Timestamp(987654L));
    // O as ID actually means that no current patch set is present.
    change.setCurrentPatchSet(PatchSet.id(new Change.Id(14), 0), null, null);

    Entities.Change proto = changeProtoConverter.toProto(change);

    Entities.Change expectedProto =
        Entities.Change.newBuilder()
            .setChangeId(Entities.Change_Id.newBuilder().setId(14))
            .setChangeKey(Entities.Change_Key.newBuilder().setId("change 1"))
            .setCreatedOn(987654L)
            // Defaults to createdOn if not set.
            .setLastUpdatedOn(987654L)
            .setOwnerAccountId(Entities.Account_Id.newBuilder().setId(35))
            .setDest(
                Entities.Branch_NameKey.newBuilder()
                    .setProject(Entities.Project_NameKey.newBuilder().setName("project 67"))
                    .setBranch("refs/heads/branch-74"))
            .setCurrentPatchSetId(0)
            // Default values which can't be unset.
            .setRowVersion(0)
            .setStatus(Change.STATUS_NEW)
            .setIsPrivate(false)
            .setWorkInProgress(false)
            .setReviewStarted(false)
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  // This test documents a special behavior which is necessary to ensure binary compatibility.
  @Test
  public void originalSubjectIsNotAutomaticallySetToSubjectWhenConvertedToProto() {
    Change change =
        new Change(
            Change.key("change 1"),
            new Change.Id(14),
            Account.id(35),
            Branch.nameKey(Project.nameKey("project 67"), "branch-74"),
            new Timestamp(987654L));
    change.setCurrentPatchSet(PatchSet.id(new Change.Id(14), 23), "subject ABC", null);

    Entities.Change proto = changeProtoConverter.toProto(change);

    Entities.Change expectedProto =
        Entities.Change.newBuilder()
            .setChangeId(Entities.Change_Id.newBuilder().setId(14))
            .setChangeKey(Entities.Change_Key.newBuilder().setId("change 1"))
            .setCreatedOn(987654L)
            // Defaults to createdOn if not set.
            .setLastUpdatedOn(987654L)
            .setOwnerAccountId(Entities.Account_Id.newBuilder().setId(35))
            .setDest(
                Entities.Branch_NameKey.newBuilder()
                    .setProject(Entities.Project_NameKey.newBuilder().setName("project 67"))
                    .setBranch("refs/heads/branch-74"))
            .setCurrentPatchSetId(23)
            .setSubject("subject ABC")
            // Default values which can't be unset.
            .setRowVersion(0)
            .setStatus(Change.STATUS_NEW)
            .setIsPrivate(false)
            .setWorkInProgress(false)
            .setReviewStarted(false)
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    Change change =
        new Change(
            Change.key("change 1"),
            new Change.Id(14),
            Account.id(35),
            Branch.nameKey(Project.nameKey("project 67"), "branch-74"),
            new Timestamp(987654L));
    change.setLastUpdatedOn(new Timestamp(1234567L));
    change.setStatus(Change.Status.MERGED);
    change.setCurrentPatchSet(
        PatchSet.id(new Change.Id(14), 23), "subject XYZ", "original subject ABC");
    change.setTopic("my topic");
    change.setSubmissionId("submission ID 234");
    change.setAssignee(Account.id(100001));
    change.setPrivate(true);
    change.setWorkInProgress(true);
    change.setReviewStarted(true);
    change.setRevertOf(new Change.Id(180));

    Change convertedChange = changeProtoConverter.fromProto(changeProtoConverter.toProto(change));
    assertEqualChange(convertedChange, change);
  }

  @Test
  public void mandatoryValuesConvertedToProtoAndBackAgain() {
    Change change =
        new Change(
            Change.key("change 1"),
            new Change.Id(14),
            Account.id(35),
            Branch.nameKey(Project.nameKey("project 67"), "branch-74"),
            new Timestamp(987654L));

    Change convertedChange = changeProtoConverter.fromProto(changeProtoConverter.toProto(change));
    assertEqualChange(convertedChange, change);
  }

  // We need this special test as some values are only optional in the protobuf definition but can
  // never be unset in our entity object.
  @Test
  public void protoWithOnlyRequiredValuesCanBeConvertedBack() {
    Entities.Change proto =
        Entities.Change.newBuilder().setChangeId(Entities.Change_Id.newBuilder().setId(14)).build();
    Change change = changeProtoConverter.fromProto(proto);

    assertThat(change.getChangeId()).isEqualTo(14);
    // Values which can't be null according to ReviewDb's column definition but which are optional.
    assertThat(change.getKey()).isNull();
    assertThat(change.getOwner()).isNull();
    assertThat(change.getDest()).isNull();
    assertThat(change.getCreatedOn()).isEqualTo(new Timestamp(0));
    assertThat(change.getLastUpdatedOn()).isEqualTo(new Timestamp(0));
    assertThat(change.getSubject()).isNull();
    assertThat(change.currentPatchSetId()).isNull();
    // Default values for unset protobuf fields which can't be unset in the entity object.
    assertThat(change.getRowVersion()).isEqualTo(0);
    assertThat(change.isNew()).isTrue();
    assertThat(change.isPrivate()).isFalse();
    assertThat(change.isWorkInProgress()).isFalse();
    assertThat(change.hasReviewStarted()).isFalse();
  }

  @Test
  public void unsetLastUpdatedOnIsAutomaticallySetToCreatedOnWhenConvertedBack() {
    Entities.Change proto =
        Entities.Change.newBuilder()
            .setChangeId(Entities.Change_Id.newBuilder().setId(14))
            .setChangeKey(Entities.Change_Key.newBuilder().setId("change 1"))
            .setCreatedOn(987654L)
            .setOwnerAccountId(Entities.Account_Id.newBuilder().setId(35))
            .setDest(
                Entities.Branch_NameKey.newBuilder()
                    .setProject(Entities.Project_NameKey.newBuilder().setName("project 67"))
                    .setBranch("branch 74"))
            .build();
    Change change = changeProtoConverter.fromProto(proto);

    assertThat(change.getLastUpdatedOn()).isEqualTo(new Timestamp(987654L));
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Entities.Change proto =
        Entities.Change.newBuilder()
            .setChangeId(Entities.Change_Id.newBuilder().setId(14))
            .setChangeKey(Entities.Change_Key.newBuilder().setId("change 1"))
            .setRowVersion(0)
            .setCreatedOn(987654L)
            .setLastUpdatedOn(1234567L)
            .setOwnerAccountId(Entities.Account_Id.newBuilder().setId(35))
            .setDest(
                Entities.Branch_NameKey.newBuilder()
                    .setProject(Entities.Project_NameKey.newBuilder().setName("project 67"))
                    .setBranch("branch 74"))
            .setStatus(Change.STATUS_MERGED)
            .setCurrentPatchSetId(23)
            .setSubject("subject XYZ")
            .setTopic("my topic")
            .setOriginalSubject("original subject ABC")
            .setSubmissionId("submission ID 234")
            .setAssignee(Entities.Account_Id.newBuilder().setId(100001))
            .setIsPrivate(true)
            .setWorkInProgress(true)
            .setReviewStarted(true)
            .setRevertOf(Entities.Change_Id.newBuilder().setId(180))
            .build();
    byte[] bytes = proto.toByteArray();

    Parser<Entities.Change> parser = changeProtoConverter.getParser();
    Entities.Change parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void fieldsExistAsExpected() {
    assertThatSerializedClass(Change.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("changeId", Change.Id.class)
                .put("changeKey", Change.Key.class)
                .put("rowVersion", int.class)
                .put("createdOn", Timestamp.class)
                .put("lastUpdatedOn", Timestamp.class)
                .put("owner", Account.Id.class)
                .put("dest", Branch.NameKey.class)
                .put("status", char.class)
                .put("currentPatchSetId", int.class)
                .put("subject", String.class)
                .put("topic", String.class)
                .put("originalSubject", String.class)
                .put("submissionId", String.class)
                .put("assignee", Account.Id.class)
                .put("isPrivate", boolean.class)
                .put("workInProgress", boolean.class)
                .put("reviewStarted", boolean.class)
                .put("revertOf", Change.Id.class)
                .build());
  }

  // Unfortunately, Change doesn't implement equals(). Remove this method when we switch Change to
  // an AutoValue.
  private static void assertEqualChange(Change change, Change expectedChange) {
    assertThat(change.getChangeId()).isEqualTo(expectedChange.getChangeId());
    assertThat(change.getKey()).isEqualTo(expectedChange.getKey());
    assertThat(change.getRowVersion()).isEqualTo(expectedChange.getRowVersion());
    assertThat(change.getCreatedOn()).isEqualTo(expectedChange.getCreatedOn());
    assertThat(change.getLastUpdatedOn()).isEqualTo(expectedChange.getLastUpdatedOn());
    assertThat(change.getOwner()).isEqualTo(expectedChange.getOwner());
    assertThat(change.getDest()).isEqualTo(expectedChange.getDest());
    assertThat(change.getStatus()).isEqualTo(expectedChange.getStatus());
    assertThat(change.currentPatchSetId()).isEqualTo(expectedChange.currentPatchSetId());
    assertThat(change.getSubject()).isEqualTo(expectedChange.getSubject());
    assertThat(change.getTopic()).isEqualTo(expectedChange.getTopic());
    assertThat(change.getOriginalSubject()).isEqualTo(expectedChange.getOriginalSubject());
    assertThat(change.getSubmissionId()).isEqualTo(expectedChange.getSubmissionId());
    assertThat(change.getAssignee()).isEqualTo(expectedChange.getAssignee());
    assertThat(change.isPrivate()).isEqualTo(expectedChange.isPrivate());
    assertThat(change.isWorkInProgress()).isEqualTo(expectedChange.isWorkInProgress());
    assertThat(change.hasReviewStarted()).isEqualTo(expectedChange.hasReviewStarted());
    assertThat(change.getRevertOf()).isEqualTo(expectedChange.getRevertOf());
  }
}
