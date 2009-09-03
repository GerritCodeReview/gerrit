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

package com.google.gerrit.server.rpc.project;

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.rpc.Handler;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;
import java.util.Set;

class DeleteProjectRights extends Handler<VoidResult> {
  interface Factory {
    DeleteProjectRights create(@Assisted Project.NameKey projectName,
        @Assisted Set<ProjectRight.Key> toRemove);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final ReviewDb db;

  private final Project.NameKey projectName;
  private final Set<ProjectRight.Key> toRemove;

  @Inject
  DeleteProjectRights(final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final ReviewDb db,

      @Assisted final Project.NameKey projectName,
      @Assisted final Set<ProjectRight.Key> toRemove) {
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.db = db;

    this.projectName = projectName;
    this.toRemove = toRemove;
  }

  @Override
  public VoidResult call() throws NoSuchProjectException, OrmException {
    final ProjectControl projectControl =
        projectControlFactory.ownerFor(projectName);

    for (final ProjectRight.Key k : toRemove) {
      if (!projectName.equals(k.getProjectNameKey())) {
        throw new IllegalArgumentException("All keys must be from same project");
      }
    }

    for (final ProjectRight.Key k : toRemove) {
      final ProjectRight m = db.projectRights().get(k);
      if (m != null) {
        db.projectRights().delete(Collections.singleton(m));
      }
    }
    projectCache.evict(projectControl.getProject());
    return VoidResult.INSTANCE;
  }
}
