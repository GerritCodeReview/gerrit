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

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.common.flogger.context.Tags;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.Provider;
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
  private static final ThreadLocal<Boolean> aclLogging = new ThreadLocal<>();

  /**
   * When copying the logging context to a new thread we need to ensure that the mutable log records
   * (performance logs and ACL logs) that are added in the new thread are added to the same multable
   * log records instance (see {@link LoggingContextAwareRunnable} and {@link
   * LoggingContextAwareCallable}). This is important since performance log records are processed
   * only at the end of the request and performance log records that are created in another thread
   * should not get lost.
   */
  private static final ThreadLocal<MutablePerformanceLogRecords> performanceLogRecords =
      new ThreadLocal<>();

  private static final ThreadLocal<MutableAclLogRecords> aclLogRecords = new ThreadLocal<>();

  /**
   * ThreadLocal variable to keep track of operations which are currently running. Allows to know
   * the callers (aka parent operations) of an operation for the purpose of logging the callers.
   */
  private static final ThreadLocal<RunningOperations> runningOperations = new ThreadLocal<>();

  private LoggingContext() {}

  /** This method is expected to be called via reflection (and might otherwise be unused). */
  public static LoggingContext getInstance() {
    return INSTANCE;
  }

  public static Runnable copy(Runnable runnable) {
    if (runnable instanceof LoggingContextAwareRunnable) {
      return runnable;
    }

    return new LoggingContextAwareRunnable(
        runnable,
        getInstance().getMutablePerformanceLogRecords(),
        getInstance().getMutableAclRecords(),
        // We copy the currently running operations to the background thread so that so that these
        // operations appear as callers of any operations that are executed in the background
        // thread. Since we copy RunningOperations any operations that are newly added in the
        // background thread do not affect the current thread. To avoid that the async operations
        // are misinterpreted as sub-steps of the callers LoggingContextAwareRunnable has a generic
        // operation to record the execution of the run method that sets the thread name in a
        // metadata field. When formatting the operations (see Metadata#decorateOperation(String))
        // we include the thread name so that we can see from the caller chain where async calls are
        // done.
        getInstance().getRunningOperations().copy());
  }

  public static <T> Callable<T> copy(Callable<T> callable) {
    if (callable instanceof LoggingContextAwareCallable) {
      return callable;
    }

    return new LoggingContextAwareCallable<>(
        callable,
        getInstance().getMutablePerformanceLogRecords(),
        getInstance().getMutableAclRecords(),
        // We copy the currently running operations to the background thread so that so that these
        // operations appear as callers of any operations that are executed in the background
        // thread. Since we copy RunningOperations any operations that are newly added in the
        // background thread do not affect the current thread. To avoid that the async operations
        // are misinterpreted as sub-steps of the callers LoggingContextAwareCallable has a generic
        // operation to record the execution of the call method that sets the thread name in a
        // metadata field. When formatting the operations (see Metadata#decorateOperation(String))
        // we include the thread name so that we can see from the caller chain where async calls are
        // done.
        getInstance().getRunningOperations().copy());
  }

  public boolean isEmpty() {
    return tags.get() == null
        && forceLogging.get() == null
        && performanceLogging.get() == null
        && (performanceLogRecords.get() == null || performanceLogRecords.get().isEmtpy())
        && aclLogging.get() == null
        && (aclLogRecords.get() == null || aclLogRecords.get().isEmpty())
        && (runningOperations.get() == null || runningOperations.get().isEmpty());
  }

  public void clear() {
    try {
      tags.remove();
      forceLogging.remove();
      performanceLogging.remove();
      performanceLogRecords.remove();
      aclLogging.remove();
      aclLogRecords.remove();
      runningOperations.remove();
    } catch (RuntimeException e) {
      FluentLogger.forEnclosingClass()
          .atSevere()
          .withCause(e)
          .log("Clearing logging context failed: %s", this);
      throw e;
    }
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
    return Boolean.TRUE.equals(forceLogging.get());
  }

  @CanIgnoreReturnValue
  boolean forceLogging(boolean force) {
    Boolean oldValue = forceLogging.get();
    if (force) {
      forceLogging.set(true);
    } else {
      forceLogging.remove();
    }
    return Boolean.TRUE.equals(oldValue);
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
   */
  void performanceLogging(boolean enable) {
    if (enable) {
      performanceLogging.set(true);
    } else {
      performanceLogging.remove();
    }
  }

  /**
   * Adds a performance log record, if performance logging is enabled.
   *
   * @param recordProvider Provider for the performance log record. This provider is only invoked if
   *     performance logging is enabled. This means if performance logging is disabled, we avoid the
   *     creation of a {@link PerformanceLogRecord}.
   */
  public void addPerformanceLogRecord(Provider<PerformanceLogRecord> recordProvider) {
    if (!isPerformanceLogging()) {
      // return early and avoid the creation of a PerformanceLogRecord
      return;
    }

    getMutablePerformanceLogRecords().add(recordProvider.get());
  }

  ImmutableList<PerformanceLogRecord> getPerformanceLogRecords() {
    MutablePerformanceLogRecords records = performanceLogRecords.get();
    if (records != null) {
      return records.list();
    }
    return ImmutableList.of();
  }

  void clearPerformanceLogEntries() {
    performanceLogRecords.remove();
  }

  /**
   * Set the performance log records in this logging context. Existing log records are overwritten.
   *
   * <p>This method makes a defensive copy of the passed in list.
   *
   * @param newPerformanceLogRecords performance log records that should be set
   */
  void setPerformanceLogRecords(List<PerformanceLogRecord> newPerformanceLogRecords) {
    if (newPerformanceLogRecords.isEmpty()) {
      performanceLogRecords.remove();
      return;
    }

    getMutablePerformanceLogRecords().set(newPerformanceLogRecords);
  }

  /**
   * Sets a {@link MutablePerformanceLogRecords} instance for storing performance log records.
   *
   * <p><strong>Attention:</strong> The passed in {@link MutablePerformanceLogRecords} instance is
   * directly stored in the logging context.
   *
   * <p>This method is intended to be only used when the logging context is copied to a new thread.
   *
   * @param mutablePerformanceLogRecords the {@link MutablePerformanceLogRecords} instance in which
   *     performance log records should be stored
   */
  void setMutablePerformanceLogRecords(MutablePerformanceLogRecords mutablePerformanceLogRecords) {
    performanceLogRecords.set(requireNonNull(mutablePerformanceLogRecords));
  }

  private MutablePerformanceLogRecords getMutablePerformanceLogRecords() {
    MutablePerformanceLogRecords records = performanceLogRecords.get();
    if (records == null) {
      records = new MutablePerformanceLogRecords();
      performanceLogRecords.set(records);
    }
    return records;
  }

  public boolean isAclLogging() {
    Boolean isAclLogging = aclLogging.get();
    return isAclLogging != null ? isAclLogging : false;
  }

  /**
   * Enables ACL logging.
   *
   * <p>It's important to enable ACL logging only in a context that ensures to consume the captured
   * ACL log records. Otherwise captured ACL log records might leak into other requests that are
   * executed by the same thread (if a thread pool is used to process requests).
   *
   * @param enable whether ACL logging should be enabled.
   * @return whether ACL logging was be enabled before invoking this method (old value).
   */
  @CanIgnoreReturnValue
  boolean aclLogging(boolean enable) {
    Boolean oldValue = aclLogging.get();
    if (enable) {
      aclLogging.set(true);
    } else {
      aclLogging.remove();
    }
    return oldValue != null ? oldValue : false;
  }

  /**
   * Adds an ACL log record.
   *
   * @param aclLogRecord ACL log record
   */
  public void addAclLogRecord(String aclLogRecord) {
    if (!isAclLogging()) {
      return;
    }

    getMutableAclRecords().add(aclLogRecord);
  }

  ImmutableList<String> getAclLogRecords() {
    MutableAclLogRecords records = aclLogRecords.get();
    if (records != null) {
      return records.list();
    }
    return ImmutableList.of();
  }

  /**
   * Set the ACL log records in this logging context. Existing log records are overwritten.
   *
   * <p>This method makes a defensive copy of the passed in list.
   *
   * @param newAclLogRecords ACL log records that should be set
   */
  void setAclLogRecords(List<String> newAclLogRecords) {
    if (newAclLogRecords.isEmpty()) {
      aclLogRecords.remove();
      return;
    }

    getMutableAclRecords().set(newAclLogRecords);
  }

  /**
   * Sets a {@link MutableAclLogRecords} instance for storing ACL log records.
   *
   * <p><strong>Attention:</strong> The passed in {@link MutableAclLogRecords} instance is directly
   * stored in the logging context.
   *
   * <p>This method is intended to be only used when the logging context is copied to a new thread
   * to ensure that the ACL log records that are added in the new thread are added to the same
   * {@link MutableAclLogRecords} instance (see {@link LoggingContextAwareRunnable} and {@link
   * LoggingContextAwareCallable}). This is important since ACL log records are processed only at
   * the end of the request and ACL log records that are created in another thread should not get
   * lost.
   *
   * @param mutableAclLogRecords the {@link MutableAclLogRecords} instance in which ACL log records
   *     should be stored
   */
  void setMutableAclLogRecords(MutableAclLogRecords mutableAclLogRecords) {
    aclLogRecords.set(requireNonNull(mutableAclLogRecords));
  }

  private MutableAclLogRecords getMutableAclRecords() {
    MutableAclLogRecords records = aclLogRecords.get();
    if (records == null) {
      records = new MutableAclLogRecords();
      aclLogRecords.set(records);
    }
    return records;
  }

  public RunningOperations getRunningOperations() {
    RunningOperations runningOperations = LoggingContext.runningOperations.get();
    if (runningOperations == null) {
      runningOperations = new RunningOperations();
      LoggingContext.runningOperations.set(runningOperations);
    }
    return runningOperations;
  }

  void setRunningOperations(RunningOperations runningOperations) {
    LoggingContext.runningOperations.set(requireNonNull(runningOperations));
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("tags", tags.get())
        .add("forceLogging", forceLogging.get())
        .add("performanceLogging", performanceLogging.get())
        .add("performanceLogRecords", performanceLogRecords.get())
        .add("aclLogging", aclLogging.get())
        .add("aclLogRecords", aclLogRecords.get())
        .add("runningOperations", getRunningOperations())
        .toString();
  }
}
