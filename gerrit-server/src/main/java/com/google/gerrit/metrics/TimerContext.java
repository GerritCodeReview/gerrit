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

abstract class TimerContext implements AutoCloseable {
  private final long startNanos;

  TimerContext() {
    this.startNanos = System.nanoTime();
  }

  /** Record the elapsed time to the timer. */
  public abstract void record(long nanoSeconds);

  /** Get the start time in system time nanoseconds. */
  public long getStartTime() {
    return startNanos;
  }

  /** Stop the timer, record the elapsed time, and return the elapsed time. */
  public long stop() {
    long elapsed = System.nanoTime() - startNanos;
    record(elapsed);
    return elapsed;
  }

  @Override
  public void close() {
    stop();
  }
}
