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
import java.sql.Timestamp;
import java.util.Optional;
import java.util.Set;

// TODO(aliceks): Add Javadoc descriptions to this file.
@AutoValue
public abstract class InternalGroupUpdate {
  @FunctionalInterface
  public interface MemberModification {
    Set<Account.Id> apply(ImmutableSet<Account.Id> in);
  }

  @FunctionalInterface
  public interface SubgroupModification {
    Set<AccountGroup.UUID> apply(ImmutableSet<AccountGroup.UUID> in);
  }

  public abstract Optional<AccountGroup.NameKey> getName();

  // TODO(aliceks): Mention empty string (not null!) -> unset value in Javadoc.
  public abstract Optional<String> getDescription();

  public abstract Optional<AccountGroup.UUID> getOwnerGroupUUID();

  public abstract Optional<Boolean> getVisibleToAll();

  public abstract MemberModification getMemberModification();

  public abstract SubgroupModification getSubgroupModification();

  public abstract Optional<Timestamp> getUpdatedOn();

  public abstract Builder toBuilder();

  public static Builder builder() {
    return new AutoValue_InternalGroupUpdate.Builder()
        .setMemberModification(in -> in)
        .setSubgroupModification(in -> in);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setName(AccountGroup.NameKey name);

    public abstract Builder setDescription(String description);

    public abstract Builder setOwnerGroupUUID(AccountGroup.UUID ownerGroupUUID);

    public abstract Builder setVisibleToAll(boolean visibleToAll);

    public abstract Builder setMemberModification(MemberModification memberModification);

    abstract MemberModification getMemberModification();

    public abstract Builder setSubgroupModification(SubgroupModification subgroupModification);

    abstract SubgroupModification getSubgroupModification();

    public abstract Builder setUpdatedOn(Timestamp timestamp);

    public abstract InternalGroupUpdate build();
  }
}
