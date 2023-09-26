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
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.PermissionAwareRepositoryManager;
import com.google.gerrit.server.notedb.ProjectChangeSequence;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Argument;

/** Set sequence value. */
@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "set", description = "Set the sequence value")
final class SequenceSetCommand extends SshCommand {
  @Argument(index = 0, metaVar = "NAME", required = true, usage = "sequence name")
  private String name;

  @Argument(index = 1, metaVar = "PROJECT", required = true, usage = "project name")
  private String projectName;

  @Argument(index = 2, metaVar = "VALUE", required = true, usage = "sequence value")
  private int value;

  @Inject Sequences sequences;

  @Inject private ProjectChangeSequence.Factory projectChangeSequenceFactory;

  @Override
  public void run() throws Exception {
    if (name == null) {
      throw die("Sequence name is required.");
    }

    switch (name) {
      case "changes":
        if (projectName == null) {
          throw die("Project name is required.");
        }

        Project.NameKey project = Project.NameKey.parse(projectName);

        ProjectChangeSequence projectSequence = projectChangeSequenceFactory.create(project);

        projectSequence.setChangeIdValue(value);
        break;
      case "accounts":
        sequences.setAccountIdValue(value);
        break;
      case "groups":
        sequences.setGroupIdValue(value);
        break;
      default:
        throw die("Unknown sequence name: " + name);
    }
    stdout.print("The value for the " + name + " sequence was set to " + value + ".");
    stdout.print('\n');
    stdout.flush();
  }
}
