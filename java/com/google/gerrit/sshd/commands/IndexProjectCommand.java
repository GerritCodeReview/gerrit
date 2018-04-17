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

import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.project.Index;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Argument;

@RequiresAnyCapability({MAINTAIN_SERVER})
@CommandMetaData(name = "project", description = "Index changes of a project")
final class IndexProjectCommand extends SshCommand {

  @Inject private ProjectAccessor.Factory projectAccessorFactory;

  @Inject private Index index;

  @Argument(
    index = 0,
    required = true,
    multiValued = true,
    metaVar = "PROJECT",
    usage = "projects for which the changes should be indexed"
  )
  private List<ProjectState> projects = new ArrayList<>();

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    if (projects.isEmpty()) {
      throw die("needs at least one project as command arguments");
    }
    projects.stream().forEach(this::index);
  }

  private void index(ProjectState projectState) {
    try {
      index.apply(new ProjectResource(projectAccessorFactory.create(projectState), user), null);
    } catch (Exception e) {
      writeError(
          "error", String.format("Unable to index %s: %s", projectState.getName(), e.getMessage()));
    }
  }
}
