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

/**
 * Metric whose value increments during the life of the process.
 *
 * <p>Suitable uses are "total requests handled", "bytes sent", etc. Use {@link
 * Description#setRate()} to suggest the monitoring system should also track the rate of increments
 * if this is of interest.
 *
 * <p>For an instantaneous read of a value that can change over time (e.g. "memory in use") use a
 * {@link CallbackMetric}.
 */
public abstract class Counter0 implements RegistrationHandle {
  /** Increment the counter by one event. */
  public void increment() {
    incrementBy(1);
  }

  /**
   * Increment the counter by a specified amount.
   *
   * @param value value to increment by, must be &gt;= 0.
   */
  public abstract void incrementBy(long value);
}
