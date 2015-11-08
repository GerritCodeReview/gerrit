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

import com.google.gerrit.extensions.registration.RegistrationHandle;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.concurrent.TimeUnit;

/**
 * Records elapsed time for an operation or span.
 * <p>
 * Typical usage in a try-with-resources block:
 *
 * <pre>
 * try (Timer.Context ctx = timer.start()) {
 * }
 * </pre>
 */
public abstract class Timer implements RegistrationHandle {
  public class Context implements AutoCloseable {
    private final long startNanos;

    Context() {
      this.startNanos = System.nanoTime();
    }

    @Override
    public void close() {
      record(unit.convert(System.nanoTime() - startNanos, NANOSECONDS));
    }
  }

  private final TimeUnit unit;

  protected Timer(TimeUnit unit) {
    this.unit = checkNotNull(unit);
  }

  /** Begin a timer for the current block, value will be recorded when closed. */
  public Context start() {
    return new Context();
  }

  /** Record a value in the distribution. */
  public abstract void record(long value);
}
