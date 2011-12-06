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

import com.google.gerrit.common.errors.InvalidNameException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.project.PerformCreateProject;
import com.google.gerrit.server.project.PerformCreateProjectImpl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Constants;

public class CreateNewProject extends Handler<VoidResult> {

  interface Factory {
    CreateNewProject create(@Assisted("projectName") String projectName,
        @Assisted("parentName") String parentName, @Assisted boolean emptyCommit);
  }

  private final PerformCreateProjectImpl.Factory performCreateProjectFactory;
  private final ProjectControl.Factory projectControlFactory;
  private final String projectName;
  private final String parentName;
  private final boolean emptyCommit;

  @Inject
  public CreateNewProject(
      final PerformCreateProjectImpl.Factory performCreateProjectFactory,
      final ProjectControl.Factory projectControlFactory,
      @Assisted("projectName") final String projectName,
      @Assisted("parentName") final String parentName,
      @Assisted boolean emptyCommit) {
    this.performCreateProjectFactory = performCreateProjectFactory;
    this.projectControlFactory = projectControlFactory;
    this.projectName = projectName;
    this.parentName = parentName;
    this.emptyCommit = emptyCommit;
  }

  @Override
  public VoidResult call() throws Exception,InvalidNameException {
    final CreateProjectArgs args = new CreateProjectArgs();
    args.setProjectName(projectName);
    if (!parentName.equals("")) {
      final Project.NameKey nameKey = new Project.NameKey(parentName);
      final ProjectControl parentProjectCtl =
          projectControlFactory.validateFor(nameKey);
      args.setNewParent(parentProjectCtl);
    } else {
      args.setNewParent(null);
    }
    args.setProjectDescription("");
    args.setSubmitType(SubmitType.MERGE_IF_NECESSARY);
    args.setBranch(Constants.MASTER);
    args.setCreateEmptyCommit(emptyCommit);

    final PerformCreateProject performCreateProject =
        performCreateProjectFactory.create(args);

    try {
      performCreateProject.createProject();
    } catch (Exception e) {
      throw new Exception("Error when trying to create project: "
          + e.getMessage());
    }

    return VoidResult.INSTANCE;
  }
}
