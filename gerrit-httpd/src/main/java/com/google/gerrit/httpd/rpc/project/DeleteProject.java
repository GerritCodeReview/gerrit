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
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** This class performs the deletion of an empty (has no changes) project. */
public class DeleteProject extends Handler<VoidResult> {

  interface Factory {
    DeleteProject create(@Assisted Project.NameKey name);
  }

  private static final Logger log =
      LoggerFactory.getLogger(DeleteProject.class);

  private final ProjectControl.Factory projectControlFactory;
  private final ReviewDb db;
  private final ProjectCache projectCache;
  private final CurrentUser user;
  private final GitRepositoryManager repoManager;
  private final ReplicationQueue replication;
  private final Project.NameKey projectName;

  @Inject
  DeleteProject(final ProjectControl.Factory projectControlFactory,
      final GitRepositoryManager repoManager,
      final ReplicationQueue replication, final CurrentUser user,
      final ReviewDb db, final ProjectCache projectCache,
      @Assisted Project.NameKey name) {
    this.projectControlFactory = projectControlFactory;
    this.repoManager = repoManager;
    this.replication = replication;
    this.projectName = name;
    this.db = db;
    this.projectCache = projectCache;
    this.user = user;
  }

  @Override
  public VoidResult call() throws NoSuchProjectException, OrmException,
      SecurityException, IOException {
    final ProjectControl projectControl =
        projectControlFactory.controlFor(projectName);

    if (user.isAdministrator() || projectControl.isOwner()) {
      // Remove Git Repository.
      repoManager.deleteRepository(projectName.get());

      // Remove all added RefRights.
      for (final RefRight k : db.refRights().byProject(projectName)) {
        db.refRights().delete(Collections.singleton(k));
      }

      // Remove all interests in this project.
      for (final AccountProjectWatch a : db.accountProjectWatches().byProject(
          projectName)) {
        db.accountProjectWatches().delete(Collections.singleton(a));
      }

      // Remove parent relationship
      for (final Project j : db.projects().getChildren(projectName.get())) {
        j.setParent(null);
        db.projects().update(Collections.singleton(j));
        projectCache.evict(j);
      }

      // Remove project record.
      db.projects().deleteKeys(Collections.singleton(projectName));

      replication.replicateProjectDeletion(projectName);

      projectCache.evict(projectControl.getProject());
    }

    return VoidResult.INSTANCE;
  }
}
