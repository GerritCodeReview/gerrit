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
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.protobuf.Parser;

public enum BranchNameKeyProtoConverter
    implements ProtoConverter<Reviewdb.Branch_NameKey, Branch.NameKey> {
  INSTANCE;

  private final ProtoConverter<Reviewdb.Project_NameKey, Project.NameKey> projectNameConverter =
      ProjectNameKeyProtoConverter.INSTANCE;

  @Override
  public Reviewdb.Branch_NameKey toProto(Branch.NameKey nameKey) {
    return Reviewdb.Branch_NameKey.newBuilder()
        .setProjectName(projectNameConverter.toProto(nameKey.getParentKey()))
        .setBranchName(nameKey.get())
        .build();
  }

  @Override
  public Branch.NameKey fromProto(Reviewdb.Branch_NameKey proto) {
    return new Branch.NameKey(
        projectNameConverter.fromProto(proto.getProjectName()), proto.getBranchName());
  }

  @Override
  public Parser<Reviewdb.Branch_NameKey> getParser() {
    return Reviewdb.Branch_NameKey.parser();
  }
}
