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
import com.google.gerrit.reviewdb.client.Project;
import com.google.protobuf.Parser;

public enum ProjectNameKeyProtoConverter
    implements ProtoConverter<Entities.Project_NameKey, Project.NameKey> {
  INSTANCE;

  @Override
  public Entities.Project_NameKey toProto(Project.NameKey nameKey) {
    return Entities.Project_NameKey.newBuilder().setName(nameKey.get()).build();
  }

  @Override
  public Project.NameKey fromProto(Entities.Project_NameKey proto) {
    return Project.nameKey(proto.getName());
  }

  @Override
  public Parser<Entities.Project_NameKey> getParser() {
    return Entities.Project_NameKey.parser();
  }
}
