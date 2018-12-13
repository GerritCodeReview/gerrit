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
    ChangeMessage.Key messageKey = new ChangeMessage.Key(new Change.Id(704), "aabbcc");

    Reviewdb.ChangeMessage_Key proto = messageKeyProtoConverter.toProto(messageKey);

    Reviewdb.ChangeMessage_Key expectedProto =
        Reviewdb.ChangeMessage_Key.newBuilder()
            .setChangeId(Reviewdb.Change_Id.newBuilder().setId(704))
            .setUuid("aabbcc")
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    ChangeMessage.Key messageKey = new ChangeMessage.Key(new Change.Id(704), "aabbcc");

    ChangeMessage.Key convertedMessageKey =
        messageKeyProtoConverter.fromProto(messageKeyProtoConverter.toProto(messageKey));

    assertThat(convertedMessageKey).isEqualTo(messageKey);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Reviewdb.ChangeMessage_Key proto =
        Reviewdb.ChangeMessage_Key.newBuilder()
            .setChangeId(Reviewdb.Change_Id.newBuilder().setId(704))
            .setUuid("aabbcc")
            .build();
    byte[] bytes = proto.toByteArray();

    Parser<Reviewdb.ChangeMessage_Key> parser = messageKeyProtoConverter.getParser();
    Reviewdb.ChangeMessage_Key parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void fieldsExistAsExpected() {
    assertThatSerializedClass(ChangeMessage.Key.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("changeId", Change.Id.class)
                .put("uuid", String.class)
                .build());
  }
}
