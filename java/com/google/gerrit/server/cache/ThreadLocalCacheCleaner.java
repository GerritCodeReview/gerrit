// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.cache;

import java.util.HashSet;
import java.util.Set;

/**
 * Thread local handler that calls <code>clean</code> on each registered cleaner upon current
 * execution ends in Thread.
 */
public class ThreadLocalCacheCleaner {
  /**
   * Each class that wants to use thread's cache and perform cleaning afterwards should register
   * CacheCleaner implementation.
   */
  @FunctionalInterface
  public interface CacheCleaner {
    void clean();
  }

  private static ThreadLocal<ThreadLocalCacheCleaner> handler =
      ThreadLocal.withInitial(ThreadLocalCacheCleaner::new);

  public static ThreadLocalCacheCleaner get() {
    return handler.get();
  }

  private final Set<CacheCleaner> cleaners;

  ThreadLocalCacheCleaner() {
    this.cleaners = new HashSet<>();
  }

  public void registerCleaner(CacheCleaner cleaner) {
    cleaners.add(cleaner);
  }

  public void cleanThreadCache() {
    cleaners.forEach(CacheCleaner::clean);
    cleaners.clear();
    handler.remove();
  }
}
