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

import com.google.gerrit.proto.Protos;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import java.sql.Timestamp;
import org.junit.Test;

// TODO(aliceks): Delete after proving binary compatibility.
public class ChangeConverterCompatibilityTest {

  private final ProtobufCodec<Change> changeCodec = CodecFactory.encoder(Change.class);
  private final ChangeProtoConverter changeProtoConverter = ChangeProtoConverter.INSTANCE;

  @Test
  public void changeIndexFieldWithAllValuesIsBinaryCompatible() {
    Change change =
        new Change(
            new Change.Key("change 1"),
            new Change.Id(14),
            new Account.Id(35),
            new Branch.NameKey(new Project.NameKey("project 67"), "branch-74"),
            new Timestamp(987654L));
    change.setLastUpdatedOn(new Timestamp(1234567L));
    change.setStatus(Change.Status.MERGED);
    change.setCurrentPatchSet(
        new PatchSet.Id(new Change.Id(14), 23), "subject XYZ", "original subject ABC");
    change.setTopic("my topic");
    change.setSubmissionId("submission ID 234");
    change.setAssignee(new Account.Id(100001));
    change.setPrivate(true);
    change.setWorkInProgress(true);
    change.setReviewStarted(true);
    change.setRevertOf(new Change.Id(180));
    change.setNoteDbState("custom noteDb state");

    byte[] resultOfOldConverter = convertToProto_old(changeCodec, change);
    byte[] resultOfNewConverter = convertToProto_new(changeProtoConverter, change);

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  @Test
  public void changeIndexFieldWithMandatoryValuesIsBinaryCompatible() {
    Change change =
        new Change(
            new Change.Key("change 1"),
            new Change.Id(14),
            new Account.Id(35),
            new Branch.NameKey(new Project.NameKey("project 67"), "branch-74"),
            new Timestamp(987654L));

    byte[] resultOfOldConverter = convertToProto_old(changeCodec, change);
    byte[] resultOfNewConverter = convertToProto_new(changeProtoConverter, change);

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  @Test
  public void changeIndexFieldWithSubjectButNotOriginalSubjectIsBinaryCompatible() {
    Change change =
        new Change(
            new Change.Key("change 1"),
            new Change.Id(14),
            new Account.Id(35),
            new Branch.NameKey(new Project.NameKey("project 67"), "branch-74"),
            new Timestamp(987654L));
    change.setCurrentPatchSet(new PatchSet.Id(new Change.Id(14), 23), "subject XYZ", null);

    byte[] resultOfOldConverter = convertToProto_old(changeCodec, change);
    byte[] resultOfNewConverter = convertToProto_new(changeProtoConverter, change);

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  @Test
  public void changeIndexFieldWithoutPatchSetIsBinaryCompatible() {
    Change change =
        new Change(
            new Change.Key("change 1"),
            new Change.Id(14),
            new Account.Id(35),
            new Branch.NameKey(new Project.NameKey("project 67"), "branch-74"),
            new Timestamp(987654L));
    // O for patch set ID means that there isn't any current patch set.
    change.setCurrentPatchSet(new PatchSet.Id(new Change.Id(14), 0), null, null);

    byte[] resultOfOldConverter = convertToProto_old(changeCodec, change);
    byte[] resultOfNewConverter = convertToProto_new(changeProtoConverter, change);

    assertThat(resultOfNewConverter).isEqualTo(resultOfOldConverter);
  }

  // Copied from ChangeField.
  private static <T> byte[] convertToProto_old(ProtobufCodec<T> codec, T object) {
    return codec.encodeToByteArray(object);
  }

  // Copied from ChangeField.
  private static <T> byte[] convertToProto_new(ProtoConverter<?, T> converter, T object) {
    return Protos.toByteArray(converter.toProto(object));
  }
}
