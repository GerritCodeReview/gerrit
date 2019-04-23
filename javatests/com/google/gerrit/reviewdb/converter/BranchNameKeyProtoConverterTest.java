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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.testing.SerializedClassSubject;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.protobuf.Parser;
import java.lang.reflect.Type;
import org.junit.Test;

public class BranchNameKeyProtoConverterTest {
  private final BranchNameKeyProtoConverter branchNameKeyProtoConverter =
      BranchNameKeyProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    Branch.NameKey nameKey = Branch.nameKey(Project.nameKey("project-13"), "branch-72");

    Entities.Branch_NameKey proto = branchNameKeyProtoConverter.toProto(nameKey);

    Entities.Branch_NameKey expectedProto =
        Entities.Branch_NameKey.newBuilder()
            .setProject(Entities.Project_NameKey.newBuilder().setName("project-13"))
            .setBranch("refs/heads/branch-72")
            .build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    Branch.NameKey nameKey = Branch.nameKey(Project.nameKey("project-52"), "branch 14");

    Branch.NameKey convertedNameKey =
        branchNameKeyProtoConverter.fromProto(branchNameKeyProtoConverter.toProto(nameKey));

    assertThat(convertedNameKey).isEqualTo(nameKey);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Entities.Branch_NameKey proto =
        Entities.Branch_NameKey.newBuilder()
            .setProject(Entities.Project_NameKey.newBuilder().setName("project 1"))
            .setBranch("branch 36")
            .build();
    byte[] bytes = proto.toByteArray();

    Parser<Entities.Branch_NameKey> parser = branchNameKeyProtoConverter.getParser();
    Entities.Branch_NameKey parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void methodsExistAsExpected() {
    assertThatSerializedClass(Branch.NameKey.class)
        .hasAutoValueMethods(
            ImmutableMap.<String, Type>builder()
                .put("project", Project.NameKey.class)
                .put("branch", String.class)
                .build());
  }
}
