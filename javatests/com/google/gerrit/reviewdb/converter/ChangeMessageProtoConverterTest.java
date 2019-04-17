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
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.protobuf.Parser;
import java.lang.reflect.Type;
import java.sql.Timestamp;
import org.junit.Test;

public class ChangeMessageProtoConverterTest {
  private final ChangeMessageProtoConverter changeMessageProtoConverter =
      ChangeMessageProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"),
            Account.id(63),
            new Timestamp(9876543),
            new PatchSet.Id(new Change.Id(34), 13));
    changeMessage.setMessage("This is a change message.");
    changeMessage.setTag("An arbitrary tag.");
    changeMessage.setRealAuthor(Account.id(10003));

    Entities.ChangeMessage proto = changeMessageProtoConverter.toProto(changeMessage);

    Entities.ChangeMessage expectedProto =
        Entities.ChangeMessage.newBuilder()
            .setKey(
                Entities.ChangeMessage_Key.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(543))
                    .setUuid("change-message-21"))
            .setAuthorId(Entities.Account_Id.newBuilder().setId(63))
            .setWrittenOn(9876543)
            .setMessage("This is a change message.")
            .setPatchset(
                Entities.PatchSet_Id.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(34))
                    .setPatchSetId(13))
            .setTag("An arbitrary tag.")
            .setRealAuthor(Entities.Account_Id.newBuilder().setId(10003))
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void mainValuesConvertedToProto() {
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"),
            Account.id(63),
            new Timestamp(9876543),
            new PatchSet.Id(new Change.Id(34), 13));

    Entities.ChangeMessage proto = changeMessageProtoConverter.toProto(changeMessage);

    Entities.ChangeMessage expectedProto =
        Entities.ChangeMessage.newBuilder()
            .setKey(
                Entities.ChangeMessage_Key.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(543))
                    .setUuid("change-message-21"))
            .setAuthorId(Entities.Account_Id.newBuilder().setId(63))
            .setWrittenOn(9876543)
            .setPatchset(
                Entities.PatchSet_Id.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(34))
                    .setPatchSetId(13))
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  // This test documents a special behavior which is necessary to ensure binary compatibility.
  @Test
  public void realAuthorIsNotAutomaticallySetToAuthorWhenConvertedToProto() {
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"),
            Account.id(63),
            null,
            null);

    Entities.ChangeMessage proto = changeMessageProtoConverter.toProto(changeMessage);

    Entities.ChangeMessage expectedProto =
        Entities.ChangeMessage.newBuilder()
            .setKey(
                Entities.ChangeMessage_Key.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(543))
                    .setUuid("change-message-21"))
            .setAuthorId(Entities.Account_Id.newBuilder().setId(63))
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void mandatoryValuesConvertedToProto() {
    // writtenOn may not be null according to the column definition but it's optional for the
    // protobuf definition. -> assume as optional and hence test null
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"), null, null, null);

    Entities.ChangeMessage proto = changeMessageProtoConverter.toProto(changeMessage);

    Entities.ChangeMessage expectedProto =
        Entities.ChangeMessage.newBuilder()
            .setKey(
                Entities.ChangeMessage_Key.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(543))
                    .setUuid("change-message-21"))
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"),
            Account.id(63),
            new Timestamp(9876543),
            new PatchSet.Id(new Change.Id(34), 13));
    changeMessage.setMessage("This is a change message.");
    changeMessage.setTag("An arbitrary tag.");
    changeMessage.setRealAuthor(Account.id(10003));

    ChangeMessage convertedChangeMessage =
        changeMessageProtoConverter.fromProto(changeMessageProtoConverter.toProto(changeMessage));
    assertThat(convertedChangeMessage).isEqualTo(changeMessage);
  }

  @Test
  public void mainValuesConvertedToProtoAndBackAgain() {
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"),
            Account.id(63),
            new Timestamp(9876543),
            new PatchSet.Id(new Change.Id(34), 13));

    ChangeMessage convertedChangeMessage =
        changeMessageProtoConverter.fromProto(changeMessageProtoConverter.toProto(changeMessage));
    assertThat(convertedChangeMessage).isEqualTo(changeMessage);
  }

  @Test
  public void mandatoryValuesConvertedToProtoAndBackAgain() {
    ChangeMessage changeMessage =
        new ChangeMessage(
            new ChangeMessage.Key(new Change.Id(543), "change-message-21"), null, null, null);

    ChangeMessage convertedChangeMessage =
        changeMessageProtoConverter.fromProto(changeMessageProtoConverter.toProto(changeMessage));
    assertThat(convertedChangeMessage).isEqualTo(changeMessage);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Entities.ChangeMessage proto =
        Entities.ChangeMessage.newBuilder()
            .setKey(
                Entities.ChangeMessage_Key.newBuilder()
                    .setChangeId(Entities.Change_Id.newBuilder().setId(543))
                    .setUuid("change-message-21"))
            .build();
    byte[] bytes = proto.toByteArray();

    Parser<Entities.ChangeMessage> parser = changeMessageProtoConverter.getParser();
    Entities.ChangeMessage parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void fieldsExistAsExpected() {
    assertThatSerializedClass(ChangeMessage.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("key", ChangeMessage.Key.class)
                .put("author", Account.Id.class)
                .put("writtenOn", Timestamp.class)
                .put("message", String.class)
                .put("patchset", PatchSet.Id.class)
                .put("tag", String.class)
                .put("realAuthor", Account.Id.class)
                .build());
  }
}
