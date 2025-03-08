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

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of evaluating a single submit requirement expression. This API entity is populated from
 * {@link com.google.gerrit.entities.SubmitRequirementExpressionResult}.
 */
public class SubmitRequirementExpressionInfo {

  /** Submit requirement expression as a String. */
  public String expression;

  /** A boolean indicating if the expression is fulfilled on a change. */
  public boolean fulfilled;

  /** A status indicating if the expression is fulfilled, non-fulfilled or not evaluated. */
  public Status status;

  /**
   * A list of all atoms that are passing, for example query "branch:refs/heads/foo and project:bar"
   * has two atoms: ["branch:refs/heads/foo", "project:bar"].
   */
  public List<String> passingAtoms;

  /**
   * A list of all atoms that are failing, for example query "branch:refs/heads/foo and project:bar"
   * has two atoms: ["branch:refs/heads/foo", "project:bar"].
   */
  public List<String> failingAtoms;

  /**
   * Map of leaf predicates to their explanations.
   *
   * <p>This is used to provide more information about complex atoms, which may otherwise be opaque
   * and hard to debug.
   *
   * <p>This will only be populated/implemented for some atoms.
   */
  public Map<String, String> atomExplanations;

  /**
   * Optional error message. Contains an explanation of why the submit requirement expression failed
   * during its evaluation.
   */
  public String errorMessage;

  /**
   * Values in this enum should match with values in {@link
   * com.google.gerrit.entities.SubmitRequirementExpressionResult.Status}.
   */
  public enum Status {
    /** Expression was evaluated and the result was true. */
    PASS,

    /** Expression was evaluated and the result was false. */
    FAIL,

    /** An error occurred while evaluating the expression. */
    ERROR,

    /** Expression was not evaluated. */
    NOT_EVALUATED
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SubmitRequirementExpressionInfo)) {
      return false;
    }
    SubmitRequirementExpressionInfo that = (SubmitRequirementExpressionInfo) o;
    return fulfilled == that.fulfilled
        && Objects.equals(expression, that.expression)
        && Objects.equals(passingAtoms, that.passingAtoms)
        && Objects.equals(failingAtoms, that.failingAtoms)
        && Objects.equals(atomExplanations, that.atomExplanations)
        && Objects.equals(errorMessage, that.errorMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        expression, fulfilled, passingAtoms, failingAtoms, atomExplanations, errorMessage);
  }
}
