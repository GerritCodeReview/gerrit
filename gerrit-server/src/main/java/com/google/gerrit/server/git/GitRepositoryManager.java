// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;


/**
 * Manages Git repositories for the Gerrit server process.
 * <p>
 * Implementations of this interface should be a {@link Singleton} and
 * registered in Guice so they are globally available within the server
 * environment.
 */
public interface GitRepositoryManager {
  /**
   * Get (or open) a repository by name.
   *
   * @param name the repository name, relative to the base directory.
   * @return the cached Repository instance. Caller must call {@code #closeRepository(Repository)}
   *         when done to decrement the resource handle.
   * @throws RepositoryNotFoundException the name does not denote an existing
   *         repository, or the name cannot be read as a repository.
   */
  public abstract Repository openRepository(String name)
      throws RepositoryNotFoundException;

  /**
   * Create (and open) a repository by name.
   *
   * @param name the repository name, relative to the base directory.
   * @return the cached Repository instance. Caller must call {@code #closeRepository(Repository)}
   *         when done to decrement the resource handle.
   * @throws RepositoryNotFoundException the name does not denote an existing
   *         repository, or the name cannot be read as a repository.
   */
  public abstract Repository createRepository(String name)
      throws RepositoryNotFoundException;

  /**
   * Closes the given repository.
   *
   * @param repository the repository that should be closed
   */
  public abstract void closeRepository(Repository repo);

  /**
   * Renames the specified repository.
   *
   * @param name the name of the repository that should be renamed
   * @param newName the new name for the repository
   * @throws RepositoryNotFoundException the name does not denote an existing
   *         repository, or the name or the new name cannot be read as a repository
   * @throws RepositoryRenameException the renaming of the repository failed
   */
  public abstract void renameRepository(String name, String newName)
      throws RepositoryNotFoundException, RepositoryRenameException;

  /**
   * Read the {@code GIT_DIR/description} file for gitweb.
   * <p>
   * NB: This code should really be in JGit, as a member of the Repository
   * object. Until it moves there, its here.
   *
   * @param name the repository name, relative to the base directory.
   * @return description text; null if no description has been configured.
   * @throws RepositoryNotFoundException the named repository does not exist.
   * @throws IOException the description file exists, but is not readable by
   *         this process.
   */
  public abstract String getProjectDescription(final String name)
      throws RepositoryNotFoundException, IOException;

  /**
   * Set the {@code GIT_DIR/description} file for gitweb.
   * <p>
   * NB: This code should really be in JGit, as a member of the Repository
   * object. Until it moves there, its here.
   *
   * @param name the repository name, relative to the base directory.
   * @param description new description text for the repository.
   */
  public abstract void setProjectDescription(final String name,
      final String description);
}
