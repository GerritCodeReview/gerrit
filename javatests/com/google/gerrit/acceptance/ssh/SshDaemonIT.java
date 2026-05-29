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
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Module;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
