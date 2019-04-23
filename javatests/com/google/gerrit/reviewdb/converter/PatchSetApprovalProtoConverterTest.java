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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.protobuf.Parser;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.util.Date;
import org.junit.Test;

public class PatchSetApprovalProtoConverterTest {
  private final PatchSetApprovalProtoConverter protoConverter =
      PatchSetApprovalProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    PatchSetApproval patchSetApproval =
        new PatchSetApproval(
            PatchSetApproval.key(
                PatchSet.id(Change.id(42), 14), Account.id(100013), LabelId.create("label-8")),
            (short) 456,
            new Date(987654L));
    patchSetApproval.setTag("tag-21");
    patchSetApproval.setRealAccountId(Account.id(612));
    patchSetApproval.setPostSubmit(true);

    Entities.PatchSetApproval proto = protoConverter.toProto(patchSetApproval);

    Entities.PatchSetApproval expectedProto =
        Entities.PatchSetApproval.newBuilder()
            .setKey(
                Entities.PatchSetApproval_Key.newBuilder()
                    .setPatchSetId(
                        Entities.PatchSet_Id.newBuilder()
                            .setChangeId(Entities.Change_Id.newBuilder().setId(42))
                            .setId(14))
                    .setAccountId(Entities.Account_Id.newBuilder().setId(100013))
                    .setLabelId(Entities.LabelId.newBuilder().setId("label-8")))
            .setValue(456)
            .setGranted(987654L)
            .setTag("tag-21")
            .setRealAccountId(Entities.Account_Id.newBuilder().setId(612))
            .setPostSubmit(true)
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void mandatoryValuesConvertedToProto() {
    PatchSetApproval patchSetApproval =
        new PatchSetApproval(
            PatchSetApproval.key(
                PatchSet.id(Change.id(42), 14), Account.id(100013), LabelId.create("label-8")),
            (short) 456,
            new Date(987654L));

    Entities.PatchSetApproval proto = protoConverter.toProto(patchSetApproval);

    Entities.PatchSetApproval expectedProto =
        Entities.PatchSetApproval.newBuilder()
            .setKey(
                Entities.PatchSetApproval_Key.newBuilder()
                    .setPatchSetId(
                        Entities.PatchSet_Id.newBuilder()
                            .setChangeId(Entities.Change_Id.newBuilder().setId(42))
                            .setId(14))
                    .setAccountId(Entities.Account_Id.newBuilder().setId(100013))
                    .setLabelId(Entities.LabelId.newBuilder().setId("label-8")))
            .setValue(456)
            .setGranted(987654L)
            // This value can't be unset when our entity class is given.
            .setPostSubmit(false)
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    PatchSetApproval patchSetApproval =
        new PatchSetApproval(
            PatchSetApproval.key(
                PatchSet.id(Change.id(42), 14), Account.id(100013), LabelId.create("label-8")),
            (short) 456,
            new Date(987654L));
    patchSetApproval.setTag("tag-21");
    patchSetApproval.setRealAccountId(Account.id(612));
    patchSetApproval.setPostSubmit(true);

    PatchSetApproval convertedPatchSetApproval =
        protoConverter.fromProto(protoConverter.toProto(patchSetApproval));
    assertThat(convertedPatchSetApproval).isEqualTo(patchSetApproval);
  }

  @Test
  public void mandatoryValuesConvertedToProtoAndBackAgain() {
    PatchSetApproval patchSetApproval =
        new PatchSetApproval(
            PatchSetApproval.key(
                PatchSet.id(Change.id(42), 14), Account.id(100013), LabelId.create("label-8")),
            (short) 456,
            new Date(987654L));

    PatchSetApproval convertedPatchSetApproval =
        protoConverter.fromProto(protoConverter.toProto(patchSetApproval));
    assertThat(convertedPatchSetApproval).isEqualTo(patchSetApproval);
  }

  // We need this special test as some values are only optional in the protobuf definition but can
  // never be unset in our entity object.
  @Test
  public void protoWithOnlyRequiredValuesCanBeConvertedBack() {
    Entities.PatchSetApproval proto =
        Entities.PatchSetApproval.newBuilder()
            .setKey(
                Entities.PatchSetApproval_Key.newBuilder()
                    .setPatchSetId(
                        Entities.PatchSet_Id.newBuilder()
                            .setChangeId(Entities.Change_Id.newBuilder().setId(42))
                            .setId(14))
                    .setAccountId(Entities.Account_Id.newBuilder().setId(100013))
                    .setLabelId(Entities.LabelId.newBuilder().setId("label-8")))
            .build();
    PatchSetApproval patchSetApproval = protoConverter.fromProto(proto);

    assertThat(patchSetApproval.getPatchSetId()).isEqualTo(PatchSet.id(Change.id(42), 14));
    assertThat(patchSetApproval.getAccountId()).isEqualTo(Account.id(100013));
    assertThat(patchSetApproval.getLabelId()).isEqualTo(LabelId.create("label-8"));
    // Default values for unset protobuf fields which can't be unset in the entity object.
    assertThat(patchSetApproval.getValue()).isEqualTo(0);
    assertThat(patchSetApproval.getGranted()).isEqualTo(new Timestamp(0));
    assertThat(patchSetApproval.isPostSubmit()).isEqualTo(false);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Entities.PatchSetApproval proto =
        Entities.PatchSetApproval.newBuilder()
            .setKey(
                Entities.PatchSetApproval_Key.newBuilder()
                    .setPatchSetId(
                        Entities.PatchSet_Id.newBuilder()
                            .setChangeId(Entities.Change_Id.newBuilder().setId(42))
                            .setId(14))
                    .setAccountId(Entities.Account_Id.newBuilder().setId(100013))
                    .setLabelId(Entities.LabelId.newBuilder().setId("label-8")))
            .setValue(456)
            .setGranted(987654L)
            .build();
    byte[] bytes = proto.toByteArray();

    Parser<Entities.PatchSetApproval> parser = protoConverter.getParser();
    Entities.PatchSetApproval parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void fieldsExistAsExpected() {
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
}
