// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.change;

import com.google.gerrit.entities.Change;

/**
 * An aggregation of operations on changes for test purposes.
 *
 * <p>To execute the operations, no Gerrit permissions are necessary.
 *
 * <p><strong>Note:</strong> This interface is not implemented using the REST or extension API.
 * Hence, it cannot be used for testing those APIs.
 */
public interface ChangeOperations {

  /**
   * Starts the fluent chain for querying or modifying a change. Please see the methods of {@link
   * PerChangeOperations} for details on possible operations.
   *
   * @return an aggregation of operations on a specific change
   */
  PerChangeOperations change(Change.Id changeId);

  /**
   * Starts the fluent chain to create a change. The returned builder can be used to specify the
   * attributes of the new change. To create the change for real, {@link
   * TestChangeCreation.Builder#create()} must be called.
   *
   * <p>Example:
   *
   * <pre>
   * Change.Id createdChangeId = changeOperations
   *     .newChange()
   *     .file("file1")
   *     .content("Line 1\nLine2\n")
   *     .create();
   * </pre>
   *
   * <p><strong>Note:</strong> There must be at least one existing user and repository.
   *
   * @return a builder to create the new change
   */
  TestChangeCreation.Builder newChange();

  /** An aggregation of methods on a specific change. */
  interface PerChangeOperations {

    /**
     * Checks whether the change exists.
     *
     * @return {@code true} if the change exists
     */
    boolean exists();

    /**
     * Retrieves the change.
     *
     * <p><strong>Note:</strong> This call will fail with an exception if the requested change
     * doesn't exist. If you want to check for the existence of a change, use {@link #exists()}
     * instead.
     *
     * @return the corresponding {@code TestChange}
     */
    TestChange get();
  }
}
