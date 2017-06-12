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

package com.google.gerrit.metrics;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import com.google.gerrit.extensions.registration.RegistrationHandle;
import java.util.concurrent.TimeUnit;

/**
 * Records elapsed time for an operation or span.
 *
 * <p>Typical usage in a try-with-resources block:
 *
 * <pre>
 * try (Timer.Context ctx = timer.start()) {
 * }
 * </pre>
 */
public abstract class Timer0 implements RegistrationHandle {
  public static class Context extends TimerContext {
    private final Timer0 timer;

    Context(Timer0 timer) {
      this.timer = timer;
    }

    @Override
    public void record(long elapsed) {
      timer.record(elapsed, NANOSECONDS);
    }
  }

  /**
   * Begin a timer for the current block, value will be recorded when closed.
   *
   * @return timer context
   */
  public Context start() {
    return new Context(this);
  }

  /**
   * Record a value in the distribution.
   *
   * @param value value to record
   * @param unit time unit of the value
   */
  public abstract void record(long value, TimeUnit unit);
}
