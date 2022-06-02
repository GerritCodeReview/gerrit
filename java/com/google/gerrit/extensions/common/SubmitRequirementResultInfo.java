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

package com.google.gerrit.extensions.common;

import com.google.gerrit.common.Nullable;
import java.util.Objects;

/** Result of evaluating a submit requirement on a change. */
public class SubmitRequirementResultInfo {
  public enum Status {
    /** Submit requirement is fulfilled. */
    SATISFIED,

    /**
     * Submit requirement is not satisfied. Happens when {@code submittabilityExpressionResult} is
     * not fulfilled.
     */
    UNSATISFIED,

    /**
     * Submit requirement is overridden. Happens when {@code overrideExpressionResult} is fulfilled.
     */
    OVERRIDDEN,

    /**
     * Submit requirement is not applicable for the change. Happens when {@code
     * applicabilityExpressionResult} is not fulfilled.
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

  /** Submit requirement name. */
  public String name;

  /** Submit requirement description. */
  public String description;

  /** Overall result (status) of evaluating this submit requirement. */
  public Status status;

  /** Whether this result was created from a legacy submit record. */
  public boolean isLegacy;

  /** Result of evaluating the applicability expression. */
  @Nullable public SubmitRequirementExpressionInfo applicabilityExpressionResult;

  /** Result of evaluating the submittability expression. */
  @Nullable public SubmitRequirementExpressionInfo submittabilityExpressionResult;

  /** Result of evaluating the override expression. */
  @Nullable public SubmitRequirementExpressionInfo overrideExpressionResult;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SubmitRequirementResultInfo)) {
      return false;
    }
    SubmitRequirementResultInfo that = (SubmitRequirementResultInfo) o;
    return isLegacy == that.isLegacy
        && Objects.equals(name, that.name)
        && Objects.equals(description, that.description)
        && status == that.status
        && Objects.equals(applicabilityExpressionResult, that.applicabilityExpressionResult)
        && Objects.equals(submittabilityExpressionResult, that.submittabilityExpressionResult)
        && Objects.equals(overrideExpressionResult, that.overrideExpressionResult);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        name,
        description,
        status,
        isLegacy,
        applicabilityExpressionResult,
        submittabilityExpressionResult,
        overrideExpressionResult);
  }
}
