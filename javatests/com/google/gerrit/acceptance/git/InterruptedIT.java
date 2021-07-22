// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class InterruptedIT extends AbstractDaemonTest {
  @Test
  public void noInterrupt() throws Exception {
    StringBuilder output = new StringBuilder();
    ExecutorService executor = Executors.newFixedThreadPool(1);
    Future<?> future = executor.submit(new TestPrinter(5, output));
    future.get();
    assertThat(output.toString())
        .isEqualTo(
            "[START]\n"
                + "1s passed\n"
                + "2s passed\n"
                + "3s passed\n"
                + "4s passed\n"
                + "5s passed\n"
                + "[END]\n");
  }

  @Test
  public void cancelNoInterrupt() throws Exception {
    StringBuilder output = new StringBuilder();
    ExecutorService executor = Executors.newFixedThreadPool(1);
    Future<?> future = executor.submit(new TestPrinter(5, output));

    // Cancel the future after 1.5s so that we are sure the the TestPrinter has been started.
    // Cancelling with mayInterruptIfRunning = false means that the thread is not interrupted if the
    // task was already started.
    Thread.sleep(1500);
    future.cancel(/* mayInterruptIfRunning= */ false);

    // Since the future was cancelled, trying to get the result throws an exception.
    assertThrows(CancellationException.class, () -> future.get());

    // Give the background thread enough time to finish (since it was not interrupted it still does
    // its whole job)
    Thread.sleep(5000);

    assertThat(output.toString())
        .isEqualTo(
            "[START]\n"
                + "1s passed\n"
                + "2s passed\n"
                + "3s passed\n"
                + "4s passed\n"
                + "5s passed\n"
                + "[END]\n");
  }

  @Test
  public void cancelBeforeStart() throws Exception {
    StringBuilder output = new StringBuilder();
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    // Schedule the TestPrinter to be executed in 2s.
    Future<?> future = executor.schedule(new TestPrinter(5, output), 2, TimeUnit.SECONDS);

    // Cancelling the future means that the task is not executed if it wasn't started yet .
    future.cancel(/* mayInterruptIfRunning= */ false);

    // Since the future was cancelled, trying to get the result throws an exception.
    assertThrows(CancellationException.class, () -> future.get());

    // Give the background thread enough time to finish (if it would be running)
    Thread.sleep(8000);

    assertThat(output.toString()).isEmpty();
  }

  @Test
  public void cancelWithInterrupt() throws Exception {
    StringBuilder output = new StringBuilder();
    ExecutorService executor = Executors.newFixedThreadPool(1);
    Future<?> future = executor.submit(new TestPrinter(5, output));

    // Cancel the future after 1.5s so that we are sure the the TestPrinter has been started.
    // Cancelling with mayInterruptIfRunning = true means that the thread is interrupted if the task
    // was already started.
    Thread.sleep(1500);
    future.cancel(/* mayInterruptIfRunning= */ true);

    // Since the future was cancelled, trying to get the result throws an exception.
    assertThrows(CancellationException.class, () -> future.get());

    // Give the background thread enough time to finish (in case it continued to run)
    Thread.sleep(5000);

    assertThat(output.toString()).isEqualTo("[START]\n1s passed\n[INTERRUPTED WHILE SLEEPING]\n");
  }

  @Test
  public void threadIsNotInterruptedIfReusedAfterCancelWithInterrupt() throws Exception {
    StringBuilder output = new StringBuilder();
    ExecutorService executor = Executors.newFixedThreadPool(1);
    TestPrinter testPrinter1 = new TestPrinter(5, output);
    Future<?> future = executor.submit(testPrinter1);

    // Cancel the future after 1.5s so that we are sure the the TestPrinter has been started.
    // Cancelling with mayInterruptIfRunning = true means that the thread is interrupted if the task
    // was already started.
    Thread.sleep(1500);
    future.cancel(/* mayInterruptIfRunning= */ true);

    // Since the future was cancelled, trying to get the result throws an exception.
    assertThrows(CancellationException.class, () -> future.get());

    // Give the background thread enough time to finish (in case it continued to run)
    Thread.sleep(5000);

    assertThat(output.toString()).isEqualTo("[START]\n1s passed\n[INTERRUPTED WHILE SLEEPING]\n");

    // Submit another task
    output = new StringBuilder();
    TestPrinter testPrinter2 = new TestPrinter(8, output);
    Future<?> future2 = executor.submit(testPrinter2);
    future2.get();
    assertThat(output.toString())
        .isEqualTo(
            "[START]\n"
                + "1s passed\n"
                + "2s passed\n"
                + "3s passed\n"
                + "4s passed\n"
                + "5s passed\n"
                + "6s passed\n"
                + "7s passed\n"
                + "8s passed\n"
                + "[END]\n");

    // Check that the same thread was used.
    assertThat(testPrinter1.threadName).isEqualTo(testPrinter2.threadName);
  }

  private static class TestPrinter implements Runnable {
    public String threadName;

    private final int secondsToRun;
    private final StringBuilder output;

    public TestPrinter(int secondsToRun, StringBuilder output) {
      this.secondsToRun = secondsToRun;
      this.output = output;
    }

    @Override
    public void run() {
      // check whether an interrupt from a previous run is still present
      if (Thread.currentThread().isInterrupted()) {
        output.append("thread is interrupted!");
      }

      this.threadName = Thread.currentThread().getName();

      output.append("[START]\n");
      for (int i = 1; i <= secondsToRun; i++) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
          output.append("[INTERRUPTED WHILE SLEEPING]\n");

          // Reset the interrupted flag to see if it leaks into the next task that is executed by
          // this thread.
          Thread.currentThread().interrupt();
          return;
        }
        output.append(String.format("%ds passed\n", i));
      }
      output.append("[END]\n");
    }
  }
}
