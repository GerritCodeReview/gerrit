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

import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ScheduledExecutorService} that copies the {@link LoggingContext} on executing a {@link
 * Runnable} to the executing thread.
 */
public class LoggingContextAwareScheduledExecutorService extends LoggingContextAwareExecutorService
    implements ScheduledExecutorService {
  private final ScheduledExecutorService scheduledExecutorService;

  public LoggingContextAwareScheduledExecutorService(
      ScheduledExecutorService scheduledExecutorService) {
    super(scheduledExecutorService);
    this.scheduledExecutorService = scheduledExecutorService;
  }

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    return scheduledExecutorService.schedule(LoggingContext.copy(command), delay, unit);
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    return scheduledExecutorService.schedule(LoggingContext.copy(callable), delay, unit);
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(
      Runnable command, long initialDelay, long period, TimeUnit unit) {
    return scheduledExecutorService.scheduleAtFixedRate(
        LoggingContext.copy(command), initialDelay, period, unit);
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(
      Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return scheduledExecutorService.scheduleWithFixedDelay(
        LoggingContext.copy(command), initialDelay, delay, unit);
  }
}
