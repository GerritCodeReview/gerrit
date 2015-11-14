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
 * <p>
 * Typical usage in a try-with-resources block:
 *
 * <pre>
 * try (Timer3.Context ctx = timer.start(field)) {
 * }
 * </pre>
 *
 * @param <F1> type of the field.
 * @param <F2> type of the field.
 * @param <F3> type of the field.
 */
public abstract class Timer3<F1, F2, F3> implements RegistrationHandle {
  public static class Context extends TimerContext {
    private final Timer3<Object, Object, Object> timer;
    private final Object field1;
    private final Object field2;
    private final Object field3;

    @SuppressWarnings("unchecked")
    <F1, F2, F3> Context(Timer3<F1, F2, F3> timer, F1 f1, F2 f2, F3 f3) {
      this.timer = (Timer3<Object, Object, Object>) timer;
      this.field1 = f1;
      this.field2 = f2;
      this.field3 = f3;
    }

    @Override
    public void record(long elapsed) {
      timer.record(field1, field2, field3, elapsed, NANOSECONDS);
    }
  }

  /** Begin a timer for the current block, value will be recorded when closed. */
  public Context start(F1 field1, F2 field2, F3 field3) {
    return new Context(this, field1, field2, field3);
  }

  /** Record a value in the distribution. */
  public abstract void record(F1 field1, F2 field2, F3 field3,
      long value, TimeUnit unit);
}
