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
    ERROR
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
  public SubmitRequirementExpressionInfo applicabilityExpressionResult;

  /** Result of evaluating the submittability expression. */
  public SubmitRequirementExpressionInfo submittabilityExpressionResult;

  /** Result of evaluating the override expression. */
  public SubmitRequirementExpressionInfo overrideExpressionResult;
}
