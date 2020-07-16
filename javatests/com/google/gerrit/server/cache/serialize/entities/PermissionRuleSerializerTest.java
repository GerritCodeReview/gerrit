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
import static com.google.gerrit.server.cache.serialize.entities.PermissionRuleSerializer.deserialize;
import static com.google.gerrit.server.cache.serialize.entities.PermissionRuleSerializer.serialize;

import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.PermissionRule;
import org.junit.Test;

public class PermissionRuleSerializerTest {
  @Test
  public void roundTrip() {
    PermissionRule permissionRuleAutoValue =
        PermissionRule.builder(GroupReference.create("name"))
            .setAction(PermissionRule.Action.BATCH)
            .setForce(true)
            .setMax(321)
            .setMin(123)
            .build();
    assertThat(deserialize(serialize(permissionRuleAutoValue))).isEqualTo(permissionRuleAutoValue);
  }

  @Test
  public void roundTripWithMinimalValues() {
    PermissionRule permissionRuleAutoValue = PermissionRule.create(GroupReference.create("name"));
    assertThat(deserialize(serialize(permissionRuleAutoValue))).isEqualTo(permissionRuleAutoValue);
  }
}
