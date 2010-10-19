// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.PerformUpdateParents;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.UpdateParentsFailedException;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.internal.Nullable;

import java.util.Collections;

public class UpdateParent extends Handler<ProjectDetail> {
  private final PerformUpdateParents.Factory performUpdateParentsFactory;
  private final ProjectControl.Factory projectControlFactory;
  private final ProjectDetailFactory.Factory projectDetailFactory;
  private final Project.NameKey childProjectName;
  private final Project.NameKey newParentProjectName;

  interface Factory {
    UpdateParent create(@Assisted("childProjectName") Project.NameKey childProjectName,
        @Assisted("newParentProjectName") Project.NameKey newParentProjectName);
  }

  @Inject
  UpdateParent(
      final PerformUpdateParents.Factory performCreateProject,
      final ProjectControl.Factory projectControlFactory,
      final ProjectDetailFactory.Factory projectDetailFactory,
      @Assisted("childProjectName") final Project.NameKey childProjectName,
      @Assisted("newParentProjectName") @Nullable final Project.NameKey newParentProjectName) {
    this.performUpdateParentsFactory = performCreateProject;
    this.projectControlFactory = projectControlFactory;
    this.projectDetailFactory = projectDetailFactory;
    this.childProjectName = childProjectName;
    this.newParentProjectName = newParentProjectName;
  }

  @Override
  public ProjectDetail call() throws OrmException, UpdateParentsFailedException,
      NoSuchProjectException {
    final ProjectControl childProject =
        projectControlFactory.controlFor(childProjectName);
    final ProjectControl newParentProject =
        newParentProjectName != null ? projectControlFactory
            .controlFor(newParentProjectName) : null;
    final PerformUpdateParents performUpdateParents =
        performUpdateParentsFactory.create();
    performUpdateParents.updateParents(Collections.singleton(childProject),
        newParentProject);
    return projectDetailFactory.create(childProjectName).call();
  }
}
