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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gwtorm.client.IntKey;

/** Static utilities for ReviewDb types. */
public class ReviewDbUtil {
  public static final Function<IntKey<?>, Integer> INT_KEY_FUNCTION =
      new Function<IntKey<?>, Integer>() {
        @Override
        public Integer apply(IntKey<?> in) {
          return in.get();
        }
      };

  private static final Function<Change, Change.Id> CHANGE_ID_FUNCTION =
      new Function<Change, Change.Id>() {
        @Override
        public Change.Id apply(Change in) {
          return in.getId();
        }
      };

  private static final Ordering<? extends IntKey<?>> INT_KEY_ORDERING =
      Ordering.natural().nullsFirst().onResultOf(INT_KEY_FUNCTION).nullsFirst();

  @SuppressWarnings("unchecked")
  public static <K extends IntKey<?>> Ordering<K> intKeyOrdering() {
    return (Ordering<K>) INT_KEY_ORDERING;
  }

  public static Function<Change, Change.Id> changeIdFunction() {
    return CHANGE_ID_FUNCTION;
  }

  public static ReviewDb unwrapDb(ReviewDb db) {
    if (db instanceof DisabledChangesReviewDbWrapper) {
      return ((DisabledChangesReviewDbWrapper) db).unsafeGetDelegate();
    }
    return db;
  }

  private ReviewDbUtil() {
  }
}
