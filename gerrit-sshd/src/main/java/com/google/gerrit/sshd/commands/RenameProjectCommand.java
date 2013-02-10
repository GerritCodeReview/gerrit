// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.project.RenameProject;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;

/** Create a new project. **/
@RequiresCapability(GlobalCapability.RENAME_PROJECT)
final class RenameProjectCommand extends SshCommand {
  @Argument(index = 0, required = true, metaVar = "PROJECT",
      usage = "name of the project to be moved")
  private String sourceName;

  @Argument(index = 1, required = true, metaVar = "NEWNAME",
      usage = "new name for the project")
  private String destinationName;

  @Inject
  private RenameProject.Factory renameProjectFactory;

  @Override
  protected void run() throws Failure {
    try {
      final RenameProject renameProject = renameProjectFactory.create(
          sourceName, destinationName);
      renameProject.renameProject();
    } catch (ProjectRenamingFailedException e) {
      throw die(e);
    }
  }
}
