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

package com.google.gerrit.server.notedb;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.PerThreadCache;
import com.google.gerrit.server.cache.ThreadLocalCacheCleaner;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.RepoRefCache;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;

/** Thread-local cache of repository refs, intended for NoteDb meta refs. */
class ThreadLocalRepoRefCache {
  private final boolean cacheEnabled;

  @Inject
  ThreadLocalRepoRefCache(@GerritServerConfig Config gerritConfig) {
    cacheEnabled = gerritConfig.getBoolean("notedb", "cacheMetaRef", false);
  }

  /**
   * Return the refs cache associated to a project
   *
   * @param projectName project name associated with the repository
   * @param repo repository to use for refs lookups
   * @return {@link RepoRefCache} associated with the project / repo
   */
  RepoRefCache get(Project.NameKey projectName, Repository repo) {
    if (!cacheEnabled) {
      return new RepoRefCache(repo);
    }

    if (PerThreadCache.get() == null) {
      @SuppressWarnings("unused")
      PerThreadCache unused = PerThreadCache.create();
      ThreadLocalCacheCleaner.get().registerCleaner(this::clear);
    }

    return PerThreadCache.getOrCompute(
        PerThreadCache.Key.create(RepoRefCache.class, projectName), () -> new RepoRefCache(repo));
  }

  private void clear() {
    if (cacheEnabled) {
      PerThreadCache.get().close();
    }
  }
}
