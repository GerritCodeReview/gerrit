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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import java.sql.Timestamp;
import java.util.Optional;

@AutoValue
public abstract class TestGroup {

  public abstract AccountGroup.UUID groupUuid();

  public abstract AccountGroup.Id groupId();

  public String name() {
    return nameKey().get();
  }

  public abstract AccountGroup.NameKey nameKey();

  public abstract Optional<String> description();

  public abstract AccountGroup.UUID ownerGroupUuid();

  public abstract boolean visibleToAll();

  public abstract Timestamp createdOn();

  public abstract ImmutableSet<Account.Id> members();

  public abstract ImmutableSet<AccountGroup.UUID> subgroups();

  static Builder builder() {
    return new AutoValue_TestGroup.Builder();
  }

  @AutoValue.Builder
  abstract static class Builder {

    public abstract Builder groupUuid(AccountGroup.UUID groupUuid);

    public abstract Builder groupId(AccountGroup.Id id);

    public abstract Builder nameKey(AccountGroup.NameKey name);

    public abstract Builder description(String description);

    public abstract Builder description(Optional<String> description);

    public abstract Builder ownerGroupUuid(AccountGroup.UUID ownerGroupUuid);

    public abstract Builder visibleToAll(boolean visibleToAll);

    public abstract Builder createdOn(Timestamp createdOn);

    public abstract Builder members(ImmutableSet<Account.Id> members);

    public abstract Builder subgroups(ImmutableSet<AccountGroup.UUID> subgroups);

    abstract TestGroup build();
  }
}
