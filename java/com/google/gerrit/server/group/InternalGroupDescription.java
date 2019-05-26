// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.group;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import java.sql.Timestamp;

public class InternalGroupDescription implements GroupDescription.Internal {

  private final InternalGroup internalGroup;

  public InternalGroupDescription(InternalGroup internalGroup) {
    this.internalGroup = requireNonNull(internalGroup);
  }

  @Override
  public AccountGroup.UUID getGroupUUID() {
    return internalGroup.getGroupUUID();
  }

  @Override
  public String getName() {
    return internalGroup.getName();
  }

  @Nullable
  @Override
  public String getEmailAddress() {
    return null;
  }

  @Nullable
  @Override
  public String getUrl() {
    return "#" + PageLinks.toGroup(getGroupUUID());
  }

  @Override
  public AccountGroup.Id getId() {
    return internalGroup.getId();
  }

  @Override
  @Nullable
  public String getDescription() {
    return internalGroup.getDescription();
  }

  @Override
  public AccountGroup.UUID getOwnerGroupUUID() {
    return internalGroup.getOwnerGroupUUID();
  }

  @Override
  public boolean isVisibleToAll() {
    return internalGroup.isVisibleToAll();
  }

  @Override
  public Timestamp getCreatedOn() {
    return internalGroup.getCreatedOn();
  }

  @Override
  public ImmutableSet<Account.Id> getMembers() {
    return internalGroup.getMembers();
  }

  @Override
  public ImmutableSet<AccountGroup.UUID> getSubgroups() {
    return internalGroup.getSubgroups();
  }
}
