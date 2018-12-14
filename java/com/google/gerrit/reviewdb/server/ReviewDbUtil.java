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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Sets;
import com.google.gwtorm.client.Column;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/** Static utilities for ReviewDb types. */
public class ReviewDbUtil {
  public static ReviewDb unwrapDb(ReviewDb db) {
    if (db instanceof DisallowedReviewDb) {
      return unwrapDb(((DisallowedReviewDb) db).unsafeGetDelegate());
    }
    return db;
  }

  public static void checkColumns(Class<?> clazz, Integer... expected) {
    Set<Integer> ids = new TreeSet<>();
    for (Field f : clazz.getDeclaredFields()) {
      Column col = f.getAnnotation(Column.class);
      if (col != null) {
        ids.add(col.id());
      }
    }
    Set<Integer> expectedIds = Sets.newTreeSet(Arrays.asList(expected));
    checkState(
        ids.equals(expectedIds),
        "Unexpected column set for %s: %s != %s",
        clazz.getSimpleName(),
        ids,
        expectedIds);
  }

  private ReviewDbUtil() {}
}
