// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.notedb.rebuild;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_GC_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_AUTO;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GarbageCollectionResult;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.server.git.GarbageCollection;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class GcAllUsers {
  private static final Logger log = LoggerFactory.getLogger(GcAllUsers.class);

  private final AllUsersName allUsers;
  private final GarbageCollection.Factory gcFactory;
  private final GitRepositoryManager repoManager;

  @Inject
  GcAllUsers(
      AllUsersName allUsers,
      GarbageCollection.Factory gcFactory,
      GitRepositoryManager repoManager) {
    this.allUsers = allUsers;
    this.gcFactory = gcFactory;
    this.repoManager = repoManager;
  }

  public void runWithLogger() {
    // Print log messages using logger, and skip progress.
    run(s -> log.info(s), null);
  }

  public void run(PrintWriter writer) {
    // Print both log messages and progress to given writer.
    run(checkNotNull(writer)::println, writer);
  }

  private void run(Consumer<String> logOneLine, @Nullable PrintWriter progressWriter) {
    if (!(repoManager instanceof LocalDiskRepositoryManager)) {
      logOneLine.accept("Skipping GC of " + allUsers + "; not a local disk repo");
      return;
    }
    if (!enableAutoGc(logOneLine)) {
      logOneLine.accept(
          "Skipping GC of "
              + allUsers
              + " due to disabling "
              + CONFIG_GC_SECTION
              + "."
              + CONFIG_KEY_AUTO);
      logOneLine.accept(
          "If loading accounts is slow after the NoteDb migration, run `git gc` on "
              + allUsers
              + " manually");
      return;
    }

    if (progressWriter == null) {
      // Mimic log line from GarbageCollection.
      logOneLine.accept("collecting garbage for \"" + allUsers + "\":\n");
    }
    GarbageCollectionResult result =
        gcFactory.create().run(ImmutableList.of(allUsers), progressWriter);
    if (!result.hasErrors()) {
      return;
    }
    for (GarbageCollectionResult.Error e : result.getErrors()) {
      switch (e.getType()) {
        case GC_ALREADY_SCHEDULED:
          logOneLine.accept("GC already scheduled for " + e.getProjectName());
          break;
        case GC_FAILED:
          logOneLine.accept("GC failed for " + e.getProjectName());
          break;
        case REPOSITORY_NOT_FOUND:
          logOneLine.accept(e.getProjectName() + " repo not found");
          break;
        default:
          logOneLine.accept("GC failed for " + e.getProjectName() + ": " + e.getType());
          break;
      }
    }
  }

  private boolean enableAutoGc(Consumer<String> logOneLine) {
    try (Repository repo = repoManager.openRepository(allUsers)) {
      return repo.getConfig().getInt(CONFIG_GC_SECTION, CONFIG_KEY_AUTO, -1) != 0;
    } catch (IOException e) {
      logOneLine.accept(
          "Error reading config for " + allUsers + ":\n" + Throwables.getStackTraceAsString(e));
      return false;
    }
  }
}
