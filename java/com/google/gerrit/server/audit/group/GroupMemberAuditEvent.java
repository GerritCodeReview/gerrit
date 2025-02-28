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

package com.google.gerrit.server.audit.group;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.InlineMe;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import java.time.Instant;

/**
 * @param modifiedMembers Gets the added or deleted members of the updated group.
 */
public record GroupMemberAuditEvent(
    Account.Id actor,
    AccountGroup.UUID updatedGroup,
    Instant timestamp,
    ImmutableSet<Account.Id> modifiedMembers)
    implements GroupAuditEvent {
  public GroupMemberAuditEvent {
    requireNonNull(actor, "actor");
    requireNonNull(updatedGroup, "updatedGroup");
    requireNonNull(timestamp, "timestamp");
    requireNonNull(modifiedMembers, "modifiedMembers");
  }

  @InlineMe(replacement = "this.actor()")
  @Override
  public Account.Id getActor() {
    return actor();
  }

  @InlineMe(replacement = "this.updatedGroup()")
  @Override
  public AccountGroup.UUID getUpdatedGroup() {
    return updatedGroup();
  }

  @InlineMe(replacement = "this.timestamp()")
  @Override
  public Instant getTimestamp() {
    return timestamp();
  }

  @InlineMe(replacement = "this.modifiedMembers()")
  public ImmutableSet<Account.Id> getModifiedMembers() {
    return modifiedMembers();
  }

  public static GroupMemberAuditEvent create(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<Account.Id> modifiedMembers,
      Instant timestamp) {
    return new GroupMemberAuditEvent(actor, updatedGroup, modifiedMembers, timestamp);
  }
}
