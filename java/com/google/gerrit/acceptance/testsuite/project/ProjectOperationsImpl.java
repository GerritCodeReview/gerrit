// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.project;

import com.google.gerrit.acceptance.testsuite.project.TestProjectCreation.Builder;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.ProjectOwnerGroupsProvider;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.project.ProjectCreator;
import java.util.ArrayList;
import java.util.Collections;
import javax.inject.Inject;
import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.jgit.lib.Constants;

public class ProjectOperationsImpl implements ProjectOperations {
  private final ProjectCreator projectCreator;
  private final ProjectOwnerGroupsProvider.Factory projectOwnerGroups;

  @Inject
  ProjectOperationsImpl(
      ProjectOwnerGroupsProvider.Factory projectOwnerGroups, ProjectCreator projectCreator) {
    this.projectCreator = projectCreator;
    this.projectOwnerGroups = projectOwnerGroups;
  }

  @Override
  public Builder newProject() {
    return TestProjectCreation.builder(this::createNewProject);
  }

  private Project.NameKey createNewProject(TestProjectCreation projectCreation) throws Exception {
    String name = projectCreation.name().orElse(RandomStringUtils.random(8));

    CreateProjectArgs args = new CreateProjectArgs();
    args.setProjectName(name);
    args.branch = Collections.singletonList(Constants.R_HEADS + Constants.MASTER);
    args.createEmptyCommit = projectCreation.createEmptyCommit().orElse(true);
    projectCreation.parent().ifPresent(p -> args.newParent = new Project.NameKey(p));
    args.ownerIds = new ArrayList<>(projectOwnerGroups.create(args.getProject()).get());
    projectCreation.submitType().ifPresent(st -> args.submitType = st);
    projectCreator.createProject(args);
    return new Project.NameKey(name);
  }
}
