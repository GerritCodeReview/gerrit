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

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.protobuf.Parser;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import org.junit.Test;

public class PatchSetProtoConverterTest {
  private final PatchSetProtoConverter patchSetProtoConverter = PatchSetProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    PatchSet patchSet = new PatchSet(new PatchSet.Id(new Change.Id(103), 73));
    patchSet.setRevision(new RevId("aabbccddeeff"));
    patchSet.setUploader(Account.id(452));
    patchSet.setCreatedOn(new Timestamp(930349320L));
    patchSet.setGroups(ImmutableList.of("group1, group2"));
    patchSet.setPushCertificate("my push certificate");
    patchSet.setDescription("This is a patch set description.");

    Entities.PatchSet proto = patchSetProtoConverter.toProto(patchSet);

    Entities.PatchSet expectedProto =
        Entities.PatchSet.newBuilder()
            .setId(
                Entities.PatchSet_Id.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(103))
                    .setPatchSetId(73))
            .setRevision(Entities.RevId.newBuilder().setId("aabbccddeeff"))
            .setUploaderAccountId(Entities.Account_Id.newBuilder().setId(452))
            .setCreatedOn(930349320L)
            .setGroups("group1, group2")
            .setPushCertificate("my push certificate")
            .setDescription("This is a patch set description.")
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void mandatoryValuesConvertedToProto() {
    PatchSet patchSet = new PatchSet(new PatchSet.Id(new Change.Id(103), 73));

    Entities.PatchSet proto = patchSetProtoConverter.toProto(patchSet);

    Entities.PatchSet expectedProto =
        Entities.PatchSet.newBuilder()
            .setId(
                Entities.PatchSet_Id.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(103))
                    .setPatchSetId(73))
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    PatchSet patchSet = new PatchSet(new PatchSet.Id(new Change.Id(103), 73));
    patchSet.setRevision(new RevId("aabbccddeeff"));
    patchSet.setUploader(Account.id(452));
    patchSet.setCreatedOn(new Timestamp(930349320L));
    patchSet.setGroups(ImmutableList.of("group1, group2"));
    patchSet.setPushCertificate("my push certificate");
    patchSet.setDescription("This is a patch set description.");

    PatchSet convertedPatchSet =
        patchSetProtoConverter.fromProto(patchSetProtoConverter.toProto(patchSet));
    Truth.assertThat(convertedPatchSet).isEqualTo(patchSet);
  }

  @Test
  public void mandatoryValuesConvertedToProtoAndBackAgain() {
    PatchSet patchSet = new PatchSet(new PatchSet.Id(new Change.Id(103), 73));

    PatchSet convertedPatchSet =
        patchSetProtoConverter.fromProto(patchSetProtoConverter.toProto(patchSet));
    Truth.assertThat(convertedPatchSet).isEqualTo(patchSet);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Entities.PatchSet proto =
        Entities.PatchSet.newBuilder()
            .setId(
                Entities.PatchSet_Id.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(103))
                    .setPatchSetId(73))
            .build();
    byte[] bytes = proto.toByteArray();

    Parser<Entities.PatchSet> parser = patchSetProtoConverter.getParser();
    Entities.PatchSet parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void fieldsExistAsExpected() {
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
}
