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

import com.google.common.flogger.backend.Tags;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * ThreadFactory that copies the logging context of the current thread to any new thread that is
 * created by this ThreadFactory.
 */
public class LoggingContextAwareThreadFactory implements ThreadFactory {
  private final ThreadFactory parentThreadFactory;

  public LoggingContextAwareThreadFactory() {
    this.parentThreadFactory = Executors.defaultThreadFactory();
  }

  public LoggingContextAwareThreadFactory(ThreadFactory parentThreadFactory) {
    this.parentThreadFactory = parentThreadFactory;
  }

  @Override
  public Thread newThread(Runnable r) {
    Thread callingThread = Thread.currentThread();
    Tags tags = LoggingContext.getInstance().getTags();
    boolean forceLogging = LoggingContext.getInstance().isLoggingForced();
    return parentThreadFactory.newThread(
        () -> {
          if (callingThread.equals(Thread.currentThread())) {
            // propagation of logging context is not needed
            r.run();
            return;
          }

          // propagate logging context
          LoggingContext loggingCtx = LoggingContext.getInstance();
          loggingCtx.getMutableTags().set(tags);
          loggingCtx.forceLogging(forceLogging);
          try {
            r.run();
          } finally {
            loggingCtx.getMutableTags().clear();
            loggingCtx.forceLogging(false);
          }
        });
  }
}
