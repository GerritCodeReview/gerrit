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

import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.verifier.Verifier;
import com.google.gerrit.server.verifier.Verifiers;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

/** Class to read verifiers from NoteDb. */
public class NoteDbVerifiers implements Verifiers {
  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;

  @Inject
  NoteDbVerifiers(GitRepositoryManager repoManager, AllProjectsName allProjectsName) {
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
  }

  @Override
  public Optional<Verifier> get(String verifierUuid) throws IOException, ConfigInvalidException {
    try (Repository allProjectsRepo = repoManager.openRepository(allProjectsName)) {
      VerifierConfig verifierConfig =
          VerifierConfig.loadForVerifier(allProjectsName, allProjectsRepo, verifierUuid);
      return verifierConfig.getLoadedVerifier();
    }
  }
}
