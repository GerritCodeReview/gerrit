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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import java.lang.reflect.Type;
import org.junit.Test;

public class PatchSetIdProtoConverterTest {
  private final PatchSetIdProtoConverter patchSetIdProtoConverter =
      PatchSetIdProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    PatchSet.Id patchSetId = PatchSet.id(Change.id(103, "foo"), 73);

    Entities.PatchSet_Id proto = patchSetIdProtoConverter.toProto(patchSetId);

    Entities.PatchSet_Id expectedProto =
        Entities.PatchSet_Id.newBuilder()
            .setChangeId(Entities.Change_Id.newBuilder().setId(103).setProjectName("foo"))
            .setId(73)
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    PatchSet.Id patchSetId = PatchSet.id(Change.id(20, "foo"), 13);

    PatchSet.Id convertedPatchSetId =
        patchSetIdProtoConverter.fromProto(patchSetIdProtoConverter.toProto(patchSetId));

    assertThat(convertedPatchSetId).isEqualTo(patchSetId);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void methodsExistAsExpected() {
    assertThatSerializedClass(PatchSet.Id.class)
        .hasAutoValueMethods(
            ImmutableMap.<String, Type>builder()
                .put("changeId", Change.Id.class)
                .put("id", int.class)
                .build());
  }
}
