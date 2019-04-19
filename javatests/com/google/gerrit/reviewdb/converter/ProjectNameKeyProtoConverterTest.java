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
import com.google.gerrit.reviewdb.client.Project;
import com.google.protobuf.Parser;
import org.junit.Test;

public class ProjectNameKeyProtoConverterTest {
  private final ProjectNameKeyProtoConverter projectNameKeyProtoConverter =
      ProjectNameKeyProtoConverter.INSTANCE;

  @Test
  public void allValuesConvertedToProto() {
    Project.NameKey nameKey = Project.nameKey("project-72");

    Entities.Project_NameKey proto = projectNameKeyProtoConverter.toProto(nameKey);

    Entities.Project_NameKey expectedProto =
        Entities.Project_NameKey.newBuilder().setName("project-72").build();
    assertThat(proto).isEqualTo(expectedProto);
  }

  @Test
  public void allValuesConvertedToProtoAndBackAgain() {
    Project.NameKey nameKey = Project.nameKey("project-52");

    Project.NameKey convertedNameKey =
        projectNameKeyProtoConverter.fromProto(projectNameKeyProtoConverter.toProto(nameKey));

    assertThat(convertedNameKey).isEqualTo(nameKey);
  }

  @Test
  public void protoCanBeParsedFromBytes() throws Exception {
    Entities.Project_NameKey proto =
        Entities.Project_NameKey.newBuilder().setName("project 36").build();
    byte[] bytes = proto.toByteArray();

    Parser<Entities.Project_NameKey> parser = projectNameKeyProtoConverter.getParser();
    Entities.Project_NameKey parsedProto = parser.parseFrom(bytes);

    assertThat(parsedProto).isEqualTo(proto);
  }

  /** See {@link SerializedClassSubject} for background and what to do if this test fails. */
  @Test
  public void fieldsExistAsExpected() {
    assertThatSerializedClass(Project.NameKey.class)
        .hasFields(ImmutableMap.of("name", String.class));
  }
}
