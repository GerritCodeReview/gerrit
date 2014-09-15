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

package com.google.gerrit.server.account;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectControl;

import java.util.Collection;

/**
 * Implementations of GroupBackend provide lookup and membership accessors
 * to a group system.
 */
@ExtensionPoint
public interface GroupBackend {
  /** @return {@code true} if the backend can operate on the UUID. */
  boolean handles(AccountGroup.UUID uuid);

  /**
   * Looks up a group in the backend. If the group does not exist, null is
   * returned.
   *
   * @param uuid the group identifier
   * @return the group
   */
  @Nullable
  GroupDescription.Basic get(AccountGroup.UUID uuid);

  /** @return suggestions for the group name sorted by name. */
  Collection<GroupReference> suggest(
      String name,
      @Nullable ProjectControl project);

  /**
   * @param user the user the returned checker applies to
   * @return the project-independent group membership checker for the backend.
   *         The checker does not include project-dependent groups.
   *
   * @see GroupDescription.Basic#isProjectDependent()
   */
  GroupMembership membershipsOf(IdentifiedUser user);

  /**
   * @param project the project control the returned checker applies to
   * @return the project-dependent group membership checker for the backend.
   *         The checker does not include project-independent groups.
   *         This methods may be called only in a context where the project the
   *         created checker applies to is known. GroupBackends that do not
   *         handle any project-dependent groups may simply return an empty
   *         checker.
   *
   * @see GroupDescription.Basic#isProjectDependent
   */
  GroupMembership membershipsOf(ProjectControl project);
}
