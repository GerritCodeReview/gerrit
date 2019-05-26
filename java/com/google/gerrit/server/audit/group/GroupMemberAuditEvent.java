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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import java.sql.Timestamp;

@AutoValue
public abstract class GroupMemberAuditEvent implements GroupAuditEvent {
  public static GroupMemberAuditEvent create(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<Account.Id> modifiedMembers,
      Timestamp timestamp) {
    return new AutoValue_GroupMemberAuditEvent(actor, updatedGroup, modifiedMembers, timestamp);
  }

  @Override
  public abstract Account.Id getActor();

  @Override
  public abstract AccountGroup.UUID getUpdatedGroup();

  /** Gets the added or deleted members of the updated group. */
  public abstract ImmutableSet<Account.Id> getModifiedMembers();

  @Override
  public abstract Timestamp getTimestamp();
}
