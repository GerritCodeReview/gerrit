// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.group;

import com.google.gerrit.entities.AccountGroup;

/**
 * An aggregation of operations on groups for test purposes.
 *
 * <p>To execute the operations, no Gerrit permissions are necessary.
 *
 * <p><strong>Note:</strong> This interface is not implemented using the REST or extension API.
 * Hence, it cannot be used for testing those APIs.
 */
public interface GroupOperations {
  /**
   * Starts the fluent chain for querying or modifying a group. Please see the methods of {@link
   * PerGroupOperations} for details on possible operations.
   *
   * @return an aggregation of operations on a specific group
   */
  PerGroupOperations group(AccountGroup.UUID groupUuid);

  /**
   * Starts the fluent chain to create a group. The returned builder can be used to specify the
   * attributes of the new group. To create the group for real, {@link
   * TestGroupCreation.Builder#create()} must be called.
   *
   * <p>Example:
   *
   * <pre>
   * AccountGroup.UUID createdGroupUuid = groupOperations
   *     .newGroup()
   *     .name("verifiers")
   *     .description("All verifiers of this server")
   *     .create();
   * </pre>
   *
   * <p><strong>Note:</strong> If another group with the provided name already exists, the creation
   * of the group will fail.
   *
   * @return a builder to create the new group
   */
  TestGroupCreation.Builder newGroup();

  /** An aggregation of methods on a specific group. */
  interface PerGroupOperations {

    /**
     * Checks whether the group exists.
     *
     * @return {@code true} if the group exists
     */
    boolean exists();

    /**
     * Retrieves the group.
     *
     * <p><strong>Note:</strong> This call will fail with an exception if the requested group
     * doesn't exist. If you want to check for the existence of a group, use {@link #exists()}
     * instead.
     *
     * @return the corresponding {@code TestGroup}
     */
    TestGroup get();

    /**
     * Starts the fluent chain to update a group. The returned builder can be used to specify how
     * the attributes of the group should be modified. To update the group for real, {@link
     * TestGroupUpdate.Builder#update()} must be called.
     *
     * <p>Example:
     *
     * <pre>
     * groupOperations.forUpdate().description("Another description for this group").update();
     * </pre>
     *
     * <p><strong>Note:</strong> The update will fail with an exception if the group to update
     * doesn't exist. If you want to check for the existence of a group, use {@link #exists()}.
     *
     * @return a builder to update the group
     */
    TestGroupUpdate.Builder forUpdate();
  }
}
