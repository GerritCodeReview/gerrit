// Copyright (C) 2010 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.common;

import java.util.Collection;

/**
 * Utils operations for manipulating Collections
 */
public class CollectionsUtil {

  private CollectionsUtil() {
  }

  /**
   * Checks if any of the elements in the first collection can be found in the
   * second collection.
   *
   * @param findAnyOfThese which elements to look for.
   * @param inThisCollection where to look for them.
   * @param <E> type of the elements in question.
   * @return {@code true} if any of the elements in {@code findAnyOfThese} can
   *         be found in {@code inThisCollection}, {@code false} otherwise.
   */
  public static <E> boolean isAnyIncludedIn(Collection<E> findAnyOfThese,
      Collection<E> inThisCollection) {
    for (E findThisItem : findAnyOfThese) {
      if (inThisCollection.contains(findThisItem)) {
        return true;
      }
    }
    return false;
  }

}
