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

public class LoggingContextAwareRunnable implements Runnable {
  private final Runnable runnable;
  private final Thread callingThread;
  private final ImmutableSetMultimap<String, String> tags;
  private final boolean forceLogging;

  LoggingContextAwareRunnable(Runnable runnable) {
    this.runnable = runnable;
    this.callingThread = Thread.currentThread();
    this.tags = LoggingContext.getInstance().getTagsAsMap();
    this.forceLogging = LoggingContext.getInstance().isLoggingForced();
  }

  public Runnable unwrap() {
    return runnable;
  }

  @Override
  public void run() {
    if (callingThread.equals(Thread.currentThread())) {
      // propagation of logging context is not needed
      runnable.run();
      return;
    }

    // propagate logging context
    LoggingContext loggingCtx = LoggingContext.getInstance();
    loggingCtx.setTags(tags);
    loggingCtx.forceLogging(forceLogging);
    try {
      runnable.run();
    } finally {
      loggingCtx.clearTags();
      loggingCtx.forceLogging(false);
    }
  }
}
