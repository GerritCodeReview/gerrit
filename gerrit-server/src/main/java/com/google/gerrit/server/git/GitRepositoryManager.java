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

import com.google.gerrit.reviewdb.client.Project;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.SortedSet;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;

/**
 * Manages Git repositories for the Gerrit server process.
 *
 * <p>Implementations of this interface should be a {@link Singleton} and registered in Guice so
 * they are globally available within the server environment.
 */
public interface GitRepositoryManager {
  /**
   * Get (or open) a repository by name.
   *
   * @param name the repository name, relative to the base directory.
   * @return the cached Repository instance. Caller must call {@code close()} when done to decrement
   *     the resource handle.
   * @throws RepositoryNotFoundException the name does not denote an existing repository.
   * @throws IOException the name cannot be read as a repository.
   */
  Repository openRepository(Project.NameKey name) throws RepositoryNotFoundException, IOException;

  /**
   * Create (and open) a repository by name.
   *
   * @param name the repository name, relative to the base directory.
   * @return the cached Repository instance. Caller must call {@code close()} when done to decrement
   *     the resource handle.
   * @throws RepositoryCaseMismatchException the name collides with an existing repository name, but
   *     only in case of a character within the name.
   * @throws RepositoryNotFoundException the name is invalid.
   * @throws IOException the repository cannot be created.
   */
  Repository createRepository(Project.NameKey name)
      throws RepositoryCaseMismatchException, RepositoryNotFoundException, IOException;

  /** @return set of all known projects, sorted by natural NameKey order. */
  SortedSet<Project.NameKey> list();

  /**
   * Read the {@code GIT_DIR/description} file for gitweb.
   *
   * <p>NB: This code should really be in JGit, as a member of the Repository object. Until it moves
   * there, its here.
   *
   * @param name the repository name, relative to the base directory.
   * @return description text; null if no description has been configured.
   * @throws RepositoryNotFoundException the named repository does not exist.
   * @throws IOException the description file exists, but is not readable by this process.
   */
  String getProjectDescription(Project.NameKey name)
      throws RepositoryNotFoundException, IOException;

  /**
   * Set the {@code GIT_DIR/description} file for gitweb.
   *
   * <p>NB: This code should really be in JGit, as a member of the Repository object. Until it moves
   * there, its here.
   *
   * @param name the repository name, relative to the base directory.
   * @param description new description text for the repository.
   */
  void setProjectDescription(Project.NameKey name, final String description);
}
