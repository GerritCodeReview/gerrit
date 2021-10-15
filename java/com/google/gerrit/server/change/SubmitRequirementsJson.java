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

package com.google.gerrit.server.change;

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.extensions.common.SubmitRequirementExpressionInfo;
import com.google.gerrit.extensions.common.SubmitRequirementResultInfo;
import com.google.inject.Singleton;

/**
 * Produces submit requirements related entities like {@link SubmitRequirementResultInfo}s, which
 * are serialized to JSON afterwards.
 */
@Singleton
public class SubmitRequirementsJson {
  public SubmitRequirementResultInfo toInfo(SubmitRequirement req, SubmitRequirementResult result) {
    SubmitRequirementResultInfo info = new SubmitRequirementResultInfo();
    info.name = req.name();
    info.description = req.description().orElse(null);
    if (req.applicabilityExpression().isPresent()) {
      info.applicabilityExpressionResult =
          submitRequirementExpressionToInfo(
              req.applicabilityExpression().get(), result.applicabilityExpressionResult().get());
    }
    if (req.overrideExpression().isPresent()) {
      info.overrideExpressionResult =
          submitRequirementExpressionToInfo(
              req.overrideExpression().get(), result.overrideExpressionResult().get());
    }
    info.submittabilityExpressionResult =
        submitRequirementExpressionToInfo(
            req.submittabilityExpression(), result.submittabilityExpressionResult());
    info.status = SubmitRequirementResultInfo.Status.valueOf(result.status().toString());
    info.isLegacy = result.legacy();
    return info;
  }

  private static SubmitRequirementExpressionInfo submitRequirementExpressionToInfo(
      SubmitRequirementExpression expression, SubmitRequirementExpressionResult result) {
    SubmitRequirementExpressionInfo info = new SubmitRequirementExpressionInfo();
    info.expression = expression.expressionString();
    info.fulfilled = result.status().equals(SubmitRequirementExpressionResult.Status.PASS);
    info.passingAtoms = result.passingAtoms();
    info.failingAtoms = result.failingAtoms();
    return info;
  }
}
