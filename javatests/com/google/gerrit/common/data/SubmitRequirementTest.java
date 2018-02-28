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

package com.google.gerrit.common.data;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;

public class SubmitRequirementTest {
  public static final String LABEL = "Verified";
  private static final String SHORT_REASON = "Reason";
  private static final String FULL_REASON = "A longer reason.";

  @Test
  public void noLabels() {
    SubmitRequirement.Builder b = SubmitRequirement.builder();
    b.setShortReason(SHORT_REASON);
    b.setFullReason(FULL_REASON);
    SubmitRequirement reason = b.build();
    assertThat(reason.shortReason()).isEqualTo(SHORT_REASON);
    assertThat(reason.fullReason()).isEqualTo(FULL_REASON);
    assertThat(reason.label()).isNull();
  }

  @Test
  public void withLabel() {
    SubmitRequirement.Builder b = SubmitRequirement.builder();
    b.setShortReason(SHORT_REASON);
    b.setFullReason(FULL_REASON);
    b.setLabel(LABEL);
    SubmitRequirement reason = b.build();

    assertThat(reason.shortReason()).isEqualTo(SHORT_REASON);
    assertThat(reason.fullReason()).isEqualTo(FULL_REASON);
    assertThat(reason.label()).isSameAs(LABEL);
  }
}
