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

public enum ChangeIdProtoConverter implements ProtoConverter<Reviewdb.Change_Id, Change.Id> {
  INSTANCE;

  @Override
  public Reviewdb.Change_Id toProto(Change.Id changeId) {
    return Reviewdb.Change_Id.newBuilder().setId(changeId.get()).build();
  }

  @Override
  public Change.Id fromProto(Reviewdb.Change_Id proto) {
    return new Change.Id(proto.getId());
  }

  @Override
  public Parser<Reviewdb.Change_Id> getParser() {
    return Reviewdb.Change_Id.parser();
  }
}
