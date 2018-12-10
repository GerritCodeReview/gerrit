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

import com.google.gerrit.proto.reviewdb.Reviewdb;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.protobuf.Parser;

public enum ChangeMessageKeyProtoConverter
    implements ProtoConverter<Reviewdb.ChangeMessage_Key, ChangeMessage.Key> {
  INSTANCE;

  private ProtoConverter<Reviewdb.Change_Id, Change.Id> changeIdConverter =
      ChangeIdProtoConverter.INSTANCE;

  @Override
  public Reviewdb.ChangeMessage_Key toProto(ChangeMessage.Key messageKey) {
    return Reviewdb.ChangeMessage_Key.newBuilder()
        .setChangeId(changeIdConverter.toProto(messageKey.getParentKey()))
        .setUuid(messageKey.get())
        .build();
  }

  @Override
  public ChangeMessage.Key fromProto(Reviewdb.ChangeMessage_Key proto) {
    return new ChangeMessage.Key(changeIdConverter.fromProto(proto.getChangeId()), proto.getUuid());
  }

  @Override
  public Parser<Reviewdb.ChangeMessage_Key> getParser() {
    return Reviewdb.ChangeMessage_Key.parser();
  }
}
