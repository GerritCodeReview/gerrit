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

package com.google.gerrit.server;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.git.GitRepositoryManager;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

public class ProjectUtil {

  /**
   * Checks whether the specified branch exists.
   *
   * @param repoManager Git repository manager to open the git repository
   * @param branch the branch for which it should be checked if it exists
   * @return <code>true</code> if the specified branch exists, otherwise
   *         <code>false</code>
   * @throws RepositoryNotFoundException the repository of the branch's project
   *         does not exist.
   * @throws IOException error while retrieving the branch from the repository.
   */
  public static boolean branchExists(final GitRepositoryManager repoManager,
      final Branch.NameKey branch) throws RepositoryNotFoundException,
      IOException {
    final Repository repo = repoManager.openRepository(branch.getParentKey());
    try {
      return repo.getRef(branch.get()) != null;
    } finally {
      repo.close();
    }
  }
}
