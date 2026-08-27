// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritServerTestRule;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Module;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;
import org.junit.runner.RunWith;

@NoHttpd
@UseSsh
@Sandboxed
@RunWith(ConfigSuite.class)
public class SshDaemonIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config gracefulConfig() {
    Config config = new Config();
    config.setString("sshd", null, "gracefulStopTimeout", "10s");
    return config;
  }

  @Override
  public Module createSshModule() {
    return new TestSshCommandModule();
  }

  @Test
  public void nonGracefulCommandIsStoppedImmediately() throws Exception {
    try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
      Future<Integer> future = startCommand(executor, false);
      closeTestRepositories();
      ((GerritServerTestRule) server).restartKeepSessionOpen();
      assertThat(future.get()).isEqualTo(-1);
    }
  }

  @Test
  public void gracefulCommandIsStoppedGracefully() throws Exception {
    assume().that(isGracefulStopEnabled()).isTrue();

    try (ExecutorService executor = Executors.newFixedThreadPool(1)) {
      Future<Integer> future = startCommand(executor, true);
      closeTestRepositories();
      ((GerritServerTestRule) server).restartKeepSessionOpen();
      assertThat(future.get()).isEqualTo(0);
    }
  }

  @Test
  public void clientDisconnectDoesNotLogInternalServerError() throws Exception {
    List<LogRecord> severeRecords = new CopyOnWriteArrayList<>();
    Handler captureHandler =
        new Handler() {
          @Override
          public void publish(LogRecord r) {
            if (r.getLevel().intValue() >= Level.SEVERE.intValue()) {
              severeRecords.add(r);
            }
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    Logger baseCommandLogger = Logger.getLogger(BaseCommand.class.getName());
    baseCommandLogger.addHandler(captureHandler);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<Integer> commandFuture =
          executor.submit(() -> userSshSession.execAndReturnStatus("interrupted"));

      // Wait until the command is running and parked in Thread.sleep().
      InterruptedCommand.syncPoint.await(30, TimeUnit.SECONDS);

      // Simulate the client dropping the connection. sshd calls destroy(), which
      // interrupts the worker thread.
      userSshSession.close();

      // Positive signal: prove the interrupt actually reached the command and was
      // rethrown wrapped, so a green test cannot mean "the path never ran".
      assertThat(InterruptedCommand.threwWrapped.await(30, TimeUnit.SECONDS)).isTrue();

      // handleError() runs just after the throw. Poll rather than sleeping a fixed
      // interval: fail fast on a bad log, and do not burn wall-clock on success.
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (System.nanoTime() < deadline && severeRecords.isEmpty()) {
        Thread.sleep(50);
      }

      // Surface any failure from the command thread rather than discarding it.
      commandFuture.get(30, TimeUnit.SECONDS);
    } finally {
      baseCommandLogger.removeHandler(captureHandler);
      executor.shutdownNow();
    }

    assertThat(severeRecords).isEmpty();
  }

  private Future<Integer> startCommand(ExecutorService executor, boolean graceful)
      throws Exception {
    Future<Integer> future =
        executor.submit(
            () ->
                userSshSession.execAndReturnStatus(
                    String.format("%sgraceful -d 5", graceful ? "" : "non-")));
    TestCommand.syncPoint.await();
    return future;
  }

  private boolean isGracefulStopEnabled() {
    return cfg.getTimeUnit("sshd", null, "gracefulStopTimeout", 0, TimeUnit.SECONDS) > 0;
  }
}
