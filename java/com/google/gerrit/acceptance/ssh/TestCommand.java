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
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.File;

public abstract class TestCommand extends SshCommand {
  @Inject private SitePaths gerritSitePath;
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static String LOCK_FILE = "command.lock";

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    logger.atFine().log("Starting command.");
    if (isGraceful()) {
      enableGracefulStop();
    }
    try {
      File lockFile = new File(String.format("%s/%s", gerritSitePath.etc_dir, LOCK_FILE));
      lockFile.createNewFile();
      Thread.sleep(5000);
      lockFile.delete();
      logger.atFine().log("Stopping command.");
    } catch (Exception e) {
      throw die("Command ended prematurely.", e);
    }
  }

  abstract boolean isGraceful();
}
