// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;
import static com.google.gerrit.server.i18n.I18n.getText;

import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.project.IndexChanges;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Argument;

@RequiresAnyCapability({MAINTAIN_SERVER})
@CommandMetaData(name = "changes-in-project", description = "Index changes of a project")
final class IndexChangesInProjectCommand extends SshCommand {

  @Inject private IndexChanges index;

  @Argument(
      index = 0,
      required = true,
      multiValued = true,
      metaVar = "PROJECT",
      usage = "projects for which the changes should be indexed")
  private List<ProjectState> projects = new ArrayList<>();

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    if (projects.isEmpty()) {
      throw die(getText("sshd.command.index.project.changes.input.empty"));
    }
    projects.stream().forEach(this::index);
  }

  private void index(ProjectState projectState) {
    try {
      index.apply(new ProjectResource(projectState, user), null);
    } catch (Exception e) {
      writeError(
          "error",
          getText(
              "sshd.command.index.project.changes.failed", projectState.getName(), e.getMessage()));
    }
  }
}
