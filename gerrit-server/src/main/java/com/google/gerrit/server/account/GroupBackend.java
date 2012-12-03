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

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectControl;

import java.util.Collection;

import javax.annotation.Nullable;

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

  /** @return the group membership checker for the backend. */
  GroupMembership membershipsOf(IdentifiedUser user);
}
