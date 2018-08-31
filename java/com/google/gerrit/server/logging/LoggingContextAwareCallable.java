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
import java.util.concurrent.Callable;

/**
 * Wrapper for a {@link Callable} that copies the {@link LoggingContext} from the current thread to
 * the thread that executes the callable.
 *
 * <p>The state of the logging context that is copied to the thread that executes the callable is
 * fixed at the creation time of this wrapper. If the callable is submitted to an executor and is
 * executed later this means that changes that are done to the logging context in between creating
 * and executing the callable do not apply.
 *
 * <p>See {@link LoggingContextAwareRunnable} for an example.
 *
 * @see LoggingContextAwareRunnable
 */
class LoggingContextAwareCallable<T> implements Callable<T> {
  private final Callable<T> callable;
  private final Thread callingThread;
  private final ImmutableSetMultimap<String, String> tags;
  private final boolean forceLogging;

  LoggingContextAwareCallable(Callable<T> callable) {
    this.callable = callable;
    this.callingThread = Thread.currentThread();
    this.tags = LoggingContext.getInstance().getTagsAsMap();
    this.forceLogging = LoggingContext.getInstance().isLoggingForced();
  }

  @Override
  public T call() throws Exception {
    if (callingThread.equals(Thread.currentThread())) {
      // propagation of logging context is not needed
      return callable.call();
    }

    // propagate logging context
    LoggingContext loggingCtx = LoggingContext.getInstance();
    ImmutableSetMultimap<String, String> oldTags = loggingCtx.getTagsAsMap();
    boolean oldForceLogging = loggingCtx.isLoggingForced();
    loggingCtx.setTags(tags);
    loggingCtx.forceLogging(forceLogging);
    try {
      return callable.call();
    } finally {
      loggingCtx.setTags(oldTags);
      loggingCtx.forceLogging(oldForceLogging);
    }
  }
}
