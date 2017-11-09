// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.group.db;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.MetaDataUpdate;
import java.io.IOException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Repository;

@FunctionalInterface
public interface MetaDataUpdateFactory {
  /**
   * Creates a {@link MetaDataUpdate} for the given project and repository.
   *
   * <p>The {@link CommitBuilder} of the returned {@link MetaDataUpdate} must have author and
   * committer set.
   *
   * @param projectName The project for which meta data should be updated.
   * @param repository the repository to update. The caller is responsible for closing the
   *     repository.
   * @return A new {@link MetaDataUpdate} instance for the given project.
   */
  default MetaDataUpdate create(Project.NameKey projectName, Repository repository)
      throws IOException {
    return create(projectName, repository, null);
  }

  /**
   * Creates a {@link MetaDataUpdate}.
   *
   * <p>The {@link CommitBuilder} of the returned {@link MetaDataUpdate} must have author and
   * committer set.
   *
   * @param projectName the name of the project for which meta data should be updated
   * @param repository the repository to update. The caller is responsible for closing the
   *     repository.
   * @param batchRefUpdate a {@code BatchRefUpdate} to combine multiple ref updates. The caller is
   *     responsible for executing the {@code BatchRefUpdate}.
   * @return a new {@link MetaDataUpdate} instance
   */
  MetaDataUpdate create(
      Project.NameKey projectName, Repository repository, @Nullable BatchRefUpdate batchRefUpdate)
      throws IOException;
}
