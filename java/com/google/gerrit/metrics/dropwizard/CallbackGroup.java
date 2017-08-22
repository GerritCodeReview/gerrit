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

package com.google.gerrit.metrics.dropwizard;

import com.google.common.collect.ImmutableSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Run a user specified trigger only once every 2 seconds.
 *
 * <p>This allows the same Runnable trigger to be applied to several metrics. When a recorder is
 * sampling the related metrics only the first access will perform recomputation. Reading other
 * related metrics will rely on the already set values for the next several seconds.
 */
class CallbackGroup implements Runnable {
  private static final long PERIOD = TimeUnit.SECONDS.toNanos(2);

  private final AtomicLong reloadAt;
  private final Runnable trigger;
  private final ImmutableSet<CallbackMetricGlue> metrics;
  private final Object reloadLock = new Object();

  CallbackGroup(Runnable trigger, ImmutableSet<CallbackMetricGlue> metrics) {
    this.reloadAt = new AtomicLong(0);
    this.trigger = trigger;
    this.metrics = metrics;
  }

  @Override
  public void run() {
    if (reload()) {
      synchronized (reloadLock) {
        for (CallbackMetricGlue m : metrics) {
          m.beginSet();
        }
        trigger.run();
        for (CallbackMetricGlue m : metrics) {
          m.endSet();
        }
      }
    }
  }

  private boolean reload() {
    for (; ; ) {
      long now = System.nanoTime();
      long next = reloadAt.get();
      if (next > now) {
        return false;
      } else if (reloadAt.compareAndSet(next, now + PERIOD)) {
        return true;
      }
    }
  }
}
