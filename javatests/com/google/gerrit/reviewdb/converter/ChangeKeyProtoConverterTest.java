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
import com.google.gerrit.reviewdb.client.Change;
import com.google.protobuf.Parser;
import org.junit.Test;

public class ChangeKeyProtoConverterTest {
  private final ChangeKeyProtoConverter changeKeyProtoConverter = ChangeKeyProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    Change.Key changeKey = Change.key("change-1");

    Entities.Change_Key proto = changeKeyProtoConverter.toProto(changeKey);

    Entities.Change_Key expectedProto = Entities.Change_Key.newBuilder().setId("change-1").build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    Change.Key changeKey = Change.key("change-52");

    Change.Key convertedChangeKey =
        changeKeyProtoConverter.fromProto(changeKeyProtoConverter.toProto(changeKey));

    assertThat(convertedChangeKey).isEqualTo(changeKey);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Entities.Change_Key proto = Entities.Change_Key.newBuilder().setId("change 36").build();
    byte[] bytes = proto.toByteArray();

    Parser<Entities.Change_Key> parser = changeKeyProtoConverter.getParser();
    Entities.Change_Key parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void methodsExistAsExpected() {
    assertThatSerializedClass(Change.Key.class)
        .hasAutoValueMethods(ImmutableMap.of("key", String.class));
  }
}
