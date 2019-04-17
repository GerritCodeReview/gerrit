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
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.protobuf.Parser;
import org.junit.Test;

public class LabelIdProtoConverterTest {
  private final LabelIdProtoConverter labelIdProtoConverter = LabelIdProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    LabelId labelId = LabelId.create("Label ID 42");

    Entities.LabelId proto = labelIdProtoConverter.toProto(labelId);

    Entities.LabelId expectedProto = Entities.LabelId.newBuilder().setId("Label ID 42").build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    LabelId labelId = LabelId.create("label-5");

    LabelId convertedLabelId =
        labelIdProtoConverter.fromProto(labelIdProtoConverter.toProto(labelId));

    assertThat(convertedLabelId).isEqualTo(labelId);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Entities.LabelId proto = Entities.LabelId.newBuilder().setId("label-23").build();
    byte[] bytes = proto.toByteArray();

    Parser<Entities.LabelId> parser = labelIdProtoConverter.getParser();
    Entities.LabelId parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void fieldsExistAsExpected() {
    assertThatSerializedClass(LabelId.class).hasFields(ImmutableMap.of("id", String.class));
  }
}
