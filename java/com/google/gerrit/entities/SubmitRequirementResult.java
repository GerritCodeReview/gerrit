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
import com.google.gerrit.common.Nullable;
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
   * Result of evaluating a {@link SubmitRequirement#submittabilityExpression()} on a change.
   *
   * <p>Empty if submit requirement does not apply.
   */
  public abstract Optional<SubmitRequirementExpressionResult> submittabilityExpressionResult();

  /**
   * Result of evaluating a {@link SubmitRequirement#overrideExpression()} on a change.
   *
   * <p>Empty if submit requirement does not apply, or if the submit requirement did not define an
   * override expression.
   */
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

  /**
   * Boolean indicating if the "submit requirement" was bypassed during submission, e.g. by
   * performing a push with the %submit option.
   */
  public abstract Optional<Boolean> forced();

  /**
   * Whether this result should be filtered out when returned from REST API.
   *
   * <p>This can be used by {@link
   * com.google.gerrit.server.project.OnStoreSubmitRequirementResultModifier}. It can override the
   * {@code SubmitRequirementResult} status and might want to hide the SR from the API as if it was
   * non-applicable (non-applicable SRs are currently hidden on UI).
   */
  public abstract Optional<Boolean> hidden();

  public boolean isHidden() {
    return hidden().orElse(false);
  }

  public Optional<String> errorMessage() {
    if (!status().equals(Status.ERROR)) {
      return Optional.empty();
    }
    if (applicabilityExpressionResult().isPresent()
        && applicabilityExpressionResult().get().errorMessage().isPresent()) {
      return Optional.of(
          "Applicability expression result has an error: "
              + applicabilityExpressionResult().get().errorMessage().get());
    }
    if (submittabilityExpressionResult().isPresent()
        && submittabilityExpressionResult().get().errorMessage().isPresent()) {
      return Optional.of(
          "Submittability expression result has an error: "
              + submittabilityExpressionResult().get().errorMessage().get());
    }
    if (overrideExpressionResult().isPresent()
        && overrideExpressionResult().get().errorMessage().isPresent()) {
      return Optional.of(
          "Override expression result has an error: "
              + overrideExpressionResult().get().errorMessage().get());
    }
    return Optional.of("No error logged.");
  }

  @Memoized
  public Status status() {
    if (forced().orElse(false)) {
      return Status.FORCED;
    } else if (assertError(submittabilityExpressionResult())
        || assertError(applicabilityExpressionResult())
        || assertError(overrideExpressionResult())) {
      return Status.ERROR;
    } else if (assertFail(applicabilityExpressionResult())) {
      return Status.NOT_APPLICABLE;
    } else if (assertPass(overrideExpressionResult())) {
      return Status.OVERRIDDEN;
    } else if (assertPass(submittabilityExpressionResult())) {
      return Status.SATISFIED;
    } else {
      return Status.UNSATISFIED;
    }
  }

  /** Returns true if the submit requirement is fulfilled and can allow change submission. */
  @Memoized
  public boolean fulfilled() {
    Status s = status();
    return s == Status.SATISFIED
        || s == Status.OVERRIDDEN
        || s == Status.NOT_APPLICABLE
        || s == Status.FORCED;
  }

  public static Builder builder() {
    return new AutoValue_SubmitRequirementResult.Builder();
  }

  public abstract Builder toBuilder();

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
     * Any of the applicability, submittability or override expressions contain invalid syntax and
     * are not parsable.
     */
    ERROR,

    /**
     * The "submit requirement" was bypassed during submission, e.g. by pushing for review with the
     * %submit option.
     */
    FORCED
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder submitRequirement(SubmitRequirement submitRequirement);

    public abstract Builder applicabilityExpressionResult(
        Optional<SubmitRequirementExpressionResult> value);

    public abstract Builder submittabilityExpressionResult(
        Optional<SubmitRequirementExpressionResult> value);

    public abstract Builder submittabilityExpressionResult(
        @Nullable SubmitRequirementExpressionResult value);

    public abstract Builder overrideExpressionResult(
        Optional<SubmitRequirementExpressionResult> value);

    public abstract Builder patchSetCommitId(ObjectId value);

    public abstract Builder legacy(Optional<Boolean> value);

    public abstract Builder forced(Optional<Boolean> value);

    public abstract Builder hidden(Optional<Boolean> value);

    public abstract SubmitRequirementResult build();
  }

  public static boolean assertPass(Optional<SubmitRequirementExpressionResult> expressionResult) {
    return assertStatus(expressionResult, SubmitRequirementExpressionResult.Status.PASS);
  }

  public static boolean assertFail(Optional<SubmitRequirementExpressionResult> expressionResult) {
    return assertStatus(expressionResult, SubmitRequirementExpressionResult.Status.FAIL);
  }

  public static boolean assertError(Optional<SubmitRequirementExpressionResult> expressionResult) {
    return assertStatus(expressionResult, SubmitRequirementExpressionResult.Status.ERROR);
  }

  private static boolean assertStatus(
      SubmitRequirementExpressionResult expressionResult,
      SubmitRequirementExpressionResult.Status status) {
    return expressionResult.status() == status;
  }

  private static boolean assertStatus(
      Optional<SubmitRequirementExpressionResult> expressionResult,
      SubmitRequirementExpressionResult.Status status) {
    return expressionResult.isPresent() && assertStatus(expressionResult.get(), status);
  }
}
