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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.Patch.FileMode;
import com.google.gerrit.entities.Patch.PatchType;
import com.google.gerrit.server.patch.filediff.Edit;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiff;
import com.google.gerrit.server.patch.gitfilediff.GitFileDiff.Serializer;
import java.util.Optional;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class GitFileDiffSerializerTest {
  private static final ObjectId OLD_ID =
      ObjectId.fromString("123e9fa8a286255ac7d5ba11b598892735758391");
  private static final ObjectId NEW_ID =
      ObjectId.fromString("d07a03a9818c120301cb5b4a969b035479400b5f");

  @Test
  public void roundTrip() {
    ImmutableList<Edit> edits =
        ImmutableList.of(Edit.create(1, 5, 3, 4), Edit.create(21, 30, 150, 158));

    GitFileDiff gitFileDiff =
        GitFileDiff.builder()
            .edits(edits)
            .fileHeader("file_header")
            .oldPath(Optional.of("old_file_path.txt"))
            .newPath(Optional.empty())
            .oldId(AbbreviatedObjectId.fromObjectId(OLD_ID))
            .newId(AbbreviatedObjectId.fromObjectId(NEW_ID))
            .changeType(ChangeType.DELETED)
            .patchType(Optional.of(PatchType.UNIFIED))
            .oldMode(Optional.of(FileMode.REGULAR_FILE))
            .newMode(Optional.of(FileMode.REGULAR_FILE))
            .build();

    byte[] serialized = Serializer.INSTANCE.serialize(gitFileDiff);
    assertThat(Serializer.INSTANCE.deserialize(serialized)).isEqualTo(gitFileDiff);
  }
}
