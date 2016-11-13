// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gwtorm.server.OrmException;
import java.util.Collection;

/**
 * Store for reviewed flags on changes.
 *
 * <p>A reviewed flag is a tuple of (patch set ID, file, account ID) and records whether the user
 * has reviewed a file in a patch set. Each user can easily have thousands of reviewed flags and the
 * number of reviewed flags is growing without bound. The store must be able handle this data volume
 * efficiently.
 *
 * <p>For a multi-master setup the store must replicate the data between the masters.
 */
public interface AccountPatchReviewStore {
  /**
   * Marks the given file in the given patch set as reviewed by the given user.
   *
   * @param psId patch set ID
   * @param accountId account ID of the user
   * @param path file path
   * @return {@code true} if the reviewed flag was updated, {@code false} if the reviewed flag was
   *     already set
   * @throws OrmException thrown if updating the reviewed flag failed
   */
  boolean markReviewed(PatchSet.Id psId, Account.Id accountId, String path) throws OrmException;

  /**
   * Marks the given files in the given patch set as reviewed by the given user.
   *
   * @param psId patch set ID
   * @param accountId account ID of the user
   * @param paths file paths
   * @throws OrmException thrown if updating the reviewed flag failed
   */
  void markReviewed(PatchSet.Id psId, Account.Id accountId, Collection<String> paths)
      throws OrmException;

  /**
   * Clears the reviewed flag for the given file in the given patch set for the given user.
   *
   * @param psId patch set ID
   * @param accountId account ID of the user
   * @param path file path
   * @throws OrmException thrown if clearing the reviewed flag failed
   */
  void clearReviewed(PatchSet.Id psId, Account.Id accountId, String path) throws OrmException;

  /**
   * Clears the reviewed flags for all files in the given patch set for all users.
   *
   * @param psId patch set ID
   * @throws OrmException thrown if clearing the reviewed flags failed
   */
  void clearReviewed(PatchSet.Id psId) throws OrmException;

  /**
   * Returns the paths of all files in the given patch set the have been reviewed by the given user.
   *
   * @param psId patch set ID
   * @param accountId account ID of the user
   * @return the paths of all files in the given patch set the have been reviewed by the given user
   * @throws OrmException thrown if accessing the reviewed flags failed
   */
  Collection<String> findReviewed(PatchSet.Id psId, Account.Id accountId) throws OrmException;
}
