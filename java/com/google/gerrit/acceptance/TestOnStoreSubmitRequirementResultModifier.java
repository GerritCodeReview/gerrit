// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.server.project.OnStoreSubmitRequirementResultModifier;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.ChangeContext;
import java.util.Optional;

/** Implementation of {@link OnStoreSubmitRequirementResultModifier} that is used in tests. */
public class TestOnStoreSubmitRequirementResultModifier
    implements OnStoreSubmitRequirementResultModifier {

  private ModificationStrategy modificationStrategy = ModificationStrategy.KEEP;

  private boolean hide = false;

  /**
   * The strategy, used by this modifier to transform {@link SubmitRequirementResult} on {@link
   * OnStoreSubmitRequirementResultModifier#modifyResultOnStore} invocations.
   */
  public enum ModificationStrategy {
    KEEP,
    FAIL,
    PASS,
    OVERRIDE
  }

  public void setModificationStrategy(ModificationStrategy modificationStrategy) {
    this.modificationStrategy = modificationStrategy;
  }

  public void hide(boolean hide) {
    this.hide = hide;
  }

  @Override
  public SubmitRequirementResult modifyResultOnStore(
      SubmitRequirement submitRequirement,
      SubmitRequirementResult result,
      ChangeData cd,
      ChangeContext ctx) {
    if (modificationStrategy.equals(ModificationStrategy.KEEP)) {
      return result;
    }
    SubmitRequirementResult.Builder srResultBuilder = result.toBuilder().hidden(Optional.of(hide));
    if (modificationStrategy.equals(ModificationStrategy.OVERRIDE)) {
      return srResultBuilder
          .overrideExpressionResult(
              Optional.of(
                  SubmitRequirementExpressionResult.create(
                      submitRequirement.submittabilityExpression(),
                      SubmitRequirementExpressionResult.Status.PASS,
                      ImmutableList.of(),
                      ImmutableList.of(
                          submitRequirement.submittabilityExpression().expressionString()))))
          .build();
    }
    return srResultBuilder
        .submittabilityExpressionResult(
            SubmitRequirementExpressionResult.create(
                submitRequirement.submittabilityExpression(),
                modificationStrategy.equals(ModificationStrategy.FAIL)
                    ? SubmitRequirementExpressionResult.Status.FAIL
                    : SubmitRequirementExpressionResult.Status.PASS,
                ImmutableList.of(),
                ImmutableList.of(submitRequirement.submittabilityExpression().expressionString())))
        .build();
  }
}
