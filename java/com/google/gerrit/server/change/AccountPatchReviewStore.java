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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSet;
import java.util.Collection;
import java.util.Optional;

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

  /** Represents patch set id with reviewed files. */
  @AutoValue
  abstract class PatchSetWithReviewedFiles {
    public abstract PatchSet.Id patchSetId();

    public abstract ImmutableSet<String> files();

    public static PatchSetWithReviewedFiles create(PatchSet.Id id, ImmutableSet<String> files) {
      return new AutoValue_AccountPatchReviewStore_PatchSetWithReviewedFiles(id, files);
    }
  }

  /**
   * Marks the given file in the given patch set as reviewed by the given user.
   *
   * @param psId patch set ID
   * @param accountId account ID of the user
   * @param path file path
   * @return {@code true} if the reviewed flag was updated, {@code false} if the reviewed flag was
   *     already set
   * @throws StorageException thrown if updating the reviewed flag failed
   */
  boolean markReviewed(PatchSet.Id psId, Account.Id accountId, String path) throws StorageException;

  /**
   * Marks the given files in the given patch set as reviewed by the given user.
   *
   * @param psId patch set ID
   * @param accountId account ID of the user
   * @param paths file paths
   * @throws StorageException thrown if updating the reviewed flag failed
   */
  void markReviewed(PatchSet.Id psId, Account.Id accountId, Collection<String> paths)
      throws StorageException;

  /**
   * Clears the reviewed flag for the given file in the given patch set for the given user.
   *
   * @param psId patch set ID
   * @param accountId account ID of the user
   * @param path file path
   * @throws StorageException thrown if clearing the reviewed flag failed
   */
  void clearReviewed(PatchSet.Id psId, Account.Id accountId, String path) throws StorageException;

  /**
   * Clears the reviewed flags for all files in the given patch set for all users.
   *
   * @param psId patch set ID
   * @throws StorageException thrown if clearing the reviewed flags failed
   */
  void clearReviewed(PatchSet.Id psId) throws StorageException;

  /**
   * Find the latest patch set, that is smaller or equals to the given patch set, where at least,
   * one file has been reviewed by the given user.
   *
   * @param psId patch set ID
   * @param accountId account ID of the user
   * @return optionally, all files the have been reviewed by the given user that belong to the patch
   *     set that is smaller or equals to the given patch set
   * @throws StorageException thrown if accessing the reviewed flags failed
   */
  Optional<PatchSetWithReviewedFiles> findReviewed(PatchSet.Id psId, Account.Id accountId)
      throws StorageException;
}
