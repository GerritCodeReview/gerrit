// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;

import java.io.IOException;

@Singleton
public class FilesystemDeleteHandler {
  private final GitRepositoryManager repoManager;

  @Inject
  public FilesystemDeleteHandler(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  public void nukeTheWorld()
      throws IOException {
    for (Project.NameKey p : repoManager.list()) {
      delete(p);
    }
  }

  public void delete(Project.NameKey project)
      throws IOException, RepositoryNotFoundException {
    Repository repository = repoManager.openRepository(project);
    cleanCache(repository);
    if (repository.getDirectory() != null) {
      throw new IllegalStateException("only deleting of memory " +
          "based repositories is supported");
    }

    repoManager.wipeOut(project);
  }

  private static void cleanCache(Repository repository) {
    repository.close();
    RepositoryCache.close(repository);
  }
}