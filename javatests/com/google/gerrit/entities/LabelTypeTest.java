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

package com.google.gerrit.entities;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class LabelTypeTest {
  @Test
  public void sortLabelValues() {
    LabelValue v0 = LabelValue.create((short) 0, "Zero");
    LabelValue v1 = LabelValue.create((short) 1, "One");
    LabelValue v2 = LabelValue.create((short) 2, "Two");
    LabelType types = LabelType.create("Label", ImmutableList.of(v2, v0, v1));
    assertThat(types.getValues()).containsExactly(v0, v1, v2).inOrder();
  }

  @Test
  public void sortCopyValues() {
    LabelValue v0 = LabelValue.create((short) 0, "Zero");
    LabelValue v1 = LabelValue.create((short) 1, "One");
    LabelValue v2 = LabelValue.create((short) 2, "Two");
    LabelType types =
        LabelType.builder("Label", ImmutableList.of(v2, v0, v1))
            .setCopyValues(ImmutableList.of((short) 2, (short) 0, (short) 1))
            .build();
    assertThat(types.getCopyValues()).containsExactly((short) 0, (short) 1, (short) 2).inOrder();
  }

  @Test
  public void insertMissingLabelValues() {
    LabelValue v0 = LabelValue.create((short) 0, "Zero");
    LabelValue v2 = LabelValue.create((short) 2, "Two");
    LabelValue v5 = LabelValue.create((short) 5, "Five");
    LabelType types = LabelType.create("Label", ImmutableList.of(v2, v5, v0));
    assertThat(types.getValues())
        .containsExactly(
            v0,
            LabelValue.create((short) 1, ""),
            v2,
            LabelValue.create((short) 3, ""),
            LabelValue.create((short) 4, ""),
            v5)
        .inOrder();
  }
}
