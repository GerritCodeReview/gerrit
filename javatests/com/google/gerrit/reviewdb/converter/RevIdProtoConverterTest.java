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
import com.google.gerrit.proto.reviewdb.Entities;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.protobuf.Parser;
import org.junit.Test;

public class RevIdProtoConverterTest {
  private final RevIdProtoConverter revIdProtoConverter = RevIdProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    RevId revId = new RevId("9903402f303249e");

    Entities.RevId proto = revIdProtoConverter.toProto(revId);

    Entities.RevId expectedProto = Entities.RevId.newBuilder().setId("9903402f303249e").build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    RevId revId = new RevId("ff3934a320bb");

    RevId convertedRevId = revIdProtoConverter.fromProto(revIdProtoConverter.toProto(revId));

    assertThat(convertedRevId).isEqualTo(revId);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Entities.RevId proto = Entities.RevId.newBuilder().setId("9903402f303249e").build();
    byte[] bytes = proto.toByteArray();

    Parser<Entities.RevId> parser = revIdProtoConverter.getParser();
    Entities.RevId parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void fieldsExistAsExpected() {
    assertThatSerializedClass(RevId.class).hasFields(ImmutableMap.of("id", String.class));
  }
}
