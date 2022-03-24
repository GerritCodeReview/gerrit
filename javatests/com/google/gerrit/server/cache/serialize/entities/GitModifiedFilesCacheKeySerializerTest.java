// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.cache.serialize.entities;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCacheKey;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCacheKey.Serializer;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class GitModifiedFilesCacheKeySerializerTest {
  private static final ObjectId TREE_ID_1 =
      ObjectId.fromString("123e9fa8a286255ac7d5ba11b598892735758391");
  private static final ObjectId TREE_ID_2 =
      ObjectId.fromString("d07a03a9818c120301cb5b4a969b035479400b5f");

  @Test
  public void roundTrip() {
    GitModifiedFilesCacheKey key =
        GitModifiedFilesCacheKey.builder()
            .project(Project.NameKey.parse("Project/X"))
            .aTree(TREE_ID_1)
            .bTree(TREE_ID_2)
            .renameScore(65)
            .build();
    byte[] serialized = Serializer.INSTANCE.serialize(key);
    assertThat(Serializer.INSTANCE.deserialize(serialized)).isEqualTo(key);
  }
}
