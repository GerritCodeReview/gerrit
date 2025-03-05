// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.testsuite.group;

import static java.util.Objects.requireNonNull;

import com.google.auto.value.AutoBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import java.time.Instant;
import java.util.Optional;

public record TestGroup(
    AccountGroup.UUID groupUuid,
    AccountGroup.Id groupId,
    AccountGroup.NameKey nameKey,
    Optional<String> description,
    AccountGroup.UUID ownerGroupUuid,
    boolean visibleToAll,
    Instant createdOn,
    ImmutableSet<Account.Id> members,
    ImmutableSet<AccountGroup.UUID> subgroups) {
  public TestGroup {
    requireNonNull(groupUuid, "groupUuid");
    requireNonNull(groupId, "groupId");
    requireNonNull(nameKey, "nameKey");
    requireNonNull(description, "description");
    requireNonNull(ownerGroupUuid, "ownerGroupUuid");
    requireNonNull(createdOn, "createdOn");
    requireNonNull(members, "members");
    requireNonNull(subgroups, "subgroups");
  }

  public String name() {
    return nameKey().get();
  }

  static Builder builder() {
    return new AutoBuilder_TestGroup_Builder();
  }

  @AutoBuilder
  abstract static class Builder {

    public abstract Builder groupUuid(AccountGroup.UUID groupUuid);

    public abstract Builder groupId(AccountGroup.Id id);

    public abstract Builder nameKey(AccountGroup.NameKey name);

    public abstract Builder description(String description);

    public abstract Builder description(Optional<String> description);

    public abstract Builder ownerGroupUuid(AccountGroup.UUID ownerGroupUuid);

    public abstract Builder visibleToAll(boolean visibleToAll);

    public abstract Builder createdOn(Instant createdOn);

    public abstract Builder members(ImmutableSet<Account.Id> members);

    public abstract Builder subgroups(ImmutableSet<AccountGroup.UUID> subgroups);

    abstract TestGroup build();
  }
}
