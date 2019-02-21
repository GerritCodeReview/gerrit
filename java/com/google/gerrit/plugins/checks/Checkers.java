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

package com.google.gerrit.plugins.checks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Project;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * A database accessor for read calls related to checkers.
 *
 * <p>All calls which read checker related details from the database are gathered here. Other
 * classes should always use this class instead of accessing the database directly.
 *
 * <p>This is an interface so that the implementation can be swapped if needed.
 */
public interface Checkers {
  /**
   * Returns the checker for the given UUID.
   *
   * <p>If no checker with the given UUID exists, {@link Optional#empty()} is returned.
   *
   * @param checkerUuid the checker UUID
   * @return the checker, {@link Optional#empty()} if no checker with the given UUID exists
   * @throws IOException if the checker couldn't be retrieved from the storage
   * @throws ConfigInvalidException if the checker in the storage is invalid
   */
  Optional<Checker> getChecker(String checkerUuid) throws IOException, ConfigInvalidException;

  /**
   * Returns a list with all checkers.
   *
   * <p>Checkers with invalid configuration are silently ignored.
   *
   * @return all checkers, sorted by UUID
   * @throws IOException if any checker couldn't be retrieved from the storage
   */
  ImmutableList<Checker> listCheckers() throws IOException;

  /**
   * Returns the checkers that apply to the given repository.
   *
   * <p>Checkers with invalid configuration are silently ignored.
   *
   * @param repositoryName the name of the repository for which the applying checkers should be
   *     returned
   * @return the checkers that apply that apply to the given repository
   * @throws IOException if reading the checker list fails or if any checker couldn't be retrieved
   *     from the storage
   * @throws ConfigInvalidException if reading the checker list fails
   */
  ImmutableSet<Checker> checkersOf(Project.NameKey repositoryName)
      throws IOException, ConfigInvalidException;
}
