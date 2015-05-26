// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.common;

import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.Set;


/**
 * This class allows you to lock different resources of a type atomically.
 *
 * @param <T> The resource type you want to have a lock of.
 */
public class MultiLock<T> {
  private Set<T> currentLocks;

  /** Creates an empty MultiLock. */
  public MultiLock() {
    currentLocks = new HashSet<>();
  }

  /**
   * Obtains the lock for each of the items in the set atomically.
   *
   * @param set the set of items to hold a lock for
   * @return {@code true} if the operation could succeed and all locks were
   *         successfully locked. If one of the locks could not be obtained,
   *         no lock will be obtained and {@code false} will be returned.
   */
  public synchronized boolean lock(Set<T> set) {
    if (Sets.intersection(currentLocks, set).isEmpty()) {
      currentLocks.addAll(set);
      return true;
    } else {
      return false;
    }
  }

  /**
   * Releases locks for all given items.
   *
   * @param set The set of items to be unlocked.
   */
  public synchronized void unlock(Set<T> set) {
    currentLocks.removeAll(set);
  }

  @Override
  public String toString() {
    return getClass().getName() + "{" + currentLocks + "}";
  }
}

