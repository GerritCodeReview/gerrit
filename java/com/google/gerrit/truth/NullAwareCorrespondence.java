// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.truth;

import com.google.common.base.Function;
import com.google.common.truth.Correspondence;
import java.util.Optional;

/** Utility class for constructing null aware {@link Correspondence}s. */
public class NullAwareCorrespondence {
  /**
   * Constructs a {@link Correspondence} that compares elements by transforming the actual elements
   * using the given function and testing for equality with the expected elements.
   *
   * <p>If the actual element is null, it will correspond to a null expected element. This is
   * different to {@link Correspondence#transforming(Function, String)} which would invoke the
   * function with a {@code null} argument, requiring the function being able to handle {@code
   * null}.
   *
   * @param actualTransform a {@link Function} taking an actual value and returning a new value
   *     which will be compared with an expected value to determine whether they correspond
   * @param description should fill the gap in a failure message of the form {@code "not true that
   *     <some actual element> is an element that <description> <some expected element>"}, e.g.
   *     {@code "has an ID of"}
   */
  public static <A, E> Correspondence<A, E> transforming(
      Function<A, ? extends E> actualTransform, String description) {
    return Correspondence.transforming(
        actualValue -> Optional.ofNullable(actualValue).map(actualTransform).orElse(null),
        description);
  }

  /**
   * Constructs a {@link Correspondence} that compares elements by transforming the actual elements
   * using the given function and testing for equality with the expected elements.
   *
   * <p>If the actual element is null, it will correspond to a null expected element. This is
   * different to {@link Correspondence#transforming(Function, Function, String)} which would invoke
   * the function with a {@code null} argument, requiring the function being able to handle {@code
   * null}.
   *
   * <p>If the expected element is null, it will correspond to a new null expected element. This is
   * different to {@link Correspondence#transforming(Function, Function, String)} which would invoke
   * the function with a {@code null} argument, requiring the function being able to handle {@code
   * null}.
   *
   * @param actualTransform a {@link Function} taking an actual value and returning a new value
   *     which will be compared with an expected value to determine whether they correspond
   * @param expectedTransform a {@link Function} taking an expected value and returning a new value
   *     which will be compared with a transformed actual value
   * @param description should fill the gap in a failure message of the form {@code "not true that
   *     <some actual element> is an element that <description> <some expected element>"}, e.g.
   *     {@code "has an ID of"}
   */
  public static <A, E> Correspondence<A, E> transforming(
      Function<A, ? extends E> actualTransform,
      Function<E, ?> expectedTransform,
      String description) {
    return Correspondence.transforming(
        actualValue -> Optional.ofNullable(actualValue).map(actualTransform).orElse(null),
        expectedValue -> Optional.ofNullable(expectedValue).map(expectedTransform).orElse(null),
        description);
  }

  /**
   * Private constructor to prevent instantiation of this class.
   *
   * <p>This class contains only static method and hence never needs to be instantiated.
   */
  private NullAwareCorrespondence() {}
}
