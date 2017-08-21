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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import java.io.Serializable;
import java.sql.Timestamp;

@AutoValue
public abstract class InternalGroup implements Serializable {

  public static InternalGroup create(
      AccountGroup accountGroup,
      ImmutableSet<Account.Id> members,
      ImmutableSet<AccountGroup.UUID> subgroups) {
    return new AutoValue_InternalGroup(
        accountGroup.getId(),
        accountGroup.getNameKey(),
        accountGroup.getDescription(),
        accountGroup.getOwnerGroupUUID(),
        accountGroup.isVisibleToAll(),
        accountGroup.getGroupUUID(),
        accountGroup.getCreatedOn(),
        members,
        subgroups);
  }

  public abstract AccountGroup.Id getId();

  public String getName() {
    return getNameKey().get();
  }

  public abstract AccountGroup.NameKey getNameKey();

  @Nullable
  public abstract String getDescription();

  public abstract AccountGroup.UUID getOwnerGroupUUID();

  public abstract boolean isVisibleToAll();

  public abstract AccountGroup.UUID getGroupUUID();

  public abstract Timestamp getCreatedOn();

  public abstract ImmutableSet<Account.Id> getMembers();

  public abstract ImmutableSet<AccountGroup.UUID> getSubgroups();
}
