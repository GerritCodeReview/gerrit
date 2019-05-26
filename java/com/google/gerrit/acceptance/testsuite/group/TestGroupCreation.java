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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.testsuite.ThrowingFunction;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import java.util.Optional;
import java.util.Set;

@AutoValue
public abstract class TestGroupCreation {

  public abstract Optional<String> name();

  public abstract Optional<String> description();

  public abstract Optional<AccountGroup.UUID> ownerGroupUuid();

  public abstract Optional<Boolean> visibleToAll();

  public abstract ImmutableSet<Account.Id> members();

  public abstract ImmutableSet<AccountGroup.UUID> subgroups();

  abstract ThrowingFunction<TestGroupCreation, AccountGroup.UUID> groupCreator();

  public static Builder builder(
      ThrowingFunction<TestGroupCreation, AccountGroup.UUID> groupCreator) {
    return new AutoValue_TestGroupCreation.Builder().groupCreator(groupCreator);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder name(String name);

    public abstract Builder description(String description);

    public Builder clearDescription() {
      return description("");
    }

    public abstract Builder ownerGroupUuid(AccountGroup.UUID ownerGroupUuid);

    public abstract Builder visibleToAll(boolean visibleToAll);

    public Builder clearMembers() {
      return members(ImmutableSet.of());
    }

    public Builder members(Account.Id member1, Account.Id... otherMembers) {
      return members(Sets.union(ImmutableSet.of(member1), ImmutableSet.copyOf(otherMembers)));
    }

    public abstract Builder members(Set<Account.Id> members);

    abstract ImmutableSet.Builder<Account.Id> membersBuilder();

    public Builder addMember(Account.Id member) {
      membersBuilder().add(member);
      return this;
    }

    public Builder clearSubgroups() {
      return subgroups(ImmutableSet.of());
    }

    public Builder subgroups(AccountGroup.UUID subgroup1, AccountGroup.UUID... otherSubgroups) {
      return subgroups(Sets.union(ImmutableSet.of(subgroup1), ImmutableSet.copyOf(otherSubgroups)));
    }

    public abstract Builder subgroups(Set<AccountGroup.UUID> subgroups);

    abstract ImmutableSet.Builder<AccountGroup.UUID> subgroupsBuilder();

    public Builder addSubgroup(AccountGroup.UUID subgroup) {
      subgroupsBuilder().add(subgroup);
      return this;
    }

    abstract Builder groupCreator(
        ThrowingFunction<TestGroupCreation, AccountGroup.UUID> groupCreator);

    abstract TestGroupCreation autoBuild();

    /**
     * Executes the group creation as specified.
     *
     * @return the UUID of the created group
     */
    public AccountGroup.UUID create() {
      TestGroupCreation groupCreation = autoBuild();
      return groupCreation.groupCreator().applyAndThrowSilently(groupCreation);
    }
  }
}
