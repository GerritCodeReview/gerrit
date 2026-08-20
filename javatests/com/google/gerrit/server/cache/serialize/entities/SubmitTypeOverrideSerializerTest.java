// Copyright (C) 2026 The Android Open Source Project
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
import static com.google.gerrit.server.cache.serialize.entities.SubmitTypeRuleSerializer.deserialize;
import static com.google.gerrit.server.cache.serialize.entities.SubmitTypeRuleSerializer.serialize;

import com.google.gerrit.entities.SubmitTypeRule;
import com.google.gerrit.extensions.client.SubmitType;
import org.junit.Test;

public class SubmitTypeOverrideSerializerTest {
  static final SubmitTypeRule ALL_VALUES_SET =
      SubmitTypeRule.builder()
          .setType(SubmitType.MERGE_ALWAYS)
          .setApplicabilityExpression("branch:stable")
          .build();

  @Test
  public void roundTrip() {
    assertThat(deserialize(serialize(ALL_VALUES_SET))).isEqualTo(ALL_VALUES_SET);
  }

  @Test
  public void roundTripAllSubmitTypes() {
    for (SubmitType type : SubmitType.values()) {
      SubmitTypeRule override =
          SubmitTypeRule.builder().setType(type).setApplicabilityExpression("is:open").build();
      assertThat(deserialize(serialize(override))).isEqualTo(override);
    }
  }
}
