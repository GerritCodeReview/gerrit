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
import static com.google.gerrit.server.cache.serialize.entities.SubmitRequirementSerializer.deserialize;
import static com.google.gerrit.server.cache.serialize.entities.SubmitRequirementSerializer.serialize;

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import java.util.Optional;
import org.junit.Test;

public class SubmitRequirementSerializerTest {
  private static final SubmitRequirement submitReq =
      SubmitRequirement.builder()
          .setName("Code-Review")
          .setDescription(Optional.of("require code review +2"))
          .setApplicabilityExpression(SubmitRequirementExpression.of("branch(refs/heads/master)"))
          .setSubmittabilityExpression(SubmitRequirementExpression.create("label(code-review, 2+)"))
          .setOverrideExpression(Optional.empty())
          .setAllowOverrideInChildProjects(true)
          .setHideApplicabilityExpression(Optional.empty())
          .build();

  private static final SubmitRequirement submitReqHideApplicabilityExprTrue =
      SubmitRequirement.builder()
          .setName("Code-Review")
          .setDescription(Optional.of("require code review +2"))
          .setSubmittabilityExpression(SubmitRequirementExpression.create("label(code-review, 2+)"))
          .setAllowOverrideInChildProjects(true)
          .setHideApplicabilityExpression(Optional.of(true))
          .build();

  @Test
  public void roundTrip() {
    assertThat(deserialize(serialize(submitReq))).isEqualTo(submitReq);
  }

  @Test
  public void roundTrip_hideApplicabilityExprTrue() {
    assertThat(deserialize(serialize(submitReqHideApplicabilityExprTrue)))
        .isEqualTo(submitReqHideApplicabilityExprTrue);
  }
}
