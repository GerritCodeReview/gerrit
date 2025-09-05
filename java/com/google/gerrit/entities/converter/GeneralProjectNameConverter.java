// Copyright (C) 2025 The Android Open Source Project
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

import com.google.gerrit.entities.GeneralProjectName;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.Entities.Project_NameKey;
import com.google.protobuf.Parser;

public enum GeneralProjectNameConverter
    implements SafeProtoConverter<Entities.Project_NameKey, GeneralProjectName> {
  INSTANCE;

  @Override
  public Project_NameKey toProto(GeneralProjectName nameKey) {
    return Entities.Project_NameKey.newBuilder().setName(nameKey.get()).build();
  }

  @Override
  public GeneralProjectName fromProto(Project_NameKey proto) {
    return new GeneralProjectName(proto.getName());
  }

  @Override
  public Class<Project_NameKey> getProtoClass() {
    return Project_NameKey.class;
  }

  @Override
  public Class<GeneralProjectName> getEntityClass() {
    return GeneralProjectName.class;
  }

  @Override
  public Parser<Project_NameKey> getParser() {
    return Project_NameKey.parser();
  }
}
