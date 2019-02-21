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

package com.google.gerrit.plugins.checks.db;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.plugins.checks.Checker;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.plugins.checks.Checkers;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** Class to read checkers from NoteDb. */
@Singleton
class NoteDbCheckers implements Checkers {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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

  @Override
  public ImmutableList<Checker> listCheckers() throws IOException {
    try (Repository allProjectsRepo = repoManager.openRepository(allProjectsName)) {
      List<Ref> checkerRefs =
          allProjectsRepo.getRefDatabase().getRefsByPrefix(CheckerRef.REFS_CHECKERS);
      ImmutableList<String> sortedCheckerUuids =
          checkerRefs
              .stream()
              .map(CheckerUuid::fromRef)
              .flatMap(Streams::stream)
              .sorted()
              .collect(toImmutableList());
      ImmutableList.Builder<Checker> sortedCheckers = ImmutableList.builder();
      for (String checkerUuid : sortedCheckerUuids) {
        try {
          CheckerConfig checkerConfig =
              CheckerConfig.loadForChecker(allProjectsName, allProjectsRepo, checkerUuid);
          checkerConfig.getLoadedChecker().ifPresent(sortedCheckers::add);
        } catch (ConfigInvalidException e) {
          logger.atWarning().withCause(e).log(
              "Ignore invalid checker %s on listing checkers", checkerUuid);
        }
      }
      return sortedCheckers.build();
    }
  }

  @Override
  public ImmutableSet<Checker> checkersOf(Project.NameKey repositoryName)
      throws IOException, ConfigInvalidException {
    try (Repository allProjectsRepo = repoManager.openRepository(allProjectsName)) {
      ImmutableSet<String> checkerUuids =
          CheckersByRepositoryNotes.load(allProjectsName, allProjectsRepo).get(repositoryName);

      ImmutableSet.Builder<Checker> checkers = ImmutableSet.builder();
      for (String checkerUuid : checkerUuids) {
        try {
          CheckerConfig checkerConfig =
              CheckerConfig.loadForChecker(allProjectsName, allProjectsRepo, checkerUuid);
          checkerConfig.getLoadedChecker().ifPresent(checkers::add);
        } catch (ConfigInvalidException e) {
          logger.atWarning().withCause(e).log(
              "Ignore invalid checker %s on listing checkers for repository %s",
              checkerUuid, repositoryName);
        }
      }
      return checkers.build();
    }
  }
}
