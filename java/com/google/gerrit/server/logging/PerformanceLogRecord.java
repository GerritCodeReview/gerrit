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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.Optional;

/**
 * The record of an operation for which the execution time was measured.
 *
 * <p>Metadata to provide additional context can be included by providing a {@link Metadata}
 * instance.
 */
@AutoValue
public abstract class PerformanceLogRecord {
  /**
   * Creates a performance log record without meta data.
   *
   * @param operation the name of operation the is was performed
   * @param durationNanos the execution time in nanoseconds
   * @param parentOperations the parent operations that called the operation for which this
   *     performance log is being created
   * @return the performance log record
   */
  public static PerformanceLogRecord create(
      String operation, long durationNanos, ImmutableList<String> parentOperations) {
    return new AutoValue_PerformanceLogRecord(
        operation, durationNanos, Instant.now(), parentOperations, Optional.empty());
  }

  /**
   * Creates a performance log record with meta data.
   *
   * @param operation the name of operation the is was performed
   * @param durationNanos the execution time in nanoseconds
   * @param metadata metadata
   * @return the performance log record
   */
  public static PerformanceLogRecord create(
      String operation,
      long durationNanos,
      ImmutableList<String> parentOperations,
      Metadata metadata) {
    return new AutoValue_PerformanceLogRecord(
        operation, durationNanos, Instant.now(), parentOperations, Optional.of(metadata));
  }

  public abstract String operation();

  public abstract long durationNanos();

  public abstract Instant endTime();

  public abstract ImmutableList<String> parentOperations();

  public abstract Optional<Metadata> metadata();

  public String getDecoratedOperationName() {
    return metadata().isPresent() ? metadata().get().decorateOperation(operation()) : operation();
  }

  void writeTo(PerformanceLogger performanceLogger) {
    if (metadata().isPresent()) {
      performanceLogger.logNanos(operation(), durationNanos(), endTime(), metadata().get());
    } else {
      performanceLogger.logNanos(operation(), durationNanos(), endTime());
    }
  }
}
