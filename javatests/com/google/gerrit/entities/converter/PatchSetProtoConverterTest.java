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

package com.google.gerrit.entities.converter;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import com.google.inject.TypeLiteral;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class PatchSetProtoConverterTest {
  private final PatchSetProtoConverter patchSetProtoConverter = PatchSetProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    PatchSet patchSet =
        PatchSet.builder()
            .id(PatchSet.id(Change.id(103), 73))
            .commitId(ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
            .uploader(Account.id(452))
            .realUploader(Account.id(687))
            .createdOn(Instant.ofEpochMilli(930349320L))
            .groups(ImmutableList.of("group1", " group2"))
            .pushCertificate("my push certificate")
            .description("This is a patch set description.")
            .build();

    Entities.PatchSet proto = patchSetProtoConverter.toProto(patchSet);

    Entities.PatchSet expectedProto =
        Entities.PatchSet.newBuilder()
            .setId(
                Entities.PatchSet_Id.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(103))
                    .setId(73))
            .setCommitId(
                Entities.ObjectId.newBuilder().setName("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
            .setUploaderAccountId(Entities.Account_Id.newBuilder().setId(452))
            .setRealUploaderAccountId(Entities.Account_Id.newBuilder().setId(687))
            .setCreatedOn(930349320L)
            .setGroups("group1, group2")
            .setPushCertificate("my push certificate")
            .setDescription("This is a patch set description.")
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void mandatoryValuesConvertedToProto() {
    PatchSet patchSet =
        PatchSet.builder()
            .id(PatchSet.id(Change.id(103), 73))
            .commitId(ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
            .uploader(Account.id(452))
            .realUploader(Account.id(687))
            .createdOn(Instant.ofEpochMilli(930349320L))
            .build();

    Entities.PatchSet proto = patchSetProtoConverter.toProto(patchSet);

    Entities.PatchSet expectedProto =
        Entities.PatchSet.newBuilder()
            .setId(
                Entities.PatchSet_Id.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(103))
                    .setId(73))
            .setCommitId(
                Entities.ObjectId.newBuilder().setName("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
            .setUploaderAccountId(Entities.Account_Id.newBuilder().setId(452))
            .setRealUploaderAccountId(Entities.Account_Id.newBuilder().setId(687))
            .setCreatedOn(930349320L)
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    PatchSet patchSet =
        PatchSet.builder()
            .id(PatchSet.id(Change.id(103), 73))
            .commitId(ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
            .uploader(Account.id(452))
            .realUploader(Account.id(687))
            .createdOn(Instant.ofEpochMilli(930349320L))
            .groups(ImmutableList.of("group1", " group2"))
            .pushCertificate("my push certificate")
            .description("This is a patch set description.")
            .build();

    PatchSet convertedPatchSet =
        patchSetProtoConverter.fromProto(patchSetProtoConverter.toProto(patchSet));
    Truth.assertThat(convertedPatchSet).isEqualTo(patchSet);
  }

  @Test
  public void mandatoryValuesConvertedToProtoAndBackAgain() {
    PatchSet patchSet =
        PatchSet.builder()
            .id(PatchSet.id(Change.id(103), 73))
            .commitId(ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef"))
            .uploader(Account.id(452))
            .realUploader(Account.id(687))
            .createdOn(Instant.ofEpochMilli(930349320L))
            .build();

    PatchSet convertedPatchSet =
        patchSetProtoConverter.fromProto(patchSetProtoConverter.toProto(patchSet));
    Truth.assertThat(convertedPatchSet).isEqualTo(patchSet);
  }

  @Test
  public void previouslyOptionalValuesMayBeMissingFromProto() {
    Entities.PatchSet proto =
        Entities.PatchSet.newBuilder()
            .setId(
                Entities.PatchSet_Id.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(103))
                    .setId(73))
            .build();

    PatchSet convertedPatchSet = patchSetProtoConverter.fromProto(proto);
    Truth.assertThat(convertedPatchSet)
        .isEqualTo(
            PatchSet.builder()
                .id(PatchSet.id(Change.id(103), 73))
                .commitId(ObjectId.fromString("0000000000000000000000000000000000000000"))
                .uploader(Account.id(0))
                .realUploader(Account.id(0))
                .createdOn(Instant.EPOCH)
                .build());
  }

  @Test
  public void realUploaderIsSetToUploaderIfMissingFromProto() {
    Entities.PatchSet proto =
        Entities.PatchSet.newBuilder()
            .setId(
                Entities.PatchSet_Id.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(103))
                    .setId(73))
            .setUploaderAccountId(Entities.Account_Id.newBuilder().setId(452))
            .build();

    PatchSet convertedPatchSet = patchSetProtoConverter.fromProto(proto);
    Truth.assertThat(convertedPatchSet)
        .isEqualTo(
            PatchSet.builder()
                .id(PatchSet.id(Change.id(103), 73))
                .commitId(ObjectId.fromString("0000000000000000000000000000000000000000"))
                .uploader(Account.id(452))
                .realUploader(Account.id(452))
                .createdOn(Instant.EPOCH)
                .build());
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void fieldsExistAsExpected() {
    assertThatSerializedClass(PatchSet.class)
        .hasAutoValueMethods(
            ImmutableMap.<String, Type>builder()
                .put("id", PatchSet.Id.class)
                .put("commitId", ObjectId.class)
                .put("uploader", Account.Id.class)
                .put("realUploader", Account.Id.class)
                .put("createdOn", Instant.class)
                .put("groups", new TypeLiteral<ImmutableList<String>>() {}.getType())
                .put("pushCertificate", new TypeLiteral<Optional<String>>() {}.getType())
                .put("description", new TypeLiteral<Optional<String>>() {}.getType())
                .build());
  }
}
