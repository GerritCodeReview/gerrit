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

package com.google.gerrit.reviewdb.server;

import com.google.common.base.Function;
import com.google.common.collect.Ordering;
import com.google.gwtorm.client.IntKey;

/** Static utilities for ReviewDb types. */
public class ReviewDbUtil {
  private static final Function<IntKey<?>, Integer> INT_KEY_FUNCTION =
      new Function<IntKey<?>, Integer>() {
        @Override
        public Integer apply(IntKey<?> in) {
          return in.get();
        }
      };

  private static final Ordering<? extends IntKey<?>> INT_KEY_ORDERING =
      Ordering.natural().onResultOf(INT_KEY_FUNCTION);

  @SuppressWarnings("unchecked")
  public static <K extends IntKey<?>> Ordering<K> intKeyOrdering() {
    return (Ordering<K>) INT_KEY_ORDERING;
  }

  private ReviewDbUtil() {
  }
}
