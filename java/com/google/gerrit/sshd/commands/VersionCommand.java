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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.gerrit.common.Version;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;

@CommandMetaData(name = "version", description = "Display gerrit version", runsAt = MASTER_OR_SLAVE)
final class VersionCommand extends SshCommand {

  @Override
  protected void run() throws Failure {
    String v = Version.getVersion();
    if (v == null) {
      throw new Failure(1, "fatal: version unavailable");
    }

    stdout.println("gerrit version " + v);
  }
}
