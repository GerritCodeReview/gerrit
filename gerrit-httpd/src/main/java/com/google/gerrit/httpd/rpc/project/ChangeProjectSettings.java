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

import com.google.gerrit.common.data.ProjectDetail;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;

class ChangeProjectSettings extends Handler<ProjectDetail> {
  interface Factory {
    ChangeProjectSettings create(@Assisted Project update);
  }

  private final ProjectDetailFactory.Factory projectDetailFactory;
  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final ReviewDb db;
  private final GitRepositoryManager repoManager;

  private final Project update;

  @Inject
  ChangeProjectSettings(
      final ProjectDetailFactory.Factory projectDetailFactory,
      final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final ReviewDb db,
      final GitRepositoryManager grm,
      @Assisted final Project update) {
    this.projectDetailFactory = projectDetailFactory;
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.db = db;
    this.repoManager = grm;

    this.update = update;
  }

  @Override
  public ProjectDetail call() throws NoSuchProjectException, OrmException {
    final Project.NameKey projectName = update.getNameKey();
    final ProjectControl projectControl =
        projectControlFactory.ownerFor(projectName);
    final Project.Id projectId = projectControl.getProject().getId();

    final Project proj = db.projects().get(projectId);
    if (proj == null) {
      throw new NoSuchProjectException(projectName);
    }

    proj.copySettingsFrom(update);
    db.projects().update(Collections.singleton(proj));
    projectCache.evict(proj);

    if (!projectControl.getProjectState().isSpecialWildProject()) {
      repoManager.setProjectDescription(projectName.get(), update.getDescription());
    }

    return projectDetailFactory.create(projectName).call();
  }
}
