// Copyright (C) 2021 The Android Open Source Project
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
import com.google.gerrit.server.patch.DiffSummary;
import com.google.gerrit.server.patch.DiffSummary.Serializer;
import org.junit.Test;

/** Serializer test for {@link com.google.gerrit.server.patch.DiffSummary}. */
public class DiffSummarySerializerTest {
  @Test
  public void roundTrip() {
    DiffSummary diffSummary =
        DiffSummary.create(
            ImmutableList.of("path_1.txt", "path_2.txt"),
            /* insertions= */ 15,
            /* deletions= */ 12);

    assertThat(Serializer.INSTANCE.deserialize(Serializer.INSTANCE.serialize(diffSummary)))
        .isEqualTo(diffSummary);
  }
}
