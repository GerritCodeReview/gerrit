// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.reviewdb.client;

import com.google.auto.value.AutoValue;
import java.sql.Timestamp;
import java.util.Optional;

/** Inclusion of an {@link AccountGroup} in another {@link AccountGroup}. */
@AutoValue
public abstract class AccountGroupByIdAudit {
  public static Key key(AccountGroup.Id groupId, AccountGroup.UUID includeUuid, Timestamp addedOn) {
    return new AutoValue_AccountGroupByIdAudit_Key(groupId, includeUuid, addedOn);
  }

  @AutoValue
  public abstract static class Key {
    public abstract AccountGroup.Id groupId();

    public abstract AccountGroup.UUID includeUuid();

    public abstract Timestamp addedOn();
  }

  public static Builder builder() {
    return new AutoValue_AccountGroupByIdAudit.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder key(Key key);

    public abstract Builder addedBy(Account.Id addedBy);

    abstract Builder removedBy(Account.Id removedBy);

    abstract Builder removedOn(Timestamp removedOn);

    public Builder removed(Account.Id removedBy, Timestamp removedOn) {
      return removedBy(removedBy).removedOn(removedOn);
    }

    public abstract AccountGroupByIdAudit build();
  }

  public abstract AccountGroupByIdAudit.Key key();

  public abstract Account.Id addedBy();

  public abstract Optional<Account.Id> removedBy();

  public abstract Optional<Timestamp> removedOn();

  public abstract Builder toBuilder();

  public AccountGroup.Id groupId() {
    return key().groupId();
  }

  public Timestamp getAddedOn() {
    return key().addedOn();
  }

  public AccountGroup.UUID includeUuid() {
    return key().includeUuid();
  }

  public boolean isActive() {
    return !removedOn().isPresent();
  }
}
