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
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.NoSuchRefException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.RefControl;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.Collections;
import java.util.Set;

class DeleteRefRights extends Handler<VoidResult> {
  interface Factory {
    DeleteRefRights create(@Assisted Project.NameKey projectName,
        @Assisted Set<RefRight.Key> toRemove);
  }

  private final ProjectControl.Factory projectControlFactory;
  private final ProjectCache projectCache;
  private final ReviewDb db;

  private final Project.NameKey projectName;
  private final Set<RefRight.Key> toRemove;

  @Inject
  DeleteRefRights(final ProjectControl.Factory projectControlFactory,
      final ProjectCache projectCache, final ReviewDb db,

      @Assisted final Project.NameKey projectName,
      @Assisted final Set<RefRight.Key> toRemove) {
    this.projectControlFactory = projectControlFactory;
    this.projectCache = projectCache;
    this.db = db;

    this.projectName = projectName;
    this.toRemove = toRemove;
  }

  @Override
  public VoidResult call() throws NoSuchProjectException, OrmException,
      NoSuchRefException {
    final ProjectControl projectControl =
        projectControlFactory.controlFor(projectName);

    for (final RefRight.Key k : toRemove) {
      if (!projectName.equals(k.getProjectNameKey())) {
        throw new IllegalArgumentException("All keys must be from same project");
      }
      if (!controlForRef(projectControl, k.getRefPattern()).isOwner()) {
        throw new NoSuchRefException(k.getRefPattern());
      }
    }

    for (final RefRight.Key k : toRemove) {
      final RefRight m = db.refRights().get(k);
      if (m != null) {
        db.refRights().delete(Collections.singleton(m));
      }
    }
    projectCache.evictAll();
    return VoidResult.INSTANCE;
  }

  private RefControl controlForRef(ProjectControl p, String ref) {
    if (ref.endsWith("/*")) {
      ref = ref.substring(0, ref.length() - 1);
    }
    return p.controlForRef(ref);
  }
}
