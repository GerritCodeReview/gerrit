//  Copyright (C) 2020 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.google.gerrit.server.cache.serialize.entities;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCacheImpl.ValueSerializer;
import com.google.gerrit.server.patch.gitdiff.ModifiedFile;
import java.util.Optional;
import org.junit.Test;

public class ModifiedFilesSerializerTest {
  @Test
  public void roundTrip() {
    ImmutableList.Builder<ModifiedFile> builder = ImmutableList.builder();

    builder.add(
        ModifiedFile.builder()
            .oldPath(Optional.of("file_1.txt"))
            .newPath(Optional.of("file_2.txt"))
            .build());
    builder.add(
        ModifiedFile.builder()
            .changeType(Optional.of(ChangeType.ADDED))
            .oldPath(Optional.empty())
            .newPath(Optional.of("file_3.txt"))
            .build());

    // TODO(ghareeb): the serializer cannot differentiate between an empty optional vs. an
    // optional with empty string.

    ImmutableList<ModifiedFile> modifiedFiles = builder.build();

    byte[] serialized = ValueSerializer.INSTANCE.serialize(modifiedFiles);

    assertThat(ValueSerializer.INSTANCE.deserialize(serialized)).isEqualTo(modifiedFiles);
  }
}
