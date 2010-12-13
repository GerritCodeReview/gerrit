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

package com.google.gerrit.pgm.util;

import static java.util.concurrent.TimeUnit.HOURS;

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.reviewdb.Project.Status;
import com.google.gerrit.server.config.PruneProjectsDelayConfig;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.project.PerformDeleteProject;
import com.google.gerrit.server.project.PerformDeleteProjectImpl;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class DeleteNonEmptyProjects implements Runnable {
  private static final Logger log =
      LoggerFactory.getLogger(DeleteNonEmptyProjects.class);

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      listener().to(Lifecycle.class);
    }
  }

  static class Lifecycle implements LifecycleListener {
    private final WorkQueue queue;
    private final DeleteNonEmptyProjects prune;

    @Inject
    Lifecycle(final WorkQueue queue, final DeleteNonEmptyProjects prune) {
      this.queue = queue;
      this.prune = prune;
    }

    @Override
    public void start() {
      queue.getDefaultQueue().scheduleWithFixedDelay(prune, 1, 24, HOURS);
    }

    @Override
    public void stop() {
    }
  }

  private final SchemaFactory<ReviewDb> schemaFactory;
  private final PerformDeleteProjectImpl.Factory performDeleteProject;
  private final PruneProjectsDelayConfig pruneProjectsDelayConfig;
  private ReviewDb db;

  @Inject
  DeleteNonEmptyProjects(final SchemaFactory<ReviewDb> sf,
      final PerformDeleteProjectImpl.Factory performDeleteProject,
      final PruneProjectsDelayConfig dp) {
    this.schemaFactory = sf;
    this.performDeleteProject = performDeleteProject;
    pruneProjectsDelayConfig = dp;
  }

  @Override
  public void run() {
    try {
      if (db == null) {
        db = schemaFactory.open();
      }
      final List<NameKey> projectsToDelete = new ArrayList<NameKey>();
      final Timestamp current = new Timestamp(System.currentTimeMillis());
      final long currentTimeMilliseconds = current.getTime();

      for (Project p : db.projects().byStatus(Status.PRUNE.getCode())) {
        final long lastUpdatedOnMilliseconds = p.getLastUpdatedOn().getTime();
        final long diff = currentTimeMilliseconds - lastUpdatedOnMilliseconds;
        final long diffDays = diff / (24 * 60 * 60 * 1000);

        // Projects will be put out of database and off disk after a delay of
        // one or more days in Prune status.
        // This delay is set in gerrit.config and considers it had not been
        // updated for one or more days at least. The default value is a delay
        // of 5 days.
        if (diffDays > pruneProjectsDelayConfig.getPruneProjectsDelay()) {
          projectsToDelete.add(p.getNameKey());
        }
      }

      final PerformDeleteProject perfDeleteProject =
          performDeleteProject.create(projectsToDelete);

      perfDeleteProject.deleteProjects();

    } catch (OrmException e) {
      log.error("Could not open database.", e);
    } finally {
      db.close();
      db = null;
    }
  }
}
