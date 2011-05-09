// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.Version;
import com.google.gerrit.sshd.BaseCommand;

import org.apache.sshd.server.Environment;

import java.io.PrintWriter;

final class VersionCommand extends BaseCommand {
  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Failure {
        parseCommandLine();

        String v = Version.getVersion();
        if (v == null) {
          throw new Failure(1, "fatal: version unavailable");
        }

        final PrintWriter stdout = toPrintWriter(out);
        stdout.println("gerrit version " + v);
        stdout.flush();
      }
    });
  }
}
