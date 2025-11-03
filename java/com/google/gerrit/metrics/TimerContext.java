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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.server.logging.LoggingContext;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.RunningOperations.RegistrationHandle;

abstract class TimerContext implements AutoCloseable {
  private final long startNanos;
  private boolean stopped;
  private final RegistrationHandle registrationHandle;

  TimerContext(String timerName, Metadata metadata) {
    this.startNanos = System.nanoTime();
    this.registrationHandle = LoggingContext.getInstance().startOperation(timerName, metadata);
  }

  /**
   * Record the elapsed time to the timer.
   *
   * @param elapsed Elapsed time in nanoseconds.
   */
  public abstract void record(long elapsed, ImmutableList<String> parentOperations);

  /** Returns the start time in system time nanoseconds. */
  public long getStartTime() {
    return startNanos;
  }

  /**
   * Stop the timer and record the elapsed time.
   *
   * @return the elapsed time in nanoseconds.
   * @throws IllegalStateException if the timer is already stopped.
   */
  @CanIgnoreReturnValue
  public long stop() {
    if (!stopped) {
      stopped = true;
      long elapsed = System.nanoTime() - startNanos;
      record(elapsed, registrationHandle.parentOperations());
      registrationHandle.remove();
      return elapsed;
    }
    throw new IllegalStateException("Already stopped");
  }

  @Override
  public void close() {
    if (!stopped) {
      stop();
    }
  }
}
