// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.server.git.WorkQueue;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Singleton
class PluginCleanerTask implements Runnable {
  private final WorkQueue workQueue;
  private final PluginLoader loader;
  private volatile int pending;
  private Future<?> self;
  private int attempts;
  private long start;

  @Inject
  PluginCleanerTask(WorkQueue workQueue, PluginLoader loader) {
    this.workQueue = workQueue;
    this.loader = loader;
  }

  @Override
  public void run() {
    try {
      for (int t = 0; t < 2 * (attempts + 1); t++) {
        System.gc();
        Thread.sleep(50);
      }
    } catch (InterruptedException e) {
      // Ignored
    }

    int left = loader.processPendingCleanups();
    synchronized (this) {
      pending = left;
      self = null;

      if (0 < left) {
        long waiting = TimeUtil.nowMs() - start;
        PluginLoader.log.warn(
            String.format(
                "%d plugins still waiting to be reclaimed after %d minutes",
                pending, TimeUnit.MILLISECONDS.toMinutes(waiting)));
        attempts = Math.min(attempts + 1, 15);
        ensureScheduled();
      } else {
        attempts = 0;
      }
    }
  }

  @Override
  public String toString() {
    int p = pending;
    if (0 < p) {
      return String.format("Plugin Cleaner (waiting for %d plugins)", p);
    }
    return "Plugin Cleaner";
  }

  synchronized void clean(int expect) {
    if (self == null && pending == 0) {
      start = TimeUtil.nowMs();
    }
    pending = expect;
    ensureScheduled();
  }

  private void ensureScheduled() {
    if (self == null && 0 < pending) {
      if (attempts == 1) {
        self = workQueue.getDefaultQueue().schedule(this, 30, TimeUnit.SECONDS);
      } else {
        self = workQueue.getDefaultQueue().schedule(this, attempts + 1, TimeUnit.MINUTES);
      }
    }
  }
}
