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

package com.google.gerrit.entities;

import com.google.auto.value.AutoValue;
import java.sql.Timestamp;
import java.util.Optional;

/** Inclusion of an {@link AccountGroup} in another {@link AccountGroup}. */
@AutoValue
public abstract class AccountGroupByIdAudit {
  public static Builder builder() {
    return new AutoValue_AccountGroupByIdAudit.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder groupId(AccountGroup.Id groupId);

    public abstract Builder includeUuid(AccountGroup.UUID includeUuid);

    public abstract Builder addedBy(Account.Id addedBy);

    public abstract Builder addedOn(Timestamp addedOn);

    abstract Builder removedBy(Account.Id removedBy);

    abstract Builder removedOn(Timestamp removedOn);

    public Builder removed(Account.Id removedBy, Timestamp removedOn) {
      return removedBy(removedBy).removedOn(removedOn);
    }

    public abstract AccountGroupByIdAudit build();
  }

  public abstract AccountGroup.Id groupId();

  public abstract AccountGroup.UUID includeUuid();

  public abstract Account.Id addedBy();

  public abstract Timestamp addedOn();

  public abstract Optional<Account.Id> removedBy();

  public abstract Optional<Timestamp> removedOn();

  public abstract Builder toBuilder();

  public boolean isActive() {
    return !removedOn().isPresent();
  }
}
