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
import com.google.gerrit.proto.reviewdb.Reviewdb;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.protobuf.Parser;
import java.lang.reflect.Type;
import org.junit.Test;

public class RevIdProtoConverterTest {
  private final RevIdProtoConverter revIdProtoConverter = RevIdProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    RevId revId = new RevId("9903402f303249e");

    Reviewdb.RevId proto = revIdProtoConverter.toProto(revId);

    Reviewdb.RevId expectedProto = Reviewdb.RevId.newBuilder().setId("9903402f303249e").build();
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
    Reviewdb.RevId proto = Reviewdb.RevId.newBuilder().setId("9903402f303249e").build();
    byte[] bytes = proto.toByteArray();

    Parser<Reviewdb.RevId> parser = revIdProtoConverter.getParser();
    Reviewdb.RevId parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void fieldsExistAsExpected() {
    assertThatSerializedClass(RevId.class)
        .hasFields(ImmutableMap.<String, Type>builder().put("id", String.class).build());
  }
}
