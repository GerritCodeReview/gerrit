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

package com.google.gerrit.server.group.db;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

// TODO(aliceks): Add Javadoc descriptions to this file.
@AutoValue
public abstract class InternalGroupUpdate {
  public abstract Optional<AccountGroup.NameKey> getName();

  // TODO(aliceks): Mention empty string (not null!) -> unset value in Javadoc.
  public abstract Optional<String> getDescription();

  public abstract Optional<AccountGroup.UUID> getOwnerGroupUUID();

  public abstract Optional<Boolean> getVisibleToAll();

  public abstract Function<ImmutableSet<Account.Id>, ? extends Set<Account.Id>>
      getMemberModification();

  public abstract Function<ImmutableSet<AccountGroup.UUID>, ? extends Set<AccountGroup.UUID>>
      getSubgroupModification();

  public static Builder builder() {
    return new AutoValue_InternalGroupUpdate.Builder()
        .setMemberModification(Function.identity())
        .setSubgroupModification(Function.identity());
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(AccountGroup.NameKey name);

    public abstract Builder setDescription(String description);

    public abstract Builder setOwnerGroupUUID(AccountGroup.UUID ownerGroupUUID);

    public abstract Builder setVisibleToAll(boolean visibleToAll);

    public abstract Builder setMemberModification(
        Function<ImmutableSet<Account.Id>, ? extends Set<Account.Id>> memberModification);

    public abstract Builder setSubgroupModification(
        Function<ImmutableSet<AccountGroup.UUID>, ? extends Set<AccountGroup.UUID>>
            subgroupModification);

    public abstract InternalGroupUpdate build();
  }
}
