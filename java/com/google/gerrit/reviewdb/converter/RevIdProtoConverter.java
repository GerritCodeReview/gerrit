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

import com.google.gerrit.proto.reviewdb.Entities;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.protobuf.Parser;

public enum RevIdProtoConverter implements ProtoConverter<Entities.RevId, RevId> {
  INSTANCE;

  @Override
  public Entities.RevId toProto(RevId revId) {
    return Entities.RevId.newBuilder().setId(revId.get()).build();
  }

  @Override
  public RevId fromProto(Entities.RevId proto) {
    return new RevId(proto.getId());
  }

  @Override
  public Parser<Entities.RevId> getParser() {
    return Entities.RevId.parser();
  }
}
