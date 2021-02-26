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
import com.google.gerrit.entities.Patch.PatchType;
import com.google.gerrit.server.patch.ComparisonType;
import com.google.gerrit.server.patch.filediff.Edit;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.patch.filediff.TaggedEdit;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class FileDiffOutputSerializerTest {
  @Test
  public void roundTrip() {
    ImmutableList<TaggedEdit> edits =
        ImmutableList.of(
            TaggedEdit.create(Edit.create(1, 5, 3, 4), true),
            TaggedEdit.create(Edit.create(21, 30, 150, 158), false));

    FileDiffOutput fileDiff =
        FileDiffOutput.builder()
            .oldCommitId(ObjectId.fromString("dd4d2a1498870ca5fe415b33f65d052d69d9eaf5"))
            .newCommitId(ObjectId.fromString("0cfaab3f2ba76f71798da0a2651f41be8d45f842"))
            .comparisonType(ComparisonType.againstOtherPatchSet())
            .oldPath(Optional.of("old_file_path.txt"))
            .newPath(Optional.empty())
            .changeType(ChangeType.DELETED)
            .patchType(Optional.of(PatchType.UNIFIED))
            .size(23)
            .sizeDelta(10)
            .headerLines(ImmutableList.of("header line 1", "header line 2"))
            .edits(edits)
            .build();

    byte[] serialized = FileDiffOutput.Serializer.INSTANCE.serialize(fileDiff);
    assertThat(FileDiffOutput.Serializer.INSTANCE.deserialize(serialized)).isEqualTo(fileDiff);
  }
}
