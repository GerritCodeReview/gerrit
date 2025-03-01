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

package com.google.gerrit.entities;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import java.io.Serializable;
import java.time.Instant;
import org.eclipse.jgit.lib.ObjectId;

public record InternalGroup(
    AccountGroup.Id id,
    AccountGroup.NameKey nameKey,
    @Nullable String description,
    AccountGroup.UUID ownerGroupUUID,
    boolean visibleToAll,
    AccountGroup.UUID groupUUID,
    Instant createdOn,
    ImmutableSet<Account.Id> members,
    ImmutableSet<AccountGroup.UUID> subgroups,
    @Nullable ObjectId refState)
    implements Serializable {
  public InternalGroup {
    requireNonNull(id, "id");
    requireNonNull(nameKey, "nameKey");
    requireNonNull(ownerGroupUUID, "ownerGroupUUID");
    requireNonNull(groupUUID, "groupUUID");
    requireNonNull(createdOn, "createdOn");
    requireNonNull(members, "members");
    requireNonNull(subgroups, "subgroups");
  }

  private static final long serialVersionUID = 1L;

  public String getName() {
    return nameKey().get();
  }

  public Builder toBuilder() {
    return new AutoBuilder_InternalGroup_Builder(this);
  }

  public static Builder builder() {
    return new AutoBuilder_InternalGroup_Builder();
  }

  @AutoBuilder
  public abstract static class Builder {
    public abstract Builder setId(AccountGroup.Id id);

    public abstract Builder setNameKey(AccountGroup.NameKey name);

    public abstract Builder setDescription(@Nullable String description);

    public abstract Builder setOwnerGroupUUID(AccountGroup.UUID ownerGroupUUID);

    public abstract Builder setVisibleToAll(boolean visibleToAll);

    public abstract Builder setGroupUUID(AccountGroup.UUID groupUUID);

    public abstract Builder setCreatedOn(Instant createdOn);

    public abstract Builder setMembers(ImmutableSet<Account.Id> members);

    public abstract Builder setSubgroups(ImmutableSet<AccountGroup.UUID> subgroups);

    public abstract Builder setRefState(ObjectId refState);

    public abstract InternalGroup build();
  }
}
