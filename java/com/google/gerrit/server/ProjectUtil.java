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

import static com.google.gerrit.reviewdb.client.RefNames.isConfigRef;

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.io.IOException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

public class ProjectUtil {

  /**
   * Checks whether the specified branch exists.
   *
   * @param repoManager Git repository manager to open the git repository
   * @param branch the branch for which it should be checked if it exists
   * @return {@code true} if the specified branch exists or if {@code HEAD} points to this branch,
   *     otherwise {@code false}
   * @throws RepositoryNotFoundException the repository of the branch's project does not exist.
   * @throws IOException error while retrieving the branch from the repository.
   */
  public static boolean branchExists(final GitRepositoryManager repoManager, Branch.NameKey branch)
      throws RepositoryNotFoundException, IOException {
    try (Repository repo = repoManager.openRepository(branch.getParentKey())) {
      boolean exists = repo.getRefDatabase().exactRef(branch.get()) != null;
      if (!exists) {
        exists = repo.getFullBranch().equals(branch.get()) || isConfigRef(branch.get());
      }
      return exists;
    }
  }
}
