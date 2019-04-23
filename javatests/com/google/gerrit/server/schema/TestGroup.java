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

package com.google.gerrit.server.schema;

import com.google.auto.value.AutoValue;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.util.time.TimeUtil;
import java.sql.Timestamp;
import java.util.Optional;
import org.junit.Ignore;

@AutoValue
@Ignore
public abstract class TestGroup {
  abstract Optional<AccountGroup.NameKey> getNameKey();

  abstract Optional<AccountGroup.UUID> getGroupUuid();

  abstract Optional<AccountGroup.Id> getId();

  abstract Optional<Timestamp> getCreatedOn();

  abstract Optional<AccountGroup.UUID> getOwnerGroupUuid();

  abstract Optional<String> getDescription();

  abstract boolean isVisibleToAll();

  public static Builder builder() {
    return new AutoValue_TestGroup.Builder().setVisibleToAll(false);
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setNameKey(AccountGroup.NameKey nameKey);

    public Builder setName(String name) {
      return setNameKey(AccountGroup.nameKey(name));
    }

    public abstract Builder setGroupUuid(AccountGroup.UUID uuid);

    public abstract Builder setId(AccountGroup.Id id);

    public abstract Builder setCreatedOn(Timestamp createdOn);

    public abstract Builder setOwnerGroupUuid(AccountGroup.UUID ownerGroupUuid);

    public abstract Builder setDescription(String description);

    public abstract Builder setVisibleToAll(boolean visibleToAll);

    public abstract TestGroup autoBuild();

    public AccountGroup build() {
      TestGroup testGroup = autoBuild();
      AccountGroup.NameKey name = testGroup.getNameKey().orElse(AccountGroup.nameKey("users"));
      AccountGroup.Id id = testGroup.getId().orElse(AccountGroup.id(Math.abs(name.hashCode())));
      AccountGroup.UUID uuid = testGroup.getGroupUuid().orElse(AccountGroup.uuid(name + "-UUID"));
      Timestamp createdOn = testGroup.getCreatedOn().orElseGet(TimeUtil::nowTs);
      AccountGroup accountGroup = new AccountGroup(name, id, uuid, createdOn);
      testGroup.getOwnerGroupUuid().ifPresent(accountGroup::setOwnerGroupUUID);
      testGroup.getDescription().ifPresent(accountGroup::setDescription);
      accountGroup.setVisibleToAll(testGroup.isVisibleToAll());
      return accountGroup;
    }
  }
}
