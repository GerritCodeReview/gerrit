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
 * Measures the statistical distribution of values in a stream of data.
 *
 * <p>Suitable uses are "response size in bytes", etc.
 *
 * @param <F1> type of the field.
 */
public abstract class Histogram1<F1> implements RegistrationHandle {
  /**
   * Record a sample of a specified amount.
   *
   * @param field1 bucket to record sample
   * @param value value to record
   */
  public abstract void record(F1 field1, long value);
}
