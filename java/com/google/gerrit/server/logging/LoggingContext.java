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

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.backend.Tags;
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
}
