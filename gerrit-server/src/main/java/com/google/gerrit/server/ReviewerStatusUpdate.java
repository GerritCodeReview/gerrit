// Copyright (C) 2016 The Android Open Source Project
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
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.notedb.ReviewerStateInternal;

import java.sql.Timestamp;

/** Change to a reviewer's status. */
@AutoValue
public abstract class ReviewerStatusUpdate {
  public static ReviewerStatusUpdate create(
      Timestamp ts, Account.Id author, Account.Id reviewer,
      ReviewerStateInternal state) {
    return new AutoValue_ReviewerStatusUpdate(ts, author, reviewer, state);
  }

  public abstract Timestamp date();
  public abstract Account.Id author();
  public abstract Account.Id reviewer();
  public abstract ReviewerStateInternal state();
}
