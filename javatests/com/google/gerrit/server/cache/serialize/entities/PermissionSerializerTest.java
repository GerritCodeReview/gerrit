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
import static com.google.gerrit.server.cache.serialize.entities.PermissionSerializer.deserialize;
import static com.google.gerrit.server.cache.serialize.entities.PermissionSerializer.serialize;

import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.entities.GroupReference;
import org.junit.Test;

public class PermissionSerializerTest {
  static final Permission ALL_VALUES_SET =
      Permission.builder(Permission.ABANDON)
          .setExclusiveGroup(true)
          .add(PermissionRule.builder(GroupReference.create("group")))
          .build();

  @Test
  public void roundTrip() {
    assertThat(deserialize(serialize(ALL_VALUES_SET))).isEqualTo(ALL_VALUES_SET);
  }

  @Test
  public void roundTripWithMinimalValues() {
    Permission permission = Permission.builder(Permission.ABANDON).build();
    assertThat(deserialize(serialize(permission))).isEqualTo(permission);
  }
}
