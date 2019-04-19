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
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.protobuf.Parser;
import java.lang.reflect.Type;
import org.junit.Test;

public class ChangeMessageKeyProtoConverterTest {
  private final ChangeMessageKeyProtoConverter messageKeyProtoConverter =
      ChangeMessageKeyProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    ChangeMessage.Key messageKey = ChangeMessage.key(Change.id(704), "aabbcc");

    Entities.ChangeMessage_Key proto = messageKeyProtoConverter.toProto(messageKey);

    Entities.ChangeMessage_Key expectedProto =
        Entities.ChangeMessage_Key.newBuilder()
            .setChangeId(Entities.Change_Id.newBuilder().setId(704))
            .setUuid("aabbcc")
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    ChangeMessage.Key messageKey = ChangeMessage.key(Change.id(704), "aabbcc");

    ChangeMessage.Key convertedMessageKey =
        messageKeyProtoConverter.fromProto(messageKeyProtoConverter.toProto(messageKey));

    assertThat(convertedMessageKey).isEqualTo(messageKey);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Entities.ChangeMessage_Key proto =
        Entities.ChangeMessage_Key.newBuilder()
            .setChangeId(Entities.Change_Id.newBuilder().setId(704))
            .setUuid("aabbcc")
            .build();
    byte[] bytes = proto.toByteArray();

    Parser<Entities.ChangeMessage_Key> parser = messageKeyProtoConverter.getParser();
    Entities.ChangeMessage_Key parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void methodsExistAsExpected() {
    assertThatSerializedClass(ChangeMessage.Key.class)
        .hasAutoValueMethods(
            ImmutableMap.<String, Type>builder()
                .put("changeId", Change.Id.class)
                .put("uuid", String.class)
                .build());
  }
}
