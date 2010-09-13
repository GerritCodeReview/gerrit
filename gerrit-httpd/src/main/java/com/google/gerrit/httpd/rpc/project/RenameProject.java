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
import com.google.gerrit.reviewdb.ProjectName;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;

public class RenameProject extends Handler<ProjectDetail> {
  interface Factory {
    RenameProject create(@Assisted Project.NameKey projectNameKey,
        @Assisted String newProjectName);
  }

  private final ProjectDetailFactory.Factory projectDetailFactory;
  private final ProjectCache projectCache;
  private final ReviewDb db;
  private final GitRepositoryManager repoManager;
  private final ReplicationQueue replicationQueue;

  private final Project.NameKey projectNameKey;
  private final String newProjectName;


  @Inject
  RenameProject(final ProjectDetailFactory.Factory projectDetailFactory,
      final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final ReviewDb db,
      final GitRepositoryManager grm,
      final ReplicationQueue rq,
      @Assisted final Project.NameKey projectNameKey,
      @Assisted final String newProjectName) {
    this.projectDetailFactory = projectDetailFactory;
    this.projectCache = projectCache;
    this.db = db;
    this.repoManager = grm;
    this.replicationQueue = rq;

    this.projectNameKey = projectNameKey;
    this.newProjectName = newProjectName;
  }


  @Override
  public ProjectDetail call() throws Exception {
    final ProjectState projectState = projectCache.get(projectNameKey);
    if (projectState == null) {
      throw new NoSuchProjectException(projectNameKey);
    }

    final Project project = projectState.getProject();
    repoManager.renameRepository(project.getName(), newProjectName);

    db.projectNames().delete(Collections.singleton(new ProjectName(project)));

    projectCache.evict(project);

    project.setName(newProjectName);
    db.projects().update(Collections.singleton(project));
    db.projectNames().insert(Collections.singleton(new ProjectName(project)));

    replicationQueue.replicateProjectRename(projectNameKey, project.getNameKey());

    return projectDetailFactory.create(project.getNameKey()).call();
  }
}
