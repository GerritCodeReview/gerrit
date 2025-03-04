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
import static com.google.gerrit.server.cache.serialize.entities.SubmitRequirementExpressionResultSerializer.deserialize;
import static com.google.gerrit.server.cache.serialize.entities.SubmitRequirementExpressionResultSerializer.serialize;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.Status;
import com.google.gerrit.server.cache.proto.Cache.SubmitRequirementExpressionResultProto;
import java.util.Optional;
import org.junit.Test;

public class SubmitRequirementExpressionResultSerializerTest {
  private static final SubmitRequirementExpressionResult r1 =
      SubmitRequirementExpressionResult.create(
          SubmitRequirementExpression.create("label:Code-Review=+2"),
          Status.PASS,
          ImmutableList.of("Label:Code-Review=+2"),
          ImmutableList.of());

  private static final SubmitRequirementExpressionResult r2 =
      SubmitRequirementExpressionResult.create(
          SubmitRequirementExpression.create("label:Code-Review=+2"),
          Status.ERROR,
          ImmutableList.of(),
          ImmutableList.of(),
          Optional.empty(),
          Optional.of("Failed to parse the code review label"));

  @Test
  public void roundTrip_withoutError() throws Exception {
    assertThat(deserialize(serialize(r1))).isEqualTo(r1);
  }

  @Test
  public void roundTrip_withErrorMessage() throws Exception {
    assertThat(deserialize(serialize(r2))).isEqualTo(r2);
  }

  @Test
  public void deserializeUnknownStatus() throws Exception {
    SubmitRequirementExpressionResultProto proto =
        serialize(r1).toBuilder().setStatus("unknown").build();
    assertThat(deserialize(proto).status())
        .isEqualTo(SubmitRequirementExpressionResult.Status.ERROR);
  }
}
