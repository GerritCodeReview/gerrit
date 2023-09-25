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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import com.google.gerrit.server.util.AccountTemplateUtil;
import java.lang.reflect.Type;
import java.time.Instant;
import org.junit.Test;

public class ChangeMessageProtoConverterTest {
  private final ChangeMessageProtoConverter changeMessageProtoConverter =
      ChangeMessageProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    ChangeMessage changeMessage =
        ChangeMessage.create(
            ChangeMessage.key(Change.id(543, "project"), "change-message-21"),
            Account.id(63),
            Instant.ofEpochMilli(9876543),
            PatchSet.id(Change.id(34, "project"), 13),
            "This is a change message.",
            Account.id(10003),
            "An arbitrary tag.");

    Entities.ChangeMessage proto = changeMessageProtoConverter.toProto(changeMessage);

    Entities.ChangeMessage expectedProto =
        Entities.ChangeMessage.newBuilder()
            .setKey(
                Entities.ChangeMessage_Key.newBuilder()
                    .setChangeId(
                        Entities.Change_Id.newBuilder().setId(543).setProjectName("project"))
                    .setUuid("change-message-21"))
            .setAuthorId(Entities.Account_Id.newBuilder().setId(63))
            .setWrittenOn(9876543)
            .setMessage("This is a change message.")
            .setPatchset(
                Entities.PatchSet_Id.newBuilder()
                    .setChangeId(
                        Entities.Change_Id.newBuilder().setId(34).setProjectName("project"))
                    .setId(13))
            .setTag("An arbitrary tag.")
            .setRealAuthor(Entities.Account_Id.newBuilder().setId(10003))
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void mainValuesConvertedToProto() {
    ChangeMessage changeMessage =
        ChangeMessage.create(
            ChangeMessage.key(Change.id(543, "project"), "change-message-21"),
            Account.id(63),
            Instant.ofEpochMilli(9876543),
            PatchSet.id(Change.id(34, "project"), 13));

    Entities.ChangeMessage proto = changeMessageProtoConverter.toProto(changeMessage);

    Entities.ChangeMessage expectedProto =
        Entities.ChangeMessage.newBuilder()
            .setKey(
                Entities.ChangeMessage_Key.newBuilder()
                    .setChangeId(
                        Entities.Change_Id.newBuilder().setId(543).setProjectName("project"))
                    .setUuid("change-message-21"))
            .setAuthorId(Entities.Account_Id.newBuilder().setId(63))
            .setWrittenOn(9876543)
            .setPatchset(
                Entities.PatchSet_Id.newBuilder()
                    .setChangeId(
                        Entities.Change_Id.newBuilder().setId(34).setProjectName("project"))
                    .setId(13))
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  // This test documents a special behavior which is necessary to ensure binary compatibility.
  @Test
  public void realAuthorIsNotAutomaticallySetToAuthorWhenConvertedToProto() {
    ChangeMessage changeMessage =
        ChangeMessage.create(
            ChangeMessage.key(Change.id(543, "project"), "change-message-21"),
            Account.id(63),
            null,
            null);

    Entities.ChangeMessage proto = changeMessageProtoConverter.toProto(changeMessage);

    Entities.ChangeMessage expectedProto =
        Entities.ChangeMessage.newBuilder()
            .setKey(
                Entities.ChangeMessage_Key.newBuilder()
                    .setChangeId(
                        Entities.Change_Id.newBuilder().setId(543).setProjectName("project"))
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
        ChangeMessage.create(
            ChangeMessage.key(Change.id(543, "project"), "change-message-21"), null, null, null);

    Entities.ChangeMessage proto = changeMessageProtoConverter.toProto(changeMessage);

    Entities.ChangeMessage expectedProto =
        Entities.ChangeMessage.newBuilder()
            .setKey(
                Entities.ChangeMessage_Key.newBuilder()
                    .setChangeId(
                        Entities.Change_Id.newBuilder().setId(543).setProjectName("project"))
                    .setUuid("change-message-21"))
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    ChangeMessage changeMessage =
        ChangeMessage.create(
            ChangeMessage.key(Change.id(543, "project"), "change-message-21"),
            Account.id(63),
            Instant.ofEpochMilli(9876543),
            PatchSet.id(Change.id(34, "project"), 13),
            "This is a change message.",
            Account.id(10003),
            "An arbitrary tag.");

    ChangeMessage convertedChangeMessage =
        changeMessageProtoConverter.fromProto(changeMessageProtoConverter.toProto(changeMessage));
    assertThat(convertedChangeMessage).isEqualTo(changeMessage);
  }

  @Test
  public void messageTemplateConvertedToProtoAndParsedBack() {
    ChangeMessage changeMessage =
        ChangeMessage.create(
            ChangeMessage.key(Change.id(543, "project"), "change-message-21"),
            Account.id(63),
            Instant.ofEpochMilli(9876543),
            PatchSet.id(Change.id(34, "project"), 13),
            String.format(
                "This is a change message by %s and includes %s ",
                AccountTemplateUtil.getAccountTemplate(Account.id(10001)),
                AccountTemplateUtil.getAccountTemplate(Account.id(10002))),
            Account.id(10003),
            "An arbitrary tag.");

    ChangeMessage convertedChangeMessage =
        changeMessageProtoConverter.fromProto(changeMessageProtoConverter.toProto(changeMessage));

    assertThat(convertedChangeMessage).isEqualTo(changeMessage);
  }

  @Test
  public void mainValuesConvertedToProtoAndBackAgain() {
    ChangeMessage changeMessage =
        ChangeMessage.create(
            ChangeMessage.key(Change.id(543, "project"), "change-message-21"),
            Account.id(63),
            Instant.ofEpochMilli(9876543),
            PatchSet.id(Change.id(34, "project"), 13));

    ChangeMessage convertedChangeMessage =
        changeMessageProtoConverter.fromProto(changeMessageProtoConverter.toProto(changeMessage));
    assertThat(convertedChangeMessage).isEqualTo(changeMessage);
  }

  @Test
  public void mandatoryValuesConvertedToProtoAndBackAgain() {
    ChangeMessage changeMessage =
        ChangeMessage.create(
            ChangeMessage.key(Change.id(543, "project"), "change-message-21"), null, null, null);

    ChangeMessage convertedChangeMessage =
        changeMessageProtoConverter.fromProto(changeMessageProtoConverter.toProto(changeMessage));
    assertThat(convertedChangeMessage).isEqualTo(changeMessage);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void fieldsExistAsExpected() {
    assertThatSerializedClass(ChangeMessage.class)
        .hasFields(
            ImmutableMap.<String, Type>builder()
                .put("key", ChangeMessage.Key.class)
                .put("author", Account.Id.class)
                .put("writtenOn", Instant.class)
                .put("message", String.class)
                .put("patchset", PatchSet.Id.class)
                .put("tag", String.class)
                .put("realAuthor", Account.Id.class)
                .build());
  }
}
