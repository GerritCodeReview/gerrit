// Copyright (C) 2019 The Android Open Source Project
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
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.kohsuke.args4j.Argument;

/** Display sequence value. */
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "show", description = "Display the sequence value")
final class SequenceShowCommand extends SshCommand {
  @Argument(index = 0, metaVar = "NAME", required = true, usage = "sequence name")
  private String name;

  @Argument(index = 0, metaVar = "PROJECT_NAME", required = false, usage = "project name")
  private String projectName;

  @Inject Sequences sequences;

  @Override
  public void run() throws Exception {
    int current;
    switch (name) {
      case "changes":
        current = sequences.currentChangeId(Project.nameKey(projectName));
        break;
      case "accounts":
        current = sequences.currentAccountId();
        break;
      case "groups":
        current = sequences.currentGroupId();
        break;
      default:
        throw die("Unknown sequence name: " + name);
    }
    stdout.print(current);
    stdout.print('\n');
    stdout.flush();
  }
}
