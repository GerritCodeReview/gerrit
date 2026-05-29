// Copyright (C) 2025 The Android Open Source Project
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
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

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
public abstract class PredicateResult {
  abstract ImmutableList<PredicateResult> childPredicateResults();

  /** We only set this field for leaf predicates. */
  public abstract String predicateString();

  /** true if the predicate is passing for a given change. */
  public abstract boolean status();

  /**
   * An explanation of the predicate result.
   *
   * <p>This is used to provide more information about complex atoms, which may otherwise be opaque
   * and hard to debug.
   *
   * <p>This will be empty for most predicate results and all non-leaf predicates.
   */
  public abstract String explanation();

  /** Returns a list of leaf predicate results whose {@link PredicateResult#status()} is true. */
  public ImmutableList<String> getPassingAtoms() {
    return getAtoms(/* status= */ true).stream()
        .map(PredicateResult::predicateString)
        .collect(ImmutableList.toImmutableList());
  }

  /** Returns a list of leaf predicate results whose {@link PredicateResult#status()} is false. */
  public ImmutableList<String> getFailingAtoms() {
    return getAtoms(/* status= */ false).stream()
        .map(PredicateResult::predicateString)
        .collect(ImmutableList.toImmutableList());
  }

  public ImmutableMap<String, String> getAtomExplanations() {
    return getAtoms().stream()
        .collect(
            ImmutableMap.toImmutableMap(
                PredicateResult::predicateString, PredicateResult::explanation, (a, b) -> a));
  }

  /** Returns the list of leaf {@link PredicateResult}. */
  private ImmutableList<PredicateResult> getAtoms() {
    ImmutableList.Builder<PredicateResult> atomsList = ImmutableList.builder();
    getAtomsRecursively(atomsList);
    return atomsList.build();
  }

  /**
   * Returns the list of leaf {@link PredicateResult} whose {@link #status()} is equal to the {@code
   * status} parameter.
   */
  private ImmutableList<PredicateResult> getAtoms(boolean status) {
    ImmutableList.Builder<PredicateResult> atomsList = ImmutableList.builder();
    getAtomsRecursively(atomsList, status);
    return atomsList.build();
  }

  private void getAtomsRecursively(ImmutableList.Builder<PredicateResult> list) {
    if (!predicateString().isEmpty()) {
      list.add(this);
      return;
    }
    childPredicateResults().forEach(c -> c.getAtomsRecursively(list));
  }

  private void getAtomsRecursively(ImmutableList.Builder<PredicateResult> list, boolean status) {
    if (!predicateString().isEmpty() && status() == status) {
      list.add(this);
      return;
    }
    childPredicateResults().forEach(c -> c.getAtomsRecursively(list, status));
  }

  public static Builder builder() {
    return new AutoValue_PredicateResult.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    protected abstract ImmutableList.Builder<PredicateResult> childPredicateResultsBuilder();

    public abstract Builder predicateString(String value);

    public abstract Builder status(boolean value);

    public abstract Builder explanation(String value);

    @CanIgnoreReturnValue
    public Builder addChildPredicateResult(PredicateResult result) {
      childPredicateResultsBuilder().add(result);
      return this;
    }

    public abstract PredicateResult build();
  }
}
