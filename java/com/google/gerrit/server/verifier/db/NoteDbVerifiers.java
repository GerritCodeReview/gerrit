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

package com.google.gerrit.server.verifier.db;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.verifier.Verifier;
import com.google.gerrit.server.verifier.VerifierUuid;
import com.google.gerrit.server.verifier.Verifiers;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** Class to read verifiers from NoteDb. */
@Singleton
class NoteDbVerifiers implements Verifiers {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;

  @Inject
  NoteDbVerifiers(GitRepositoryManager repoManager, AllProjectsName allProjectsName) {
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
  }

  @Override
  public Optional<Verifier> getVerifier(String verifierUuid)
      throws IOException, ConfigInvalidException {
    if (!VerifierUuid.isUuid(verifierUuid)) {
      return Optional.empty();
    }

    try (Repository allProjectsRepo = repoManager.openRepository(allProjectsName)) {
      VerifierConfig verifierConfig =
          VerifierConfig.loadForVerifier(allProjectsName, allProjectsRepo, verifierUuid);
      return verifierConfig.getLoadedVerifier();
    }
  }

  @Override
  public ImmutableList<Verifier> listVerifiers() throws IOException {
    try (Repository allProjectsRepo = repoManager.openRepository(allProjectsName)) {
      List<Ref> verifierRefs =
          allProjectsRepo.getRefDatabase().getRefsByPrefix(RefNames.REFS_VERIFIERS);
      ImmutableList<String> sortedVerifierUuids =
          verifierRefs
              .stream()
              .map(VerifierUuid::fromRef)
              .filter(Optional::isPresent)
              .map(Optional::get)
              .sorted()
              .collect(toImmutableList());
      ImmutableList.Builder<Verifier> sortedVerifiers = ImmutableList.builder();
      for (String verifierUuid : sortedVerifierUuids) {
        try {
          VerifierConfig verifierConfig =
              VerifierConfig.loadForVerifier(allProjectsName, allProjectsRepo, verifierUuid);
          verifierConfig.getLoadedVerifier().ifPresent(sortedVerifiers::add);
        } catch (ConfigInvalidException e) {
          logger.atWarning().withCause(e).log(
              "Ignore invalid verifier %s on listing verifiers", verifierUuid);
        }
      }
      return sortedVerifiers.build();
    }
  }

  @Override
  public ImmutableSet<Verifier> verifiersOf(Project.NameKey repositoryName)
      throws IOException, ConfigInvalidException {
    try (Repository allProjectsRepo = repoManager.openRepository(allProjectsName)) {
      ImmutableSet<String> verifierUuids =
          VerifiersByRepositoryNotes.load(allProjectsName, allProjectsRepo).get(repositoryName);

      ImmutableSet.Builder<Verifier> verifiers = ImmutableSet.builder();
      for (String verifierUuid : verifierUuids) {
        try {
          VerifierConfig verifierConfig =
              VerifierConfig.loadForVerifier(allProjectsName, allProjectsRepo, verifierUuid);
          verifierConfig.getLoadedVerifier().ifPresent(verifiers::add);
        } catch (ConfigInvalidException e) {
          logger.atWarning().withCause(e).log(
              "Ignore invalid verifier %s on listing verifiers for repository %s",
              verifierUuid, repositoryName);
        }
      }
      return verifiers.build();
    }
  }
}
