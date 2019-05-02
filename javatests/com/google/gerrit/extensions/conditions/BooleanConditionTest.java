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

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.extensions.conditions.BooleanCondition.not;
import static com.google.gerrit.extensions.conditions.BooleanCondition.or;
import static com.google.gerrit.extensions.conditions.BooleanCondition.valueOf;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BooleanConditionTest {

  private static final BooleanCondition NO_TRIVIAL_EVALUATION =
      new BooleanCondition() {
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
      };

  @Test
  public void reduceAnd_CutOffNonTrivialWhenPossible() throws Exception {
    BooleanCondition nonReduced = and(false, NO_TRIVIAL_EVALUATION);
    BooleanCondition reduced = valueOf(false);
    assertEquals(nonReduced.reduce(), reduced);
  }

  @Test
  public void reduceAnd_CutOffNonTrivialWhenPossibleSwapped() throws Exception {
    BooleanCondition nonReduced = and(NO_TRIVIAL_EVALUATION, valueOf(false));
    BooleanCondition reduced = valueOf(false);
    assertEquals(nonReduced.reduce(), reduced);
  }

  @Test
  public void reduceAnd_KeepNonTrivialWhenNoCutOffPossible() throws Exception {
    BooleanCondition nonReduced = and(true, NO_TRIVIAL_EVALUATION);
    BooleanCondition reduced = and(true, NO_TRIVIAL_EVALUATION);
    assertEquals(nonReduced.reduce(), reduced);
  }

  @Test
  public void reduceAnd_KeepNonTrivialWhenNoCutOffPossibleSwapped() throws Exception {
    BooleanCondition nonReduced = and(NO_TRIVIAL_EVALUATION, valueOf(true));
    BooleanCondition reduced = and(NO_TRIVIAL_EVALUATION, valueOf(true));
    assertEquals(nonReduced.reduce(), reduced);
  }

  @Test
  public void reduceOr_CutOffNonTrivialWhenPossible() throws Exception {
    BooleanCondition nonReduced = or(true, NO_TRIVIAL_EVALUATION);
    BooleanCondition reduced = valueOf(true);
    assertEquals(nonReduced.reduce(), reduced);
  }

  @Test
  public void reduceOr_CutOffNonTrivialWhenPossibleSwapped() throws Exception {
    BooleanCondition nonReduced = or(NO_TRIVIAL_EVALUATION, valueOf(true));
    BooleanCondition reduced = valueOf(true);
    assertEquals(nonReduced.reduce(), reduced);
  }

  @Test
  public void reduceOr_KeepNonTrivialWhenNoCutOffPossible() throws Exception {
    BooleanCondition nonReduced = or(false, NO_TRIVIAL_EVALUATION);
    BooleanCondition reduced = or(false, NO_TRIVIAL_EVALUATION);
    assertEquals(nonReduced.reduce(), reduced);
  }

  @Test
  public void reduceOr_KeepNonTrivialWhenNoCutOffPossibleSwapped() throws Exception {
    BooleanCondition nonReduced = or(NO_TRIVIAL_EVALUATION, valueOf(false));
    BooleanCondition reduced = or(NO_TRIVIAL_EVALUATION, valueOf(false));
    assertEquals(nonReduced.reduce(), reduced);
  }

  @Test
  public void reduceNot_ReduceIrrelevant() throws Exception {
    BooleanCondition nonReduced = not(valueOf(true));
    BooleanCondition reduced = valueOf(false);
    assertEquals(nonReduced.reduce(), reduced);
  }

  @Test
  public void reduceNot_ReduceIrrelevant2() throws Exception {
    BooleanCondition nonReduced = not(valueOf(false));
    BooleanCondition reduced = valueOf(true);
    assertEquals(nonReduced.reduce(), reduced);
  }

  @Test
  public void reduceNot_KeepNonTrivialWhenNoCutOffPossible() throws Exception {
    BooleanCondition nonReduced = not(NO_TRIVIAL_EVALUATION);
    BooleanCondition reduced = not(NO_TRIVIAL_EVALUATION);
    assertEquals(nonReduced.reduce(), reduced);
  }

  @Test
  public void reduceComplexTreeToSingleValue() throws Exception {
    //        AND
    //       /   \
    //      OR   NOT
    //     /  \    \
    //   NTE NTE  TRUE
    BooleanCondition nonReduced =
        and(or(NO_TRIVIAL_EVALUATION, NO_TRIVIAL_EVALUATION), not(valueOf(true)));
    BooleanCondition reduced = valueOf(false);
    assertEquals(nonReduced.reduce(), reduced);
  }

  @Test
  public void reduceComplexTreeToSmallerTree() throws Exception {
    //        AND
    //       /   \
    //      OR    OR
    //     /  \   / \
    //   NTE NTE  T  F
    BooleanCondition nonReduced =
        and(or(NO_TRIVIAL_EVALUATION, NO_TRIVIAL_EVALUATION), or(valueOf(true), valueOf(false)));
    BooleanCondition reduced = and(or(NO_TRIVIAL_EVALUATION, NO_TRIVIAL_EVALUATION), valueOf(true));
    assertEquals(nonReduced.reduce(), reduced);
  }
}
