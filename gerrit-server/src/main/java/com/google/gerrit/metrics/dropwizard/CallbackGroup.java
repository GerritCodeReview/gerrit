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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class CallbackGroup implements Runnable {
  private static final long PERIOD = TimeUnit.SECONDS.toNanos(2);

  private final AtomicLong reloadAt;
  private final Runnable trigger;

  CallbackGroup(Runnable trigger) {
    this.reloadAt = new AtomicLong(0);
    this.trigger = trigger;
  }

  @Override
  public void run() {
    if (reload()) {
      trigger.run();
    }
  }

  private boolean reload() {
    for (;;) {
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
