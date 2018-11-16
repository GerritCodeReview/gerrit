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

package com.google.gerrit.server.schema;

import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Schema upgrade implementation.
 *
 * <p>Implementations must define a single public constructor that takes an {@link Arguments}. The
 * recommended idiom is to pull out whichever individual fields from the {@code Arguments} are
 * required by this implementation.
 */
interface NoteDbSchemaVersion {
  @Singleton
  class Arguments {
    final GitRepositoryManager repoManager;
    final AllProjectsName allProjects;

    @Inject
    Arguments(GitRepositoryManager repoManager, AllProjectsName allProjects) {
      this.repoManager = repoManager;
      this.allProjects = allProjects;
    }
  }

  void upgrade(UpdateUI ui) throws Exception;
}
