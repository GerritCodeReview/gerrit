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

import com.google.gwtorm.server.OrmDuplicateKeyException;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/**
 * A database accessor for write calls related to checkers.
 *
 * <p>All calls which write checker related details to the database are gathered here. Other classes
 * should always use this interface instead of accessing the database directly.
 *
 * <p>This is an interface so that the implementation can be swapped if needed.
 *
 * <p>Callers should use the {@link com.google.gerrit.server.UserInitiated} annotation or the {@link
 * com.google.gerrit.server.ServerInitiated} annotation on a provider of a {@code CheckersUpdate} to
 * get access to a {@code CheckersUpdate} instance.
 */
public interface CheckersUpdate {
  /**
   * Creates the specified checker.
   *
   * @param checkerCreation an {@code CheckerCreation} which specifies all mandatory properties of
   *     the checker
   * @param checkerUpdate an {@code CheckerUpdate} which specifies optional properties of the
   *     checker. If this {@code CheckerUpdate} updates a property which was already specified by
   *     the {@code CheckerCreation}, the value of this {@code CheckerUpdate} wins.
   * @throws OrmDuplicateKeyException if a checker with the chosen UUID already exists
   * @throws IOException if an error occurs while reading/writing from/to storage
   * @throws ConfigInvalidException if a checker with the same UUID already exists but can't be read
   *     due to an invalid format
   * @return the created {@code Checker}
   */
  Checker createChecker(CheckerCreation checkerCreation, CheckerUpdate checkerUpdate)
      throws OrmDuplicateKeyException, IOException, ConfigInvalidException;

  /**
   * Updates the specified checker.
   *
   * @param checkerUuid the UUID of the checker to update
   * @param checkerUpdate an {@code CheckerUpdate} which indicates the desired updates on the
   *     checker
   * @throws NoSuchCheckerException if the specified checker doesn't exist
   * @throws IOException if an error occurs while reading/writing from/to storage
   * @throws ConfigInvalidException if the existing checker config is invalid
   * @return the updated {@code Checker}
   */
  Checker updateChecker(String checkerUuid, CheckerUpdate checkerUpdate)
      throws NoSuchCheckerException, IOException, ConfigInvalidException;
}
