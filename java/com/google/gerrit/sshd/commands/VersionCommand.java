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

import com.google.gerrit.extensions.common.VersionInfo;
import com.google.gerrit.json.OutputFormat;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.kohsuke.args4j.Option;

@CommandMetaData(name = "version", description = "Display gerrit version", runsAt = MASTER_OR_SLAVE)
final class VersionCommand extends SshCommand {

  @Option(
      name = "--verbose",
      aliases = {"-v"},
      usage = "verbose version info")
  private boolean verbose;

  @Option(name = "--json", usage = "json output format, assumes verbose output")
  private boolean json;

  @Inject private VersionInfo versionInfo;

  @Override
  protected void run() throws Failure {
    enableGracefulStop();
    if (versionInfo.gerritVersion == null) {
      throw new Failure(1, "fatal: version unavailable");
    }

    if (json) {
      stdout.println(OutputFormat.JSON.newGson().toJson(versionInfo));
    } else if (verbose) {
      stdout.print(versionInfo.verbose());
    } else {
      stdout.print(versionInfo.compact());
    }
  }
}
