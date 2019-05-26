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

package com.google.gerrit.server.group;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.server.AuditEvent;
import java.sql.Timestamp;

public interface GroupAuditService {
  void dispatch(AuditEvent action);

  void dispatchAddMembers(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<Account.Id> addedMembers,
      Timestamp addedOn);

  void dispatchDeleteMembers(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<Account.Id> deletedMembers,
      Timestamp deletedOn);

  void dispatchAddSubgroups(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<AccountGroup.UUID> addedSubgroups,
      Timestamp addedOn);

  void dispatchDeleteSubgroups(
      Account.Id actor,
      AccountGroup.UUID updatedGroup,
      ImmutableSet<AccountGroup.UUID> deletedSubgroups,
      Timestamp deletedOn);
}
