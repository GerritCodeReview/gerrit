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

package com.google.gerrit.server.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static com.google.gerrit.proto.testing.SerializedClassSubject.assertThatSerializedClass;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.proto.Cache.DagTagSetHolderProto;
import org.junit.Test;

public class DagTagSetHolderTest {
  @Test
  public void serializerWithTagSet() throws Exception {
    DagTagSetHolder holder = new DagTagSetHolder(Project.nameKey("project"));
    holder.setTagSet(new DagTagSet(holder.getProjectName()));

    byte[] serialized = DagTagSetHolder.Serializer.INSTANCE.serialize(holder);
    assertThat(DagTagSetHolderProto.parseFrom(serialized))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(
            DagTagSetHolderProto.newBuilder()
                .setProjectName("project")
                .setTags(DagTagSet.Serializer.toProto(holder.getTagSet()))
                .build());

    DagTagSetHolder deserialized = DagTagSetHolder.Serializer.INSTANCE.deserialize(serialized);
    assertThat(deserialized.getProjectName()).isEqualTo(holder.getProjectName());
    DagTagSetTest.assertEqual(holder.getTagSet(), deserialized.getTagSet());
  }

  @Test
  public void serializerWithoutTagSet() throws Exception {
    DagTagSetHolder holder = new DagTagSetHolder(Project.nameKey("project"));

    byte[] serialized = DagTagSetHolder.Serializer.INSTANCE.serialize(holder);
    assertThat(DagTagSetHolderProto.parseFrom(serialized))
        .ignoringRepeatedFieldOrder()
        .isEqualTo(DagTagSetHolderProto.newBuilder().setProjectName("project").build());

    DagTagSetHolder deserialized = DagTagSetHolder.Serializer.INSTANCE.deserialize(serialized);
    assertThat(deserialized.getProjectName()).isEqualTo(holder.getProjectName());
    DagTagSetTest.assertEqual(holder.getTagSet(), deserialized.getTagSet());
  }

  @Test
  public void fields() {
    assertThatSerializedClass(DagTagSetHolder.class)
        .hasFields(
            ImmutableMap.of(
                "buildLock", Object.class,
                "projectName", Project.NameKey.class,
                "tagSet", DagTagSet.class));
  }
}
