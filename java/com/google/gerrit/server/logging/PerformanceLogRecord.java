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
    return new AutoValue_PerformanceLogRecord(
        operation, durationMs, null, null, null, null, null, null, null, null);
  }

  /**
   * Creates a performance log record with meta data.
   *
   * @param operation the name of operation the is was performed
   * @param durationMs the execution time in milliseconds
   * @param key meta data key
   * @param value meta data value
   * @return the performance log record
   */
  public static PerformanceLogRecord create(
      String operation, long durationMs, String key, @Nullable Object value) {
    return new AutoValue_PerformanceLogRecord(
        operation, durationMs, requireNonNull(key), value, null, null, null, null, null, null);
  }

  /**
   * Creates a performance log record with meta data.
   *
   * @param operation the name of operation the is was performed
   * @param durationMs the execution time in milliseconds
   * @param key1 first meta data key
   * @param value1 first meta data value
   * @param key2 second meta data key
   * @param value2 second meta data value
   * @return the performance log record
   */
  public static PerformanceLogRecord create(
      String operation,
      long durationMs,
      String key1,
      @Nullable Object value1,
      String key2,
      @Nullable Object value2) {
    return new AutoValue_PerformanceLogRecord(
        operation,
        durationMs,
        requireNonNull(key1),
        value1,
        requireNonNull(key2),
        value2,
        null,
        null,
        null,
        null);
  }

  /**
   * Creates a performance log record with meta data.
   *
   * @param operation the name of operation the is was performed
   * @param durationMs the execution time in milliseconds
   * @param key1 first meta data key
   * @param value1 first meta data value
   * @param key2 second meta data key
   * @param value2 second meta data value
   * @param key3 third meta data key
   * @param value3 third meta data value
   * @return the performance log record
   */
  public static PerformanceLogRecord create(
      String operation,
      long durationMs,
      String key1,
      @Nullable Object value1,
      String key2,
      @Nullable Object value2,
      String key3,
      @Nullable Object value3) {
    return new AutoValue_PerformanceLogRecord(
        operation,
        durationMs,
        requireNonNull(key1),
        value1,
        requireNonNull(key2),
        value2,
        requireNonNull(key3),
        value3,
        null,
        null);
  }

  /**
   * Creates a performance log record with meta data.
   *
   * @param operation the name of operation the is was performed
   * @param durationMs the execution time in milliseconds
   * @param key1 first meta data key
   * @param value1 first meta data value
   * @param key2 second meta data key
   * @param value2 second meta data value
   * @param key3 third meta data key
   * @param value3 third meta data value
   * @param key4 forth meta data key
   * @param value4 forth meta data value
   * @return the performance log record
   */
  public static PerformanceLogRecord create(
      String operation,
      long durationMs,
      String key1,
      @Nullable Object value1,
      String key2,
      @Nullable Object value2,
      String key3,
      @Nullable Object value3,
      String key4,
      @Nullable Object value4) {
    return new AutoValue_PerformanceLogRecord(
        operation,
        durationMs,
        requireNonNull(key1),
        value1,
        requireNonNull(key2),
        value2,
        requireNonNull(key3),
        value3,
        requireNonNull(key4),
        value4);
  }

  public abstract String operation();

  public abstract long durationMs();

  @Nullable
  public abstract String key1();

  @Nullable
  public abstract Object value1();

  @Nullable
  public abstract String key2();

  @Nullable
  public abstract Object value2();

  @Nullable
  public abstract String key3();

  @Nullable
  public abstract Object value3();

  @Nullable
  public abstract String key4();

  @Nullable
  public abstract Object value4();

  void writeTo(PerformanceLogger performanceLogger) {
    if (key4() != null) {
      requireNonNull(key1());
      requireNonNull(key2());
      requireNonNull(key3());
      performanceLogger.log(
          operation(),
          durationMs(),
          key1(),
          value1(),
          key2(),
          value2(),
          key3(),
          value3(),
          key4(),
          value4());
    } else if (key3() != null) {
      requireNonNull(key1());
      requireNonNull(key2());
      performanceLogger.log(
          operation(), durationMs(), key1(), value1(), key2(), value2(), key3(), value3());
    } else if (key2() != null) {
      requireNonNull(key1());
      performanceLogger.log(operation(), durationMs(), key1(), value1(), key2(), value2());
    } else if (key1() != null) {
      performanceLogger.log(operation(), durationMs(), key1(), value1());
    } else {
      performanceLogger.log(operation(), durationMs());
    }
  }
}
