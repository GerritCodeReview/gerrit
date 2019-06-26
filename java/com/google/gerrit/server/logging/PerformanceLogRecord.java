// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.logging;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoValue;
import com.google.gerrit.common.Nullable;

/**
 * The record of an operation for which the execution time was measured.
 *
 * <p>Meta data is stored in separate key/value fields to avoid expensive instantiations of Map
 * objects.
 */
@AutoValue
public abstract class PerformanceLogRecord {
  /**
   * Creates a performance log record without meta data.
   *
   * @param operation the name of operation the is was performed
   * @param durationMs the execution time in milliseconds
   * @return the performance log record
   */
  public static PerformanceLogRecord create(String operation, long durationMs) {
    return new AutoValue_PerformanceLogRecord(operation, durationMs, null);
  }

  /**
   * Creates a performance log record with meta data.
   *
   * @param operation the name of operation the is was performed
   * @param durationMs the execution time in milliseconds
   * @param metadata metadata
   * @return the performance log record
   */
  public static PerformanceLogRecord create(String operation, long durationMs, Metadata metadata) {
    return new AutoValue_PerformanceLogRecord(operation, durationMs, requireNonNull(metadata));
  }

  public abstract String operation();

  public abstract long durationMs();

  @Nullable
  public abstract Metadata metadata();

  void writeTo(PerformanceLogger performanceLogger) {
    if (metadata() != null) {
      performanceLogger.log(operation(), durationMs(), metadata());
    } else {
      performanceLogger.log(operation(), durationMs());
    }
  }
}
