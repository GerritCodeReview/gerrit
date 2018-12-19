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
import com.google.protobuf.Parser;

public enum ChangeKeyProtoConverter implements ProtoConverter<Reviewdb.Change_Key, Change.Key> {
  INSTANCE;

  @Override
  public Reviewdb.Change_Key toProto(Change.Key key) {
    return Reviewdb.Change_Key.newBuilder().setId(key.get()).build();
  }

  @Override
  public Change.Key fromProto(Reviewdb.Change_Key proto) {
    return new Change.Key(proto.getId());
  }

  @Override
  public Parser<Reviewdb.Change_Key> getParser() {
    return Reviewdb.Change_Key.parser();
  }
}
