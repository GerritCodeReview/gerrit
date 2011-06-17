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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

final class SingleGroupUser extends CurrentUser {
  private final Set<AccountGroup.UUID> groups;

  SingleGroupUser(CapabilityControl.Factory capabilityControlFactory,
      AccountGroup.UUID groupId) {
    this(capabilityControlFactory, Collections.singleton(groupId));
  }

  SingleGroupUser(CapabilityControl.Factory capabilityControlFactory,
      Set<AccountGroup.UUID> groups) {
    super(capabilityControlFactory, AccessPath.UNKNOWN);
    this.groups = groups;
  }

  @Override
  public Set<AccountGroup.UUID> getEffectiveGroups() {
    return groups;
  }

  @Override
  public Set<Change.Id> getStarredChanges() {
    return Collections.emptySet();
  }

  @Override
  public Collection<AccountProjectWatch> getNotificationFilters() {
    return Collections.emptySet();
  }
}
