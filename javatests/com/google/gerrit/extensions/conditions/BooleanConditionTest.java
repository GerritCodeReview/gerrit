// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.extensions.conditions;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.extensions.conditions.BooleanCondition.not;
import static com.google.gerrit.extensions.conditions.BooleanCondition.or;
import static com.google.gerrit.extensions.conditions.BooleanCondition.valueOf;

import org.junit.Test;

public class BooleanConditionTest {

  private static NoTrivialEvaluation NO_TRIVIAL_EVALUATION = new NoTrivialEvaluation();

  private static class NoTrivialEvaluation extends BooleanCondition {
    @Override
    public boolean value() {
      throw new UnsupportedOperationException("value() is not supported");
    }

    @Override
    public <T> Iterable<T> children(Class<T> type) {
      throw new UnsupportedOperationException("children(Class<T> type) is not supported");
    }

    @Override
    public BooleanCondition reduce() {
      return this;
    }

    @Override
    protected boolean evaluatesTrivially() {
      return false;
    }
  }

  @Test
  public void reduceAnd() throws Exception {
    assertThat(and(false, NO_TRIVIAL_EVALUATION).reduce()).isEqualTo(valueOf(false));
    assertThat(and(NO_TRIVIAL_EVALUATION, valueOf(false)).reduce()).isEqualTo(valueOf(false));
    assertThat(and(true, NO_TRIVIAL_EVALUATION).reduce())
        .isEqualTo(and(true, NO_TRIVIAL_EVALUATION));
  }

  @Test
  public void reduceOr() throws Exception {
    assertThat(or(true, NO_TRIVIAL_EVALUATION).reduce()).isEqualTo(valueOf(true));
    assertThat(or(NO_TRIVIAL_EVALUATION, valueOf(true)).reduce()).isEqualTo(valueOf(true));
    assertThat(or(false, NO_TRIVIAL_EVALUATION).reduce())
        .isEqualTo(or(false, NO_TRIVIAL_EVALUATION));
  }

  @Test
  public void reduceNot() throws Exception {
    assertThat(not(valueOf(true)).reduce()).isEqualTo(valueOf(false));
    assertThat(not(valueOf(false)).reduce()).isEqualTo(valueOf(true));
    assertThat(not(NO_TRIVIAL_EVALUATION).reduce()).isEqualTo(not(NO_TRIVIAL_EVALUATION));
  }

  @Test
  public void reduceComplexTreeToSingleValue() throws Exception {
    //        AND
    //       /   \
    //      OR   NOT
    //     /  \    \
    //   NTE NTE  TRUE
    assertThat(and(or(NO_TRIVIAL_EVALUATION, NO_TRIVIAL_EVALUATION), not(valueOf(true))).reduce())
        .isEqualTo(valueOf(false));
  }

  @Test
  public void reduceComplexTreeToSmallerTree() throws Exception {
    //        AND
    //       /   \
    //      OR    OR
    //     /  \   / \
    //   NTE NTE  T  F
    assertThat(
            and(or(NO_TRIVIAL_EVALUATION, NO_TRIVIAL_EVALUATION), or(valueOf(true), valueOf(false)))
                .reduce())
        .isEqualTo(and(or(NO_TRIVIAL_EVALUATION, NO_TRIVIAL_EVALUATION), valueOf(true)));
  }
}
