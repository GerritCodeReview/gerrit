// Copyright (C) 2009 The Android Open Source Project
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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Instant;
import java.util.Optional;

/** Membership of an {@link Account} in an {@link AccountGroup}. */
public record AccountGroupMemberAudit(
    AccountGroup.Id groupId,
    Account.Id memberId,
    Account.Id addedBy,
    Instant addedOn,
    Optional<Account.Id> removedBy,
    Optional<Instant> removedOn) {
  public AccountGroupMemberAudit {
    requireNonNull(groupId, "groupId");
    requireNonNull(memberId, "memberId");
    requireNonNull(addedBy, "addedBy");
    requireNonNull(addedOn, "addedOn");
    requireNonNull(removedBy, "removedBy");
    requireNonNull(removedOn, "removedOn");
  }

  public static Builder builder() {
    return new AutoBuilder_AccountGroupMemberAudit_Builder();
  }

  @AutoBuilder
  public abstract static class Builder {
    public abstract Builder groupId(AccountGroup.Id groupId);

    public abstract Builder memberId(Account.Id accountId);

    public abstract Builder addedBy(Account.Id addedBy);

    abstract Account.Id addedBy();

    public abstract Builder addedOn(Instant addedOn);

    abstract Instant addedOn();

    abstract Builder removedBy(Account.Id removedBy);

    abstract Builder removedOn(Instant removedOn);

    @CanIgnoreReturnValue
    public Builder removed(Account.Id removedBy, Instant removedOn) {
      return removedBy(removedBy).removedOn(removedOn);
    }

    @CanIgnoreReturnValue
    public Builder removedLegacy() {
      return removed(addedBy(), addedOn());
    }

    public abstract AccountGroupMemberAudit build();
  }

  public Builder toBuilder() {
    return new AutoBuilder_AccountGroupMemberAudit_Builder(this);
  }

  public boolean isActive() {
    return !removedOn().isPresent();
  }
}
