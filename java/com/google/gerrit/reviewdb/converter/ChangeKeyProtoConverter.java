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
import com.google.protobuf.Parser;

public enum ChangeKeyProtoConverter implements ProtoConverter<Entities.Change_Key, Change.Key> {
  INSTANCE;

  @Override
  public Entities.Change_Key toProto(Change.Key key) {
    return Entities.Change_Key.newBuilder().setId(key.get()).build();
  }

  @Override
  public Change.Key fromProto(Entities.Change_Key proto) {
    return Change.key(proto.getId());
  }

  @Override
  public Parser<Entities.Change_Key> getParser() {
    return Entities.Change_Key.parser();
  }
}
