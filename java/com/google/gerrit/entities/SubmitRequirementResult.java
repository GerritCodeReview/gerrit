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

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import java.util.Optional;

/** Result of evaluating a {@link SubmitRequirement} on a given Change. */
@AutoValue
public abstract class SubmitRequirementResult {
  /** Result of evaluating a {@link SubmitRequirement#applicabilityExpression()} on a change. */
  public abstract Optional<SubmitRequirementExpressionResult> applicabilityExpressionResult();

  /**
   * Result of evaluating a {@link SubmitRequirement#submittabilityExpression()} ()} on a change.
   */
  public abstract SubmitRequirementExpressionResult submittabilityExpressionResult();

  /** Result of evaluating a {@link SubmitRequirement#overrideExpression()} ()} on a change. */
  public abstract Optional<SubmitRequirementExpressionResult> overrideExpressionResult();

  public abstract Status status();

  public static Builder builder() {
    return new AutoValue_SubmitRequirementResult.Builder();
  }

  public enum Status {
    /** Submit requirement is fulfilled. */
    SATISFIED,

    /**
     * Submit requirement is not satisfied. Happens when {@link
     * SubmitRequirement#submittabilityExpression()} evaluates to false.
     */
    UNSATISFIED,

    /**
     * Submit requirement is overridden. Happens when {@link SubmitRequirement#overrideExpression()}
     * evaluates to true.
     */
    OVERRIDDEN,

    /**
     * Submit requirement is not applicable for a given change. Happens when {@link
     * SubmitRequirement#applicabilityExpression()} evaluates to false.
     */
    NOT_APPLICABLE,
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder applicabilityExpressionResult(
        Optional<SubmitRequirementExpressionResult> value);

    public abstract Builder submittabilityExpressionResult(SubmitRequirementExpressionResult value);

    public abstract Builder overrideExpressionResult(
        Optional<SubmitRequirementExpressionResult> value);

    public abstract Builder status(Status value);

    public abstract SubmitRequirementResult build();
  }
}
