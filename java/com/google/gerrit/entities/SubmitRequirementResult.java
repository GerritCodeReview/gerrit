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
import com.google.auto.value.extension.memoized.Memoized;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/** Result of evaluating a {@link SubmitRequirement} on a given Change. */
@AutoValue
public abstract class SubmitRequirementResult {
  /** Submit requirement for which this result is evaluated. */
  public abstract SubmitRequirement submitRequirement();

  /** Result of evaluating a {@link SubmitRequirement#applicabilityExpression()} on a change. */
  public abstract Optional<SubmitRequirementExpressionResult> applicabilityExpressionResult();

  /**
   * Result of evaluating a {@link SubmitRequirement#submittabilityExpression()} ()} on a change.
   */
  public abstract Optional<SubmitRequirementExpressionResult> submittabilityExpressionResult();

  /** Result of evaluating a {@link SubmitRequirement#overrideExpression()} ()} on a change. */
  public abstract Optional<SubmitRequirementExpressionResult> overrideExpressionResult();

  /** SHA-1 of the patchset commit ID for which the submit requirement was evaluated. */
  public abstract ObjectId patchSetCommitId();

  /**
   * Whether this result was created from a legacy {@link SubmitRecord}, or by evaluating a {@link
   * SubmitRequirement}.
   *
   * <p>If equals {@link Optional#empty()}, we treat the result as non-legacy (false).
   */
  public abstract Optional<Boolean> legacy();

  public boolean isLegacy() {
    return legacy().orElse(false);
  }

  @Memoized
  public Status status() {
    if (assertError(submittabilityExpressionResult())
        || assertError(applicabilityExpressionResult())
        || assertError(overrideExpressionResult())) {
      return Status.ERROR;
    } else if (assertFail(applicabilityExpressionResult())) {
      return Status.NOT_APPLICABLE;
    } else if (assertPass(overrideExpressionResult())) {
      return Status.OVERRIDDEN;
    } else if (assertPass(submittabilityExpressionResult())
        || !submittabilityExpressionResult().isPresent()) {
      // Submit requirement is satisfied either if it was configured and the expression evaluation
      // was fulfilled, or if it was not configured for the project.
      return Status.SATISFIED;
    } else {
      return Status.UNSATISFIED;
    }
  }

  /** Returns true if the submit requirement is fulfilled and can allow change submission. */
  @Memoized
  public boolean fulfilled() {
    Status s = status();
    return s == Status.SATISFIED || s == Status.OVERRIDDEN || s == Status.NOT_APPLICABLE;
  }

  public static Builder builder() {
    return new AutoValue_SubmitRequirementResult.Builder();
  }

  public static TypeAdapter<SubmitRequirementResult> typeAdapter(Gson gson) {
    return new AutoValue_SubmitRequirementResult.GsonTypeAdapter(gson);
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

    /**
     * Any of the applicability, blocking or override expressions contain invalid syntax and are not
     * parsable.
     */
    ERROR
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder submitRequirement(SubmitRequirement submitRequirement);

    public abstract Builder applicabilityExpressionResult(
        Optional<SubmitRequirementExpressionResult> value);

    public abstract Builder submittabilityExpressionResult(
        Optional<SubmitRequirementExpressionResult> value);

    public abstract Builder overrideExpressionResult(
        Optional<SubmitRequirementExpressionResult> value);

    public abstract Builder patchSetCommitId(ObjectId value);

    public abstract Builder legacy(Optional<Boolean> value);

    public abstract SubmitRequirementResult build();
  }

  private boolean assertPass(Optional<SubmitRequirementExpressionResult> expressionResult) {
    return assertStatus(expressionResult, SubmitRequirementExpressionResult.Status.PASS);
  }

  private boolean assertFail(Optional<SubmitRequirementExpressionResult> expressionResult) {
    return assertStatus(expressionResult, SubmitRequirementExpressionResult.Status.FAIL);
  }

  private boolean assertError(Optional<SubmitRequirementExpressionResult> expressionResult) {
    return assertStatus(expressionResult, SubmitRequirementExpressionResult.Status.ERROR);
  }

  private boolean assertStatus(
      SubmitRequirementExpressionResult expressionResult,
      SubmitRequirementExpressionResult.Status status) {
    return expressionResult.status() == status;
  }

  private boolean assertStatus(
      Optional<SubmitRequirementExpressionResult> expressionResult,
      SubmitRequirementExpressionResult.Status status) {
    return expressionResult.isPresent() && assertStatus(expressionResult.get(), status);
  }
}
