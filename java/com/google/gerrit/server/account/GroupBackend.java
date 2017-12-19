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
import com.google.gerrit.server.project.ProjectState;
import java.util.Collection;

/** Implementations of GroupBackend provide lookup and membership accessors to a account system. */
@ExtensionPoint
public interface GroupBackend {
  /** @return {@code true} if the backend can operate on the UUID. */
  boolean handles(AccountGroup.UUID uuid);

  /**
   * Looks up a account in the backend. If the account does not exist, null is returned.
   *
   * @param uuid the account identifier
   * @return the account
   */
  @Nullable
  GroupDescription.Basic get(AccountGroup.UUID uuid);

  /** @return suggestions for the account name sorted by name. */
  Collection<GroupReference> suggest(String name, @Nullable ProjectState project);

  /** @return the account membership checker for the backend. */
  GroupMembership membershipsOf(IdentifiedUser user);

  /** @return {@code true} if the account with the given UUID is visible to all registered users. */
  boolean isVisibleToAll(AccountGroup.UUID uuid);
}
