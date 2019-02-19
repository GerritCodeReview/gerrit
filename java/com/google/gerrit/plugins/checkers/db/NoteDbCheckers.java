// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checkers.db;

import com.google.gerrit.plugins.checkers.Checker;
import com.google.gerrit.plugins.checkers.CheckerUuid;
import com.google.gerrit.plugins.checkers.Checkers;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

/** Class to read checkers from NoteDb. */
@Singleton
class NoteDbCheckers implements Checkers {
  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;

  @Inject
  NoteDbCheckers(GitRepositoryManager repoManager, AllProjectsName allProjectsName) {
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
  }

  @Override
  public Optional<Checker> getChecker(String checkerUuid)
      throws IOException, ConfigInvalidException {
    if (!CheckerUuid.isUuid(checkerUuid)) {
      return Optional.empty();
    }

    try (Repository allProjectsRepo = repoManager.openRepository(allProjectsName)) {
      CheckerConfig checkerConfig =
          CheckerConfig.loadForChecker(allProjectsName, allProjectsRepo, checkerUuid);
      return checkerConfig.getLoadedChecker();
    }
  }
}
