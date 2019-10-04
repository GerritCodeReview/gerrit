// Copyright (C) 2015 The Android Open Source Project
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

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class PatchSetApprovalTest {
  @Test
  public void keyEquality() {
    PatchSetApproval.Key k1 =
        PatchSetApproval.key(
            PatchSet.id(Change.id(1), 2), Account.id(3), LabelId.create("My-Label"));
    PatchSetApproval.Key k2 =
        PatchSetApproval.key(
            PatchSet.id(Change.id(1), 2), Account.id(3), LabelId.create("My-Label"));
    PatchSetApproval.Key k3 =
        PatchSetApproval.key(
            PatchSet.id(Change.id(1), 2), Account.id(3), LabelId.create("Other-Label"));

    assertThat(k2).isEqualTo(k1);
    assertThat(k3).isNotEqualTo(k1);
    assertThat(k2.hashCode()).isEqualTo(k1.hashCode());
    assertThat(k3.hashCode()).isNotEqualTo(k1.hashCode());

    Map<PatchSetApproval.Key, String> map = new HashMap<>();
    map.put(k1, "k1");
    map.put(k2, "k2");
    map.put(k3, "k3");
    assertThat(map).containsKey(k1);
    assertThat(map).containsKey(k2);
    assertThat(map).containsKey(k3);
    assertThat(map).containsEntry(k1, "k2");
    assertThat(map).containsEntry(k2, "k2");
    assertThat(map).containsEntry(k3, "k3");
  }
}
