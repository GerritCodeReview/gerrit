// Copyright (C) 2018 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.backend.Tags;
import com.google.inject.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;

/**
 * Logging context for Flogger.
 *
 * <p>To configure this logging context for Flogger set the following system property (also see
 * {@link com.google.common.flogger.backend.system.DefaultPlatform}):
 *
 * <ul>
 *   <li>{@code
 *       flogger.logging_context=com.google.gerrit.server.logging.LoggingContext#getInstance}.
 * </ul>
 */
public class LoggingContext extends com.google.common.flogger.backend.system.LoggingContext {
  private static final LoggingContext INSTANCE = new LoggingContext();

  private static final ThreadLocal<MutableTags> tags = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> forceLogging = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> performanceLogging = new ThreadLocal<>();
  private static final ThreadLocal<List<PerformanceLogRecord>> performanceLogRecords =
      new ThreadLocal<>();

  private LoggingContext() {}

  /** This method is expected to be called via reflection (and might otherwise be unused). */
  public static LoggingContext getInstance() {
    return INSTANCE;
  }

  public static Runnable copy(Runnable runnable) {
    if (runnable instanceof LoggingContextAwareRunnable) {
      return runnable;
    }
    return new LoggingContextAwareRunnable(runnable);
  }

  public static <T> Callable<T> copy(Callable<T> callable) {
    if (callable instanceof LoggingContextAwareCallable) {
      return callable;
    }
    return new LoggingContextAwareCallable<>(callable);
  }

  @Override
  public boolean shouldForceLogging(String loggerName, Level level, boolean isEnabled) {
    return isLoggingForced();
  }

  @Override
  public Tags getTags() {
    MutableTags mutableTags = tags.get();
    return mutableTags != null ? mutableTags.getTags() : Tags.empty();
  }

  public ImmutableSetMultimap<String, String> getTagsAsMap() {
    MutableTags mutableTags = tags.get();
    return mutableTags != null ? mutableTags.asMap() : ImmutableSetMultimap.of();
  }

  boolean addTag(String name, String value) {
    return getMutableTags().add(name, value);
  }

  void removeTag(String name, String value) {
    MutableTags mutableTags = getMutableTags();
    mutableTags.remove(name, value);
    if (mutableTags.isEmpty()) {
      tags.remove();
    }
  }

  void setTags(ImmutableSetMultimap<String, String> newTags) {
    if (newTags.isEmpty()) {
      tags.remove();
      return;
    }
    getMutableTags().set(newTags);
  }

  void clearTags() {
    tags.remove();
  }

  private MutableTags getMutableTags() {
    MutableTags mutableTags = tags.get();
    if (mutableTags == null) {
      mutableTags = new MutableTags();
      tags.set(mutableTags);
    }
    return mutableTags;
  }

  boolean isLoggingForced() {
    Boolean force = forceLogging.get();
    return force != null ? force : false;
  }

  boolean forceLogging(boolean force) {
    Boolean oldValue = forceLogging.get();
    if (force) {
      forceLogging.set(true);
    } else {
      forceLogging.remove();
    }
    return oldValue != null ? oldValue : false;
  }

  boolean isPerformanceLogging() {
    Boolean isPerformanceLogging = performanceLogging.get();
    return isPerformanceLogging != null ? isPerformanceLogging : false;
  }

  /**
   * Enables performance logging.
   *
   * <p>It's important to enable performance logging only in a context that ensures to consume the
   * captured performance log records. Otherwise captured performance log records might leak into
   * other requests that are executed by the same thread (if a thread pool is used to process
   * requests).
   *
   * @param enable whether performance logging should be enabled.
   * @return whether performance logging was be enabled before invoking this method (old value).
   */
  boolean performanceLogging(boolean enable) {
    Boolean oldValue = performanceLogging.get();
    if (enable) {
      performanceLogging.set(true);
    } else {
      performanceLogging.remove();
    }
    return oldValue != null ? oldValue : false;
  }

  /**
   * Adds a performance log record, if performance logging is enabled.
   *
   * @param recordProvider Provider for the performance log record. This provider is only invoked if
   *     performance logging is enabled. This means if performance logging is disabled, we avoid the
   *     creation of a {@link PerformanceLogRecord}.
   */
  public void addPerformanceLogEntry(Provider<PerformanceLogRecord> recordProvider) {
    if (!isPerformanceLogging()) {
      // return early and avoid the creation of a PerformanceLogRecord
      return;
    }

    List<PerformanceLogRecord> records = performanceLogRecords.get();
    if (records == null) {
      records = new ArrayList<>();
      performanceLogRecords.set(records);
    }
    records.add(recordProvider.get());
  }

  ImmutableList<PerformanceLogRecord> getPerformanceLogEntries() {
    List<PerformanceLogRecord> records = performanceLogRecords.get();
    if (records != null) {
      return ImmutableList.copyOf(records);
    }
    return ImmutableList.of();
  }

  void setPerformanceLogEntries(List<PerformanceLogRecord> newPerformanceLogRecords) {
    if (newPerformanceLogRecords.isEmpty()) {
      performanceLogRecords.remove();
      return;
    }
    List<PerformanceLogRecord> records = new ArrayList<>();
    records.addAll(newPerformanceLogRecords);
    performanceLogRecords.set(records);
  }

  void clearPerformanceLogEntries() {
    performanceLogRecords.remove();
  }
}
