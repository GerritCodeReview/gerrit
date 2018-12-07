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
import static com.google.gerrit.server.cache.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.proto.reviewdb.Reviewdb;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.protobuf.Parser;
import java.lang.reflect.Type;
import org.junit.Test;

public class PatchSetIdProtoConverterTest {
  private final PatchSetIdProtoConverter patchSetIdProtoConverter =
      PatchSetIdProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    PatchSet.Id patchSetId = new PatchSet.Id(new Change.Id(103), 73);

    Reviewdb.PatchSet_Id proto = patchSetIdProtoConverter.toProto(patchSetId);

    Reviewdb.PatchSet_Id expectedProto =
        Reviewdb.PatchSet_Id.newBuilder()
            .setChangeId(Reviewdb.Change_Id.newBuilder().setId(103))
            .setPatchSetId(73)
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    PatchSet.Id patchSetId = new PatchSet.Id(new Change.Id(20), 13);

    PatchSet.Id convertedPatchSetId =
        patchSetIdProtoConverter.fromProto(patchSetIdProtoConverter.toProto(patchSetId));

    assertThat(convertedPatchSetId).isEqualTo(patchSetId);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Reviewdb.PatchSet_Id proto =
        Reviewdb.PatchSet_Id.newBuilder()
            .setChangeId(Reviewdb.Change_Id.newBuilder().setId(103))
            .setPatchSetId(73)
            .build();
    byte[] bytes = proto.toByteArray();

    Parser<Reviewdb.PatchSet_Id> parser = patchSetIdProtoConverter.getParser();
    Reviewdb.PatchSet_Id parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /**
   * See {@link com.google.gerrit.server.cache.testing.SerializedClassSubject} for background and
   * what to do if this test fails.
   */
  @Test
  public void fieldsExistAsExpected() {
    assertThatSerializedClass(PatchSet.Id.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("changeId", Change.Id.class)
                .put("patchSetId", int.class)
                .build());
  }
}
