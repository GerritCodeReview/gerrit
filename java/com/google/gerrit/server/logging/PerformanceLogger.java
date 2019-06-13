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

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.util.Map;
import java.util.Optional;

/**
 * Extension point for logging performance records.
 *
 * <p>This extension point is invoked for all operations for which the execution time is measured.
 * The invocation of the extension point does not happen immediately, but only at the end of a
 * request (REST call, SSH call, git push). Implementors can write the execution times into a
 * performance log for further analysis.
 *
 * <p>For optimal performance implementors should overwrite the default <code>log</code> methods to
 * avoid unneeded instantiation of Map objects.
 */
@ExtensionPoint
public interface PerformanceLogger {
  /**
   * Record the execution time of an operation in a performance log.
   *
   * @param operation operation that was performed
   * @param durationMs time that the execution of the operation took (in milliseconds)
   */
  default void log(String operation, long durationMs) {
    log(operation, durationMs, ImmutableMap.of());
  }

  /**
   * Record the execution time of an operation in a performance log.
   *
   * @param operation operation that was performed
   * @param durationMs time that the execution of the operation took (in milliseconds)
   * @param key meta data key
   * @param value meta data value
   */
  default void log(String operation, long durationMs, String key, @Nullable Object value) {
    log(operation, durationMs, ImmutableMap.of(key, Optional.ofNullable(value)));
  }

  /**
   * Record the execution time of an operation in a performance log.
   *
   * @param operation operation that was performed
   * @param durationMs time that the execution of the operation took (in milliseconds)
   * @param key1 first meta data key
   * @param value1 first meta data value
   * @param key2 second meta data key
   * @param value2 second meta data value
   */
  default void log(
      String operation,
      long durationMs,
      String key1,
      @Nullable Object value1,
      String key2,
      @Nullable Object value2) {
    log(
        operation,
        durationMs,
        ImmutableMap.of(key1, Optional.ofNullable(value1), key2, Optional.ofNullable(value2)));
  }

  /**
   * Record the execution time of an operation in a performance log.
   *
   * @param operation operation that was performed
   * @param durationMs time that the execution of the operation took (in milliseconds)
   * @param key1 first meta data key
   * @param value1 first meta data value
   * @param key2 second meta data key
   * @param value2 second meta data value
   * @param key3 third meta data key
   * @param value3 third meta data value
   */
  default void log(
      String operation,
      long durationMs,
      String key1,
      @Nullable Object value1,
      String key2,
      @Nullable Object value2,
      String key3,
      @Nullable Object value3) {
    log(
        operation,
        durationMs,
        ImmutableMap.of(
            key1,
            Optional.ofNullable(value1),
            key2,
            Optional.ofNullable(value2),
            key3,
            Optional.ofNullable(value3)));
  }

  /**
   * Record the execution time of an operation in a performance log.
   *
   * @param operation operation that was performed
   * @param durationMs time that the execution of the operation took (in milliseconds)
   * @param key1 first meta data key
   * @param value1 first meta data value
   * @param key2 second meta data key
   * @param value2 second meta data value
   * @param key3 third meta data key
   * @param value3 third meta data value
   * @param key4 fourth meta data key
   * @param value4 fourth meta data value
   */
  default void log(
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
    log(
        operation,
        durationMs,
        ImmutableMap.of(
            key1,
            Optional.ofNullable(value1),
            key2,
            Optional.ofNullable(value2),
            key3,
            Optional.ofNullable(value3),
            key4,
            Optional.ofNullable(value4)));
  }

  /**
   * Record the execution time of an operation in a performance log.
   *
   * <p>For small numbers of meta data entries the instantiation of a map should avoided by using
   * one of the <code>log</code> methods that allows to pass in meta data entries directly.
   *
   * @param operation operation that was performed
   * @param durationMs time that the execution of the operation took (in milliseconds)
   * @param metaData key-value map with meta data
   */
  void log(String operation, long durationMs, Map<String, Optional<Object>> metaData);
}
