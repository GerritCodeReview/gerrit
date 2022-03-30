// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;

/**
 * Migrates the label configurations of all projects to copy conditions.
 *
 * @see MigrateLabelConfigToCopyCondition
 */
public class Schema_185 implements NoteDbSchemaVersion {
  private AtomicInteger i = new AtomicInteger();
  private Stopwatch sw = Stopwatch.createStarted();
  private int size;

  @Override
  public void upgrade(Arguments args, UpdateUI ui) throws Exception {
    ui.message("Migrating label configurations");

    NavigableSet<Project.NameKey> projects = args.repoManager.list();
    size = projects.size();

    Set<List<Project.NameKey>> batches = Sets.newHashSet(Iterables.partition(projects, 50));
    ExecutorService pool = createExecutor(ui);
    try {
      batches.stream()
          .forEach(
              batch -> {
                @SuppressWarnings("unused")
                Future<?> possiblyIgnoredError =
                    pool.submit(() -> processBatch(args.repoManager, args.serverUser, batch, ui));
              });
      pool.shutdown();
      pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    ui.message(
        String.format(
            "... (%.3f s) Migrated label configurations of all %d projects to schema 185",
            elapsed(), i.get()));
  }

  private ExecutorService createExecutor(UpdateUI ui) {
    int threads;
    try {
      threads = Integer.parseInt(System.getProperty("threadcount"));
    } catch (NumberFormatException e) {
      threads = Runtime.getRuntime().availableProcessors();
    }
    ui.message(String.format("... using %d threads ...", threads));
    return Executors.newFixedThreadPool(threads);
  }

  private void processBatch(
      GitRepositoryManager repoManager,
      PersonIdent serverUser,
      List<Project.NameKey> batch,
      UpdateUI ui) {
    try {
      for (Project.NameKey project : batch) {
        try {
          new MigrateLabelConfigToCopyCondition(repoManager, serverUser).execute(project);
          int count = i.incrementAndGet();
          showProgress(ui, count);
        } catch (ConfigInvalidException e) {
          ui.message(
              String.format(
                  "WARNING: Skipping migration of label configurations for project %s"
                      + " since its %s file is invalid: %s",
                  project, ProjectConfig.PROJECT_CONFIG, e.getMessage()));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(
          String.format("Failed to migrate batch of projects to schema 185: %s", batch), e);
    }
  }

  private double elapsed() {
    return sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
  }

  private void showProgress(UpdateUI ui, int count) {
    if (count % 100 == 0) {
      ui.message(
          String.format(
              "... (%.3f s) migrated label configurations of %d%% (%d/%d) projects",
              elapsed(), Math.round(100.0 * count / size), count, size));
    }
  }
}
