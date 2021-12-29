// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.auto.value.AutoValue;
import com.google.gerrit.entities.Account;
import java.time.Instant;
import java.util.Optional;

/** Change to an assignee's status. */
@AutoValue
public abstract class AssigneeStatusUpdate {
  public static AssigneeStatusUpdate create(
      Instant ts, Account.Id updatedBy, Optional<Account.Id> currentAssignee) {
    return new AutoValue_AssigneeStatusUpdate(ts, updatedBy, currentAssignee);
  }

  public abstract Instant date();

  public abstract Account.Id updatedBy();

  public abstract Optional<Account.Id> currentAssignee();
}
