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
import com.google.gerrit.jgit.diff.ReplaceEdit;
import com.google.gerrit.server.patch.IntraLineDiff;
import com.google.gerrit.server.patch.IntraLineDiff.Serializer;
import com.google.gerrit.server.patch.IntraLineDiff.Status;
import java.util.List;
import org.eclipse.jgit.diff.Edit;
import org.junit.Test;

/** Serializer test for {@link com.google.gerrit.server.patch.IntraLineDiff}. */
public class IntraLineDiffSerializerTest {
  @Test
  public void roundTripWithEdits() {
    List<Edit> edits =
        ImmutableList.of(
            new Edit(1, 3, 2, 5),
            new ReplaceEdit(
                10,
                20,
                13,
                124,
                ImmutableList.of(new Edit(10, 12, 13, 16), new Edit(12, 20, 16, 24))));
    IntraLineDiff intraLineDiff = IntraLineDiff.create(edits);
    assertThat(Serializer.INSTANCE.deserialize(Serializer.INSTANCE.serialize(intraLineDiff)))
        .isEqualTo(intraLineDiff);
  }

  @Test
  public void roundTripWithAllStatusValues() {
    for (Status status : Status.values()) {
      IntraLineDiff intraLineDiff = IntraLineDiff.create(status);
      assertThat(Serializer.INSTANCE.deserialize(Serializer.INSTANCE.serialize(intraLineDiff)))
          .isEqualTo(intraLineDiff);
    }
  }
}
