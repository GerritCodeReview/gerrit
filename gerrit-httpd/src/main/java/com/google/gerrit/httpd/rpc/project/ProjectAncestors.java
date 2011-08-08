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

package com.google.gerrit.httpd.rpc.project;


import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.errors.ConfigInvalidException;

class ProjectAncestors extends Handler<List<Project.NameKey>> {
  interface Factory {
    ProjectAncestors create(@Assisted Project.NameKey projectName);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final Project.NameKey projectName;

  @Inject
  ProjectAncestors(final ProjectControl.Factory projectControlFactory,
      @Assisted final Project.NameKey projectName) {
    this.projectControlFactory = projectControlFactory;

    this.projectName = projectName;
  }

  @Override
  public List<Project.NameKey> call() throws NoSuchProjectException {
    List<Project.NameKey> ancestors = new ArrayList<Project.NameKey>();
    final ProjectControl pc =
        projectControlFactory.validateFor(projectName, ProjectControl.OWNER
            | ProjectControl.VISIBLE);
    ProjectState projectState = pc.getProjectState().getParentState();
    Set<Project.NameKey> seen = new HashSet<Project.NameKey>();
    Project.NameKey prj;
    while (projectState != null) {
      prj = projectState.getProject().getNameKey();
      if (!seen.add(prj)) {
        break;
      }
      ancestors.add(prj);
      projectState = projectState.getParentState();
    }
    return ancestors;
  }
}
