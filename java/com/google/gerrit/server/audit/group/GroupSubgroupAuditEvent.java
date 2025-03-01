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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import java.time.Instant;

/**
 * Group subgroup audit event.
 *
 * @param modifiedSubgroups Gets the added or deleted subgroups of the updated group.
 */
public record GroupSubgroupAuditEvent(
    Account.Id actor,
    AccountGroup.UUID updatedGroup,
    Instant timestamp,
    ImmutableSet<AccountGroup.UUID> modifiedSubgroups)
    implements GroupAuditEvent {
  public GroupSubgroupAuditEvent {
    requireNonNull(actor, "actor");
    requireNonNull(updatedGroup, "updatedGroup");
    requireNonNull(timestamp, "timestamp");
    requireNonNull(modifiedSubgroups, "modifiedSubgroups");
  }

  public static GroupSubgroupAuditEvent create(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<AccountGroup.UUID> modifiedSubgroups,
      Instant timestamp) {
    return new GroupSubgroupAuditEvent(actor, updatedGroup, timestamp, modifiedSubgroups);
  }
}
