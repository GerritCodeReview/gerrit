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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.restapi.config.ListTasks;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.io.File;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.eclipse.jgit.lib.Config;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@NoHttpd
@UseSsh
@Sandboxed
@RunWith(ConfigSuite.class)
@SuppressWarnings("unused")
public class SshDaemonIT extends AbstractDaemonTest {

  @Inject private ListTasks listTasks;
  @Inject private SitePaths gerritSitePath;

  @ConfigSuite.Parameter protected Config config;

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

  public Future<Integer> startCommand(String command, File lockFile) throws TimeoutException {
    Callable<Integer> gracefulSession =
        () -> {
          int returnCode = -1;
          System.out.println("Before Command");
          returnCode = userSshSession.execAndReturnStatus(command);
          System.out.println("After Command");
          return returnCode;
        };

    ExecutorService executor = Executors.newFixedThreadPool(1);
    Future<Integer> future = executor.submit(gracefulSession);

    LocalDateTime timeout = LocalDateTime.now().plusSeconds(10);

    while (!lockFile.exists()) {
      if (LocalDateTime.now().isAfter(timeout)) {
        lockFile.delete();
        throw new TimeoutException("Timeout waiting for command's lock file.");
      }
    }

    return future;
  }

  @Test
  public void NonGracefulCommandIsStoppedImmediately() throws Exception {
    File lockFile =
        new File(String.format("%s/%s", gerritSitePath.etc_dir, NonGracefulCommand.LOCK_FILE));
    Future<Integer> future = startCommand("non-graceful", lockFile);
    restart();
    lockFile.delete();
    Assert.assertTrue(future.get() == -1);
  }

  @Test
  public void GracefulCommandIsStoppedGracefully() throws Exception {
    File lockFile =
        new File(String.format("%s/%s", gerritSitePath.etc_dir, GracefulCommand.LOCK_FILE));
    Future<Integer> future = startCommand("graceful", lockFile);
    restart();
    lockFile.delete();
    if (cfg.getTimeUnit("sshd", null, "gracefulStopTimeout", 0, TimeUnit.SECONDS) == 0) {
      Assert.assertTrue(future.get() == -1);
    } else {
      Assert.assertTrue(future.get() == 0);
    }
  }
}
