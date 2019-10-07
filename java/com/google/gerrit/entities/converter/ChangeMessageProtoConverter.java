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

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.proto.Entities;
import com.google.protobuf.Parser;
import java.sql.Timestamp;
import java.util.Objects;

@Immutable
public enum ChangeMessageProtoConverter
    implements ProtoConverter<Entities.ChangeMessage, ChangeMessage> {
  INSTANCE;

  private final ProtoConverter<Entities.ChangeMessage_Key, ChangeMessage.Key>
      changeMessageKeyConverter = ChangeMessageKeyProtoConverter.INSTANCE;
  private final ProtoConverter<Entities.Account_Id, Account.Id> accountIdConverter =
      AccountIdProtoConverter.INSTANCE;
  private final ProtoConverter<Entities.PatchSet_Id, PatchSet.Id> patchSetIdConverter =
      PatchSetIdProtoConverter.INSTANCE;

  @Override
  public Entities.ChangeMessage toProto(ChangeMessage changeMessage) {
    Entities.ChangeMessage.Builder builder =
        Entities.ChangeMessage.newBuilder()
            .setKey(changeMessageKeyConverter.toProto(changeMessage.getKey()));
    Account.Id author = changeMessage.getAuthor();
    if (author != null) {
      builder.setAuthorId(accountIdConverter.toProto(author));
    }
    Timestamp writtenOn = changeMessage.getWrittenOn();
    if (writtenOn != null) {
      builder.setWrittenOn(writtenOn.getTime());
    }
    String message = changeMessage.getMessage();
    if (message != null) {
      builder.setMessage(message);
    }
    PatchSet.Id patchSetId = changeMessage.getPatchSetId();
    if (patchSetId != null) {
      builder.setPatchset(patchSetIdConverter.toProto(patchSetId));
    }
    String tag = changeMessage.getTag();
    if (tag != null) {
      builder.setTag(tag);
    }
    Account.Id realAuthor = changeMessage.getRealAuthor();
    // ChangeMessage#getRealAuthor automatically delegates to ChangeMessage#getAuthor if the real
    // author is not set. However, the previous protobuf representation kept 'realAuthor' empty if
    // it wasn't set. To ensure binary compatibility, simulate the previous behavior.
    if (realAuthor != null && !Objects.equals(realAuthor, author)) {
      builder.setRealAuthor(accountIdConverter.toProto(realAuthor));
    }
    return builder.build();
  }

  @Override
  public ChangeMessage fromProto(Entities.ChangeMessage proto) {
    ChangeMessage.Key key =
        proto.hasKey() ? changeMessageKeyConverter.fromProto(proto.getKey()) : null;
    Account.Id author =
        proto.hasAuthorId() ? accountIdConverter.fromProto(proto.getAuthorId()) : null;
    Timestamp writtenOn = proto.hasWrittenOn() ? new Timestamp(proto.getWrittenOn()) : null;
    PatchSet.Id patchSetId =
        proto.hasPatchset() ? patchSetIdConverter.fromProto(proto.getPatchset()) : null;
    ChangeMessage changeMessage = new ChangeMessage(key, author, writtenOn, patchSetId);
    if (proto.hasMessage()) {
      changeMessage.setMessage(proto.getMessage());
    }
    if (proto.hasTag()) {
      changeMessage.setTag(proto.getTag());
    }
    if (proto.hasRealAuthor()) {
      changeMessage.setRealAuthor(accountIdConverter.fromProto(proto.getRealAuthor()));
    }
    return changeMessage;
  }

  @Override
  public Parser<Entities.ChangeMessage> getParser() {
    return Entities.ChangeMessage.parser();
  }
}
