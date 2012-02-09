// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.GC;
import org.eclipse.jgit.storage.file.GC.RepoStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class GarbageCollection {
  private static final Logger log = LoggerFactory
      .getLogger(GarbageCollection.class);

  public static final String LOG_NAME = "gc_log";
  private static final Logger gcLog = LoggerFactory.getLogger(LOG_NAME);


  private final GitRepositoryManager repoManager;
  private final IdentifiedUser currentUser;

  public interface Factory {
    GarbageCollection create();
  }

  @Inject
  GarbageCollection(final GitRepositoryManager repoManager,
      final IdentifiedUser currentUser) {
    this.repoManager = repoManager;
    this.currentUser = currentUser;
  }

  public GarbageCollectionResult run(final List<Project.NameKey> projectNames,
      final PrintWriter writer) {
    final GarbageCollectionResult result = new GarbageCollectionResult();
    if (!currentUser.getCapabilities().canRunGC()) {
      result.addError(new GarbageCollectionResult.Error(
          GarbageCollectionResult.Error.Type.GC_NOT_PERMITTED, currentUser
              .getUserName()));
      return result;
    }

    for (final Project.NameKey projectName : projectNames) {
      gcLog.info("starting garbage collection for \"" + projectName + "\"");
      writer.print("collecting garbage for \"" + projectName + "\":\n");
      try {
        final Repository repo = repoManager.openRepository(projectName);
        try {
          // once JGit provides a porcelain API for the GC, we could invoke the
          // GC like this (if the repository is not a FileRepository getting the
          // statistics and running the gc would fail with an
          // UnsupportedOperationException which we would need to handle):
//          final Git git = Git.wrap(repo);
//          logRepoStatistics(git.statistics().call());
//          final GcCommand gcCommand = git.gc();
//          gcCommand.setProgressMonitor(new TextProgressMonitor(writer));
//          gcCommand.setReturnStatistics(true);
//          final RepoStatistics statistics = gcCommand.call();
//          logRepoStatistics(statistics);


          // this is how we want to call the GC as long as there is no porcelain
          // API for the GC (currently it's not compiling because JGit needs to
          // be adapted first):
          if (!(repo instanceof FileRepository)) {
            logGcError(projectName,
                "garbage collection not supported for repository type: "
                    + repo.getClass().getName());
            result.addError(new GarbageCollectionResult.Error(
                GarbageCollectionResult.Error.Type.GC_NOT_SUPPORTED, projectName));
            continue;
          }

          GC gc = new GC((FileRepository) repo, new TextProgressMonitor(writer));
          logRepoStatistics(gc.getStatistics());
          gc.gc();
          logRepoStatistics(gc.getStatistics());
        } catch (IOException e) {
          logGcError(projectName, e);
          result.addError(new GarbageCollectionResult.Error(
              GarbageCollectionResult.Error.Type.GC_FAILED, projectName));
        } finally {
          repo.close();
        }
      } catch (RepositoryNotFoundException e) {
        logGcError(projectName, e);
        result.addError(new GarbageCollectionResult.Error(
            GarbageCollectionResult.Error.Type.REPOSITORY_NOT_FOUND,
            projectName));
      } finally {
        gcLog.info("finished garbage collection for \"" + projectName + "\"");
        writer.print("done.\n\n");
      }
    }
    return result;
  }

  private static void logGcError(final Project.NameKey projectName,
      final Exception e) {
    final String msg =
        "garbage collection for \"" + projectName.get() + "\" failed";
    gcLog.error(msg, e);
    log.error(msg, e);
  }

  private static void logGcError(final Project.NameKey projectName, String msg) {
    msg = "garbage collection for \"" + projectName.get() + "\" failed: " + msg;
    gcLog.error(msg);
    log.error(msg);
  }

  private static void logRepoStatistics(final Project.NameKey projectName,
      final RepoStatistics s) {
    final StringBuilder b = new StringBuilder();
    b.append("repository statistics for \"" + projectName.get() + "\": ");
    b.append("number of packed objects = ").append(s.nrOfPackedObjects);
    b.append("; number of pack files = ").append(s.nrOfPackFiles);
    b.append("; number of loose objects = ").append(s.nrOfLooseObjects);
    gcLog.info(b.toString());
  }
}
