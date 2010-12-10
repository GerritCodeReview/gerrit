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
// limitations under the License

package com.google.gerrit.httpd.rpc.project;

import com.google.gerrit.common.data.ProjectData;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public class ChangeProjectStatus extends Handler<VoidResult> {
  interface Factory {
    ChangeProjectStatus create(@Assisted List<ProjectData> projectsToUpdate);
  }

  private static final Logger log =
      LoggerFactory.getLogger(DeleteEmptyProjects.class);
  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final ReviewDb db;

  private final List<ProjectData> projectsToUpdate;

  @Inject
  ChangeProjectStatus(final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final ReviewDb db,
      @Assisted final List<ProjectData> projectsToUpdate) {
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.db = db;

    this.projectsToUpdate = projectsToUpdate;
  }

  @Override
  public VoidResult call() throws NoSuchProjectException, OrmException {

    for (ProjectData p : projectsToUpdate) {
      final ProjectControl projectControl =
          projectControlFactory.controlFor(p.getNameKey());
      if (projectControl.isOwner()) {
        final Project proj = db.projects().get(p.getNameKey());
        proj.setStatus(p.getStatus());
        proj.resetLastUpdatedOn();
        db.projects().update(Collections.singleton(proj));
        projectCache.evict(proj);
      } else {
        log.error("User has no rights to update " + p.getName());
      }
    }

    return VoidResult.INSTANCE;
  }
}
