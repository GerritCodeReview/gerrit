// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SimilarityDecisionFunctionTest {
  // These are not the requirements of isSimilar method. The test cases are
  // here only to check the classifier is producing meaning results.
  @Rule public final Expect expect = Expect.create();

  @Test
  public void isSimilar_SVM_Similar() {
    expect.that(SimilarityDecisionFunction.INSTANCE.isSimilar(1.0, 1.0))
        .isTrue();
    expect.that(SimilarityDecisionFunction.INSTANCE.isSimilar(0.7, 0.7))
        .isTrue();
    expect.that(SimilarityDecisionFunction.INSTANCE.isSimilar(0.2, 0.7))
        .isTrue();
  }

  @Test
  public void isSimilar_SVM_NotSimilar() {
    expect.that(SimilarityDecisionFunction.INSTANCE.isSimilar(0.0, 0.0))
        .isFalse();
    expect.that(SimilarityDecisionFunction.INSTANCE.isSimilar(0.2, 0.2))
        .isFalse();
    expect.that(SimilarityDecisionFunction.INSTANCE.isSimilar(0.7, 0.2))
        .isFalse();
  }
}
