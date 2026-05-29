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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.sshd.SshCommand;
import java.util.concurrent.CyclicBarrier;
import org.kohsuke.args4j.Option;

public abstract class TestCommand extends SshCommand {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final CyclicBarrier syncPoint = new CyclicBarrier(2);

  @Option(
      name = "--duration",
      aliases = {"-d"},
      required = true,
      usage = "Duration of the command execution in seconds")
  private int duration;

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    logger.atFine().log("Starting command.");
    if (isGraceful()) {
      enableGracefulStop();
    }
    try {
      syncPoint.await();
      Thread.sleep(duration * 1000);
      logger.atFine().log("Stopping command.");
    } catch (Exception e) {
      throw die("Command ended prematurely.", e);
    }
  }

  abstract boolean isGraceful();
}
