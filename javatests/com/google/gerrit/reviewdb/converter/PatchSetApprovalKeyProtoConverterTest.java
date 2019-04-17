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
import org.junit.Test;

public class PatchSetApprovalKeyProtoConverterTest {
  private final PatchSetApprovalKeyProtoConverter protoConverter =
      PatchSetApprovalKeyProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    PatchSetApproval.Key key =
        PatchSetApproval.key(
            new PatchSet.Id(new Change.Id(42), 14), Account.id(100013), LabelId.create("label-8"));

    Entities.PatchSetApproval_Key proto = protoConverter.toProto(key);

    Entities.PatchSetApproval_Key expectedProto =
        Entities.PatchSetApproval_Key.newBuilder()
            .setPatchSetId(
                Entities.PatchSet_Id.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(42))
                    .setPatchSetId(14))
            .setAccountId(Entities.Account_Id.newBuilder().setId(100013))
            .setCategoryId(Entities.LabelId.newBuilder().setId("label-8"))
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    PatchSetApproval.Key key =
        PatchSetApproval.key(
            new PatchSet.Id(new Change.Id(42), 14), Account.id(100013), LabelId.create("label-8"));

    PatchSetApproval.Key convertedKey = protoConverter.fromProto(protoConverter.toProto(key));

    assertThat(convertedKey).isEqualTo(key);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Entities.PatchSetApproval_Key proto =
        Entities.PatchSetApproval_Key.newBuilder()
            .setPatchSetId(
                Entities.PatchSet_Id.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(42))
                    .setPatchSetId(14))
            .setAccountId(Entities.Account_Id.newBuilder().setId(100013))
            .setCategoryId(Entities.LabelId.newBuilder().setId("label-8"))
            .build();
    byte[] bytes = proto.toByteArray();

    Parser<Entities.PatchSetApproval_Key> parser = protoConverter.getParser();
    Entities.PatchSetApproval_Key parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void fieldsExistAsExpected() {
    assertThatSerializedClass(PatchSetApproval.Key.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("patchSetId", PatchSet.Id.class)
                .put("accountId", Account.Id.class)
                .put("categoryId", LabelId.class)
                .build());
  }
}
