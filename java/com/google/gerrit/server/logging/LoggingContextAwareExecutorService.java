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

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An {@link ExecutorService} that copies the {@link LoggingContext} on executing a {@link Runnable}
 * to the executing thread.
 */
public class LoggingContextAwareExecutorService implements ExecutorService {
  private final ExecutorService executorService;

  public LoggingContextAwareExecutorService(ExecutorService executorService) {
    this.executorService = executorService;
  }

  @Override
  public void execute(Runnable command) {
    executorService.execute(LoggingContext.copy(command));
  }

  @Override
  public void shutdown() {
    executorService.shutdown();
  }

  @Override
  public List<Runnable> shutdownNow() {
    return executorService.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return executorService.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return executorService.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return executorService.awaitTermination(timeout, unit);
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    return executorService.submit(LoggingContext.copy(task));
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    return executorService.submit(LoggingContext.copy(task), result);
  }

  @Override
  public Future<?> submit(Runnable task) {
    return executorService.submit(LoggingContext.copy(task));
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return executorService.invokeAll(tasks.stream().map(LoggingContext::copy).collect(toList()));
  }

  @Override
  public <T> List<Future<T>> invokeAll(
      Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException {
    return executorService.invokeAll(
        tasks.stream().map(LoggingContext::copy).collect(toList()), timeout, unit);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    return executorService.invokeAny(tasks.stream().map(LoggingContext::copy).collect(toList()));
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return executorService.invokeAny(
        tasks.stream().map(LoggingContext::copy).collect(toList()), timeout, unit);
  }
}
