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

import com.google.gerrit.proto.Entities;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.protobuf.Parser;

public enum ChangeMessageKeyProtoConverter
    implements ProtoConverter<Entities.ChangeMessage_Key, ChangeMessage.Key> {
  INSTANCE;

  private final ProtoConverter<Entities.Change_Id, Change.Id> changeIdConverter =
      ChangeIdProtoConverter.INSTANCE;

  @Override
  public Entities.ChangeMessage_Key toProto(ChangeMessage.Key messageKey) {
    return Entities.ChangeMessage_Key.newBuilder()
        .setChangeId(changeIdConverter.toProto(messageKey.changeId()))
        .setUuid(messageKey.uuid())
        .build();
  }

  @Override
  public ChangeMessage.Key fromProto(Entities.ChangeMessage_Key proto) {
    return ChangeMessage.key(changeIdConverter.fromProto(proto.getChangeId()), proto.getUuid());
  }

  @Override
  public Parser<Entities.ChangeMessage_Key> getParser() {
    return Entities.ChangeMessage_Key.parser();
  }
}
