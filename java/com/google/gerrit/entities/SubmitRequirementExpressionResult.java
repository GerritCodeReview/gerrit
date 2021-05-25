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
import com.google.common.collect.ImmutableList;
import java.util.Optional;

/** Result of evaluating a submit requirement expression on a given Change. */
@AutoValue
public abstract class SubmitRequirementExpressionResult {

  /**
   * Entity detailing the result of evaluating a Submit requirement expression. Contains an empty
   * {@link Optional} if {@link #status()} is equal to {@link Status#ERROR}.
   */
  public abstract Optional<PredicateResult> predicateResult();

  public abstract Optional<String> errorMessage();

  public Status status() {
    if (predicateResult().isPresent()) {
      return predicateResult().get().status() ? Status.PASS : Status.FAIL;
    }
    return Status.ERROR;
  }

  public static SubmitRequirementExpressionResult create(PredicateResult predicateResult) {
    return new AutoValue_SubmitRequirementExpressionResult(
        Optional.of(predicateResult), Optional.empty());
  }

  public static SubmitRequirementExpressionResult error(String errorMessage) {
    return new AutoValue_SubmitRequirementExpressionResult(
        Optional.empty(), Optional.of(errorMessage));
  }

  /**
   * Returns a list of leaf predicate results whose {@link PredicateResult#status()} is true. If
   * {@link #status()} is equal to {@link Status#ERROR}, an empty list is returned.
   */
  public ImmutableList<PredicateResult> getPassingAtoms() {
    if (predicateResult().isPresent()) {
      return predicateResult().get().getAtoms(/* status= */ true);
    }
    return ImmutableList.of();
  }

  /**
   * Returns a list of leaf predicate results whose {@link PredicateResult#status()} is false. If
   * {@link #status()} is equal to {@link Status#ERROR}, an empty list is returned.
   */
  public ImmutableList<PredicateResult> getFailingAtoms() {
    if (predicateResult().isPresent()) {
      return predicateResult().get().getAtoms(/* status= */ false);
    }
    return ImmutableList.of();
  }

  public enum Status {
    /** Submit requirement expression is fulfilled for a given change. */
    PASS,

    /** Submit requirement expression is failing for a given change. */
    FAIL,

    /** Submit requirement expression contains invalid syntax and is not parsable. */
    ERROR
  }

  /**
   * Entity detailing the result of evaluating a predicate.
   *
   * <p>Example - branch:refs/heads/foo and has:unresolved
   *
   * <p>The above predicate is an "And" predicate having two child predicates:
   *
   * <ul>
   *   <li>branch:refs/heads/foo
   *   <li>has:unresolved
   * </ul>
   *
   * <p>Each child predicate as well as the parent contains the result of its evaluation.
   */
  @AutoValue
  public abstract static class PredicateResult {
    abstract ImmutableList<PredicateResult> childPredicateResults();

    abstract String predicateString();

    /** true if the predicate is passing for a given change. */
    abstract boolean status();

    /**
     * Returns the list of leaf {@link PredicateResult} whose {@link #status()} is equal to the
     * {@code status} parameter.
     */
    ImmutableList<PredicateResult> getAtoms(boolean status) {
      ImmutableList.Builder<PredicateResult> atomsList = ImmutableList.builder();
      getAtomsRecursively(atomsList, status);
      return atomsList.build();
    }

    private void getAtomsRecursively(ImmutableList.Builder<PredicateResult> list, boolean status) {
      if (childPredicateResults().isEmpty() && status() == status) {
        list.add(this);
        return;
      }
      childPredicateResults().forEach(c -> c.getAtomsRecursively(list, status));
    }

    public static Builder builder() {
      return new AutoValue_SubmitRequirementExpressionResult_PredicateResult.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder childPredicateResults(ImmutableList<PredicateResult> value);

      protected abstract ImmutableList.Builder<PredicateResult> childPredicateResultsBuilder();

      public abstract Builder predicateString(String value);

      public abstract Builder status(boolean value);

      public Builder addChildPredicateResult(PredicateResult result) {
        childPredicateResultsBuilder().add(result);
        return this;
      }

      public abstract PredicateResult build();
    }
  }
}
