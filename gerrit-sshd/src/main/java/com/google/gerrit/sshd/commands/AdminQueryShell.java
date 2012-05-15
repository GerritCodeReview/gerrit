// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.sshd.AdminHighPriorityCommand;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

/** Opens a query processor. */
@AdminHighPriorityCommand
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
final class AdminQueryShell extends SshCommand {
  @Inject
  private QueryShell.Factory factory;

  @Option(name = "--format", usage = "Set output format")
  private QueryShell.OutputFormat format = QueryShell.OutputFormat.PRETTY;

  @Option(name = "-c", metaVar = "SQL QUERY", usage = "Query to execute")
  private String query;

  @Override
  protected void run() {
    final QueryShell shell = factory.create(in, out);
    shell.setOutputFormat(format);
    if (query != null) {
      shell.execute(query);
    } else {
      shell.run();
    }
  }
}
