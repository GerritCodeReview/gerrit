// Copyright 2009 Google Inc.
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

package com.google.gerrit.git;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class WorkQueue {
  private static ScheduledThreadPoolExecutor pool;

  public static synchronized void schedule(final Runnable task,
      final long delay, final TimeUnit unit) {
    if (pool == null) {
      pool = new ScheduledThreadPoolExecutor(1);
      pool.setKeepAliveTime(60, TimeUnit.SECONDS);
      pool.setMaximumPoolSize(5);
    }
    pool.schedule(task, delay, unit);
  }

  public static void terminate() {
    final ScheduledThreadPoolExecutor p = shutdown();
    if (p != null) {
      boolean isTerminated;
      do {
        try {
          isTerminated = p.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
          isTerminated = false;
        }
      } while (!isTerminated);
    }
  }

  private static synchronized ScheduledThreadPoolExecutor shutdown() {
    final ScheduledThreadPoolExecutor p = pool;
    if (p != null) {
      p.shutdown();
      pool = null;
      return p;
    }
    return null;
  }
}
