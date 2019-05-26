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
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@AutoValue
public abstract class TestGroupUpdate {

  public abstract Optional<String> name();

  public abstract Optional<String> description();

  public abstract Optional<AccountGroup.UUID> ownerGroupUuid();

  public abstract Optional<Boolean> visibleToAll();

  public abstract Function<ImmutableSet<Account.Id>, Set<Account.Id>> memberModification();

  public abstract Function<ImmutableSet<AccountGroup.UUID>, Set<AccountGroup.UUID>>
      subgroupModification();

  abstract ThrowingConsumer<TestGroupUpdate> groupUpdater();

  public static Builder builder(ThrowingConsumer<TestGroupUpdate> groupUpdater) {
    return new AutoValue_TestGroupUpdate.Builder()
        .groupUpdater(groupUpdater)
        .memberModification(in -> in)
        .subgroupModification(in -> in);
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder name(String name);

    public abstract Builder description(String description);

    public Builder clearDescription() {
      return description("");
    }

    public abstract Builder ownerGroupUuid(AccountGroup.UUID ownerGroupUUID);

    public abstract Builder visibleToAll(boolean visibleToAll);

    abstract Builder memberModification(
        Function<ImmutableSet<Account.Id>, Set<Account.Id>> memberModification);

    abstract Function<ImmutableSet<Account.Id>, Set<Account.Id>> memberModification();

    public Builder clearMembers() {
      return memberModification(originalMembers -> ImmutableSet.of());
    }

    public Builder addMember(Account.Id member) {
      Function<ImmutableSet<Account.Id>, Set<Account.Id>> previousModification =
          memberModification();
      memberModification(
          originalMembers ->
              Sets.union(previousModification.apply(originalMembers), ImmutableSet.of(member)));
      return this;
    }

    public Builder removeMember(Account.Id member) {
      Function<ImmutableSet<Account.Id>, Set<Account.Id>> previousModification =
          memberModification();
      memberModification(
          originalMembers ->
              Sets.difference(
                  previousModification.apply(originalMembers), ImmutableSet.of(member)));
      return this;
    }

    abstract Builder subgroupModification(
        Function<ImmutableSet<AccountGroup.UUID>, Set<AccountGroup.UUID>> subgroupModification);

    abstract Function<ImmutableSet<AccountGroup.UUID>, Set<AccountGroup.UUID>>
        subgroupModification();

    public Builder clearSubgroups() {
      return subgroupModification(originalMembers -> ImmutableSet.of());
    }

    public Builder addSubgroup(AccountGroup.UUID subgroup) {
      Function<ImmutableSet<AccountGroup.UUID>, Set<AccountGroup.UUID>> previousModification =
          subgroupModification();
      subgroupModification(
          originalSubgroups ->
              Sets.union(previousModification.apply(originalSubgroups), ImmutableSet.of(subgroup)));
      return this;
    }

    public Builder removeSubgroup(AccountGroup.UUID subgroup) {
      Function<ImmutableSet<AccountGroup.UUID>, Set<AccountGroup.UUID>> previousModification =
          subgroupModification();
      subgroupModification(
          originalSubgroups ->
              Sets.difference(
                  previousModification.apply(originalSubgroups), ImmutableSet.of(subgroup)));
      return this;
    }

    abstract Builder groupUpdater(ThrowingConsumer<TestGroupUpdate> groupUpdater);

    abstract TestGroupUpdate autoBuild();

    /** Executes the group update as specified. */
    public void update() {
      TestGroupUpdate groupUpdater = autoBuild();
      groupUpdater.groupUpdater().acceptAndThrowSilently(groupUpdater);
    }
  }
}
