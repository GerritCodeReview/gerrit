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

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import java.sql.Timestamp;

/** An audit event for groups. */
public interface GroupAuditEvent {
  /**
   * Gets the acting user who is updating the group.
   *
   * @return the {@link com.google.gerrit.entities.Account.Id} of the acting user.
   */
  Account.Id getActor();

  /**
   * Gets the {@link com.google.gerrit.entities.AccountGroup.UUID} of the updated group.
   *
   * @return the {@link com.google.gerrit.entities.AccountGroup.UUID} of the updated group.
   */
  AccountGroup.UUID getUpdatedGroup();

  /**
   * Gets the {@link Timestamp} of the action.
   *
   * @return the {@link Timestamp} of the action.
   */
  Timestamp getTimestamp();
}
