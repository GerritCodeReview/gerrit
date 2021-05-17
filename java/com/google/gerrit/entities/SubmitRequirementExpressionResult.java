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
import java.util.List;

/** Result of evaluating a submit requirement expression on a given Change. */
@AutoValue
public abstract class SubmitRequirementExpressionResult {

  /** Entity detailing the result of evaluating a Submit requirement expression. */
  public abstract PredicateResult predicateResult();

  /** true if the submit requirement expression is passing for a given change. */
  public boolean status() {
    return predicateResult().status();
  }

  public static Builder builder() {
    return new AutoValue_SubmitRequirementExpressionResult.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder predicateResult(PredicateResult value);

    public abstract SubmitRequirementExpressionResult build();
  }

  public List<PredicateResult> getPassingAtoms() {
    ImmutableList.Builder<PredicateResult> passingAtoms = ImmutableList.builder();
    predicateResult().getAtoms(passingAtoms, true);
    return passingAtoms.build();
  }

  public List<PredicateResult> getFailingAtoms() {
    ImmutableList.Builder<PredicateResult> failingAtoms = ImmutableList.builder();
    predicateResult().getAtoms(failingAtoms, false);
    return failingAtoms.build();
  }

  @AutoValue
  public abstract static class PredicateResult {
    abstract ImmutableList<PredicateResult> childPredicates();

    abstract String predicateString();

    abstract boolean status();

    void getAtoms(ImmutableList.Builder<PredicateResult> list, boolean status) {
      if (childPredicates().isEmpty()) {
        if (status() == status) {
          list.add(this);
          return;
        }
      }
      childPredicates().forEach(c -> c.getAtoms(list, status));
    }

    public static Builder builder() {
      return new AutoValue_SubmitRequirementExpressionResult_PredicateResult.Builder();
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder childPredicates(ImmutableList<PredicateResult> value);

      protected abstract ImmutableList.Builder<PredicateResult> childPredicatesBuilder();

      public abstract Builder predicateString(String value);

      public abstract Builder status(boolean value);

      public abstract PredicateResult build();

      public Builder addChildPredicate(PredicateResult result) {
        childPredicatesBuilder().add(result);
        return this;
      }
    }
  }
}
