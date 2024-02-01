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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import java.time.Instant;
import java.util.Optional;

/** Change to a reviewer's status. */
@AutoValue
public abstract class ReviewerStatusUpdate {
  public static ReviewerStatusUpdate createForReviewer(
      Instant ts, Account.Id updatedBy, Account.Id reviewer, ReviewerStateInternal state) {
    return new AutoValue_ReviewerStatusUpdate(
        ts, updatedBy, Optional.of(reviewer), Optional.empty(), state);
  }

  public static ReviewerStatusUpdate createForReviewerByEmail(
      Instant ts, Account.Id updatedBy, Address reviewerByEmail, ReviewerStateInternal state) {
    return new AutoValue_ReviewerStatusUpdate(
        ts, updatedBy, Optional.empty(), Optional.of(reviewerByEmail), state);
  }

  public abstract Instant date();

  public abstract Account.Id updatedBy();

  /** Not set if a reviewer for which no Gerrit account exists is added by email. */
  public abstract Optional<Account.Id> reviewer();

  /** Only set for reviewers that have no Gerrit account and that have been added by email. */
  public abstract Optional<Address> reviewerByEmail();

  public abstract ReviewerStateInternal state();
}
