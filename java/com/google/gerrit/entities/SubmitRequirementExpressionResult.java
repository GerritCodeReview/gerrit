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
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import java.util.Optional;

/** Result of evaluating a submit requirement expression on a given Change. */
@AutoValue
public abstract class SubmitRequirementExpressionResult {

  /** Submit requirement expression for which this result is evaluated. */
  public abstract SubmitRequirementExpression expression();

  /** Status of evaluation. */
  public abstract Status status();

  /**
   * Optional error message. Populated if the evaluator fails to evaluate the expression for a
   * certain change.
   */
  public abstract Optional<String> errorMessage();

  /**
   * List leaf predicates that are fulfilled, for example the expression
   *
   * <p><i>label:Code-Review=+2 and branch:refs/heads/master</i>
   *
   * <p>has two leaf predicates:
   *
   * <ul>
   *   <li>label:Code-Review=+2
   *   <li>branch:refs/heads/master
   * </ul>
   *
   * This method will return the leaf predicates that were fulfilled, for example if only the first
   * predicate was fulfilled, the returned list will be equal to ["label:Code-Review=+2"].
   */
  public abstract ImmutableList<String> passingAtoms();

  /**
   * List of leaf predicates that are not fulfilled. See {@link #passingAtoms()} for more details.
   */
  public abstract ImmutableList<String> failingAtoms();

  public static SubmitRequirementExpressionResult create(
      SubmitRequirementExpression expression, PredicateResult predicateResult) {
    return create(
        expression,
        predicateResult.status() ? Status.PASS : Status.FAIL,
        predicateResult.getPassingAtoms(),
        predicateResult.getFailingAtoms());
  }

  public static SubmitRequirementExpressionResult create(
      SubmitRequirementExpression expression,
      Status status,
      ImmutableList<String> passingAtoms,
      ImmutableList<String> failingAtoms) {
    return create(expression, status, passingAtoms, failingAtoms, Optional.empty());
  }

  public static SubmitRequirementExpressionResult create(
      SubmitRequirementExpression expression,
      Status status,
      ImmutableList<String> passingAtoms,
      ImmutableList<String> failingAtoms,
      Optional<String> errorMessage) {
    return new AutoValue_SubmitRequirementExpressionResult(
        expression, status, errorMessage, passingAtoms, failingAtoms);
  }

  public static SubmitRequirementExpressionResult error(
      SubmitRequirementExpression expression, String errorMessage) {
    return new AutoValue_SubmitRequirementExpressionResult(
        expression,
        Status.ERROR,
        Optional.of(errorMessage),
        ImmutableList.of(),
        ImmutableList.of());
  }

  public static SubmitRequirementExpressionResult notEvaluated(SubmitRequirementExpression expr) {
    return SubmitRequirementExpressionResult.create(
        expr, Status.NOT_EVALUATED, ImmutableList.of(), ImmutableList.of());
  }

  public static TypeAdapter<SubmitRequirementExpressionResult> typeAdapter(Gson gson) {
    return new AutoValue_SubmitRequirementExpressionResult.GsonTypeAdapter(gson);
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder expression(SubmitRequirementExpression expression);

    public abstract Builder status(Status status);

    public abstract Builder errorMessage(Optional<String> errorMessage);

    public abstract Builder passingAtoms(ImmutableList<String> passingAtoms);

    public abstract Builder failingAtoms(ImmutableList<String> failingAtoms);

    public abstract SubmitRequirementExpressionResult build();
  }

  public enum Status {
    /** Submit requirement expression is fulfilled for a given change. */
    PASS,

    /** Submit requirement expression is failing for a given change. */
    FAIL,

    /** Submit requirement expression contains invalid syntax and is not parsable. */
    ERROR,

    /** Submit requirement expression was not evaluated. */
    NOT_EVALUATED
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

    /** We only set this field for leaf predicates. */
    public abstract String predicateString();

    /** true if the predicate is passing for a given change. */
    abstract boolean status();

    /** Returns a list of leaf predicate results whose {@link PredicateResult#status()} is true. */
    ImmutableList<String> getPassingAtoms() {
      return getAtoms(/* status= */ true).stream()
          .map(PredicateResult::predicateString)
          .collect(ImmutableList.toImmutableList());
    }

    /** Returns a list of leaf predicate results whose {@link PredicateResult#status()} is false. */
    ImmutableList<String> getFailingAtoms() {
      return getAtoms(/* status= */ false).stream()
          .map(PredicateResult::predicateString)
          .collect(ImmutableList.toImmutableList());
    }

    /**
     * Returns the list of leaf {@link PredicateResult} whose {@link #status()} is equal to the
     * {@code status} parameter.
     */
    private ImmutableList<PredicateResult> getAtoms(boolean status) {
      ImmutableList.Builder<PredicateResult> atomsList = ImmutableList.builder();
      getAtomsRecursively(atomsList, status);
      return atomsList.build();
    }

    private void getAtomsRecursively(ImmutableList.Builder<PredicateResult> list, boolean status) {
      if (!predicateString().isEmpty() && status() == status) {
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
