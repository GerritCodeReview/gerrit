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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import java.io.Serializable;
import java.sql.Timestamp;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class InternalGroup implements Serializable {
  private static final long serialVersionUID = 1L;

  public static InternalGroup create(
      AccountGroup accountGroup,
      ImmutableSet<Account.Id> members,
      ImmutableSet<AccountGroup.UUID> subgroups) {
    return create(accountGroup, members, subgroups, null);
  }

  public static InternalGroup create(
      AccountGroup accountGroup,
      ImmutableSet<Account.Id> members,
      ImmutableSet<AccountGroup.UUID> subgroups,
      ObjectId refState) {
    return builder()
        .setId(accountGroup.getId())
        .setNameKey(accountGroup.getNameKey())
        .setDescription(accountGroup.getDescription())
        .setOwnerGroupUUID(accountGroup.getOwnerGroupUUID())
        .setVisibleToAll(accountGroup.isVisibleToAll())
        .setGroupUUID(accountGroup.getGroupUUID())
        .setCreatedOn(accountGroup.getCreatedOn())
        .setMembers(members)
        .setSubgroups(subgroups)
        .setRefState(refState)
        .build();
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

  @Nullable
  public abstract ObjectId getRefState();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_InternalGroup.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setId(AccountGroup.Id id);

    public abstract Builder setNameKey(AccountGroup.NameKey name);

    public abstract Builder setDescription(@Nullable String description);

    public abstract Builder setOwnerGroupUUID(AccountGroup.UUID ownerGroupUuid);

    public abstract Builder setVisibleToAll(boolean visibleToAll);

    public abstract Builder setGroupUUID(AccountGroup.UUID groupUuid);

    public abstract Builder setCreatedOn(Timestamp createdOn);

    public abstract Builder setMembers(ImmutableSet<Account.Id> members);

    public abstract Builder setSubgroups(ImmutableSet<AccountGroup.UUID> subgroups);

    public abstract Builder setRefState(ObjectId refState);

    public abstract InternalGroup build();
  }
}
