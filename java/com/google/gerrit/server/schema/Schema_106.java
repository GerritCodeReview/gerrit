// Copyright (C) 2015 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.config.GerritPersonIdent;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

public class Schema_106 extends SchemaVersion {
  // we can use multiple threads per CPU as we can expect that threads will be
  // waiting for IO
  private static final int THREADS_PER_CPU = 4;
  private final GitRepositoryManager repoManager;
  private final PersonIdent serverUser;

  @Inject
  Schema_106(
      Provider<Schema_105> prior,
      GitRepositoryManager repoManager,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.repoManager = repoManager;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    if (!(repoManager instanceof LocalDiskRepositoryManager)) {
      return;
    }

    ui.message("listing all repositories ...");
    SortedSet<Project.NameKey> repoList = repoManager.list();
    ui.message("done");

    ui.message(String.format("creating reflog files for %s branches ...", RefNames.REFS_CONFIG));

    ExecutorService executorPool = createExecutor(ui, repoList.size());
    List<Future<Void>> futures = new ArrayList<>();

    for (Project.NameKey project : repoList) {
      Callable<Void> callable = new ReflogCreator(project);
      futures.add(executorPool.submit(callable));
    }

    executorPool.shutdown();
    try {
      for (Future<Void> future : futures) {
        try {
          future.get();
        } catch (ExecutionException e) {
          ui.message(e.getCause().getMessage());
        }
      }
      ui.message("done");
    } catch (InterruptedException ex) {
      String msg =
          String.format(
              "Migration step 106 was interrupted. "
                  + "Reflog created in %d of %d repositories only.",
              countDone(futures), repoList.size());
      ui.message(msg);
    }
  }

  private static int countDone(List<Future<Void>> futures) {
    int count = 0;
    for (Future<Void> future : futures) {
      if (future.isDone()) {
        count++;
      }
    }

    return count;
  }

  private ExecutorService createExecutor(UpdateUI ui, int repoCount) {
    int procs = Runtime.getRuntime().availableProcessors();
    int threads = Math.min(procs * THREADS_PER_CPU, repoCount);
    ui.message(String.format("... using %d threads ...", threads));
    return Executors.newFixedThreadPool(threads);
  }

  private class ReflogCreator implements Callable<Void> {
    private final Project.NameKey project;

    ReflogCreator(Project.NameKey project) {
      this.project = project;
    }

    @Override
    public Void call() throws IOException {
      try (Repository repo = repoManager.openRepository(project)) {
        File metaConfigLog = new File(repo.getDirectory(), "logs/" + RefNames.REFS_CONFIG);
        if (metaConfigLog.exists()) {
          return null;
        }

        if (!metaConfigLog.getParentFile().mkdirs() || !metaConfigLog.createNewFile()) {
          throw new IOException();
        }

        ObjectId metaConfigId = repo.resolve(RefNames.REFS_CONFIG);
        if (metaConfigId != null) {
          try (PrintWriter writer = new PrintWriter(metaConfigLog, UTF_8.name())) {
            writer.print(ObjectId.zeroId().name());
            writer.print(" ");
            writer.print(metaConfigId.name());
            writer.print(" ");
            writer.print(serverUser.toExternalString());
            writer.print("\t");
            writer.print("create reflog");
            writer.println();
          }
        }
        return null;
      } catch (IOException e) {
        throw new IOException(
            String.format(
                "ERROR: Failed to create reflog file for the %s branch in repository %s",
                RefNames.REFS_CONFIG, project.get()));
      }
    }
  }
}
