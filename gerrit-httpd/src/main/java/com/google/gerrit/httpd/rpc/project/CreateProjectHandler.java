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
// limitations under the License

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.errors.ProjectCreationFailedException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.server.project.CreateProject;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Constants;

import java.util.Collections;

public class CreateProjectHandler extends Handler<VoidResult> {

  interface Factory {
    CreateProjectHandler create(@Assisted("projectName") String projectName,
        @Assisted("parentName") String parentName,
        @Assisted("emptyCommit") boolean emptyCommit,
        @Assisted("permissionsOnly") boolean permissionsOnly);
  }

  private final CreateProject.Factory createProjectFactory;
  private final ProjectControl.Factory projectControlFactory;
  private final String projectName;
  private final String parentName;
  private final boolean emptyCommit;
  private final boolean permissionsOnly;

  @Inject
  public CreateProjectHandler(final CreateProject.Factory createProjectFactory,
      final ProjectControl.Factory projectControlFactory,
      @Assisted("projectName") final String projectName,
      @Assisted("parentName") final String parentName,
      @Assisted("emptyCommit") final boolean emptyCommit,
      @Assisted("permissionsOnly") final boolean permissionsOnly) {
    this.createProjectFactory = createProjectFactory;
    this.projectControlFactory = projectControlFactory;
    this.projectName = projectName;
    this.parentName = parentName;
    this.emptyCommit = emptyCommit;
    this.permissionsOnly = permissionsOnly;
  }

  @Override
  public VoidResult call() throws ProjectCreationFailedException {
    final CreateProjectArgs args = new CreateProjectArgs();
    args.setProjectName(projectName);
    if (!parentName.equals("")) {
      final Project.NameKey nameKey = new Project.NameKey(parentName);
      try {
        args.newParent = projectControlFactory.validateFor(nameKey);
      } catch (NoSuchProjectException e) {
        throw new ProjectCreationFailedException("Parent project \""
            + parentName + "\" does not exist.", e);
      }
    }
    args.projectDescription = "";
    args.submitType = SubmitType.MERGE_IF_NECESSARY;
    args.branch = Collections.singletonList(Constants.MASTER);
    args.createEmptyCommit = emptyCommit;
    args.permissionsOnly = permissionsOnly;

    final CreateProject createProject = createProjectFactory.create(args);
    createProject.createProject();
    return VoidResult.INSTANCE;
  }
}
