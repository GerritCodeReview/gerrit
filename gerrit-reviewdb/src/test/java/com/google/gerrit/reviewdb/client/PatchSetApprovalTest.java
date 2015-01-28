// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.reviewdb.client;

import static com.google.common.truth.Truth.assertThat;

import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.server.StandardKeyEncoder;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class PatchSetApprovalTest {
  static {
    KeyUtil.setEncoderImpl(new StandardKeyEncoder());
  }

  @Test
  public void keyEquality() {
    PatchSetApproval.Key k1 = new PatchSetApproval.Key(
        new PatchSet.Id(new Change.Id(1), 2),
        new Account.Id(3),
        new LabelId("My-Label"));
    PatchSetApproval.Key k2 = new PatchSetApproval.Key(
        new PatchSet.Id(new Change.Id(1), 2),
        new Account.Id(3),
        new LabelId("My-Label"));

    assertThat(k2).isEqualTo(k1);
    assertThat(k2.hashCode()).isEqualTo(k1.hashCode());

    Map<PatchSetApproval.Key, String> map = new HashMap<>();
    map.put(k1, "k1");
    map.put(k2, "k2");

    assertThat(map).containsKey(k1);
    assertThat(map).containsKey(k2);
    assertThat(map).containsEntry(k1, "k2");
    assertThat(map).containsEntry(k2, "k2");
  }
}
