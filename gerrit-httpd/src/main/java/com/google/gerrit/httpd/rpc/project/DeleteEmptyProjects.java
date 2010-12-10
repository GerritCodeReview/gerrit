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

import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.PerformDeleteProject;
import com.google.gerrit.server.project.PerformDeleteProjectImpl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** This class performs the deletion of an empty (has no changes) project. */
public class DeleteEmptyProjects extends Handler<List<NameKey>> {

  interface Factory {
    DeleteEmptyProjects create(@Assisted List<NameKey> projectsToDelete);
  }

  private static final Logger log =
      LoggerFactory.getLogger(PerformDeleteProjectImpl.class);

  private final ProjectCache projectCache;
  private final List<NameKey> projectsToDelete;
  private final ProjectControl.Factory projectControlFactory;

  @Inject
  private PerformDeleteProjectImpl.Factory performDeleteProject;

  @Inject
  DeleteEmptyProjects(final ProjectCache projectCache,
      final ProjectControl.Factory projectControlFactory,
      @Assisted List<NameKey> projectsToDelete) {
    this.projectsToDelete = projectsToDelete;
    this.projectCache = projectCache;
    this.projectControlFactory = projectControlFactory;
  }

  @Override
  public List<NameKey> call() {
    final List<NameKey> projectsList = new ArrayList<NameKey>();
    final List<NameKey> notDeletedprojects = new ArrayList<NameKey>();

    for (NameKey p : projectsToDelete) {
      try {
        final ProjectControl projectControl =
            projectControlFactory.controlFor(p);
        if (projectControl.isOwner()) {
          projectsList.add(p);
        } else {
          notDeletedprojects.add(p);
          log.error("fatal: User has no rights to delete " + p.get());
        }
      } catch (NoSuchProjectException e) {
        notDeletedprojects.add(p);
        log.error("Project " + p.get() + " was not found.");
      }
    }

    final PerformDeleteProject perfDeleteProject =
        performDeleteProject.create(projectsList);

    notDeletedprojects.addAll(perfDeleteProject.deleteProjects());
    projectCache.evictAll();

    return notDeletedprojects;
  }
}
