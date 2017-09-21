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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.CC;
import static com.google.gerrit.server.notedb.ReviewerStateInternal.REVIEWER;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import java.sql.Timestamp;

/**
 * Set of reviewers on a change.
 *
 * <p>A given account may appear in multiple states and at different timestamps. No reviewers with
 * state {@link ReviewerStateInternal#REMOVED} are ever exposed by this interface.
 */
public class ReviewerSet {
  private static final ReviewerSet EMPTY =
      new ReviewerSet(ImmutableTable.<ReviewerStateInternal, Account.Id, Timestamp>of());

  public static ReviewerSet fromApprovals(Iterable<PatchSetApproval> approvals) {
    PatchSetApproval first = null;
    Table<ReviewerStateInternal, Account.Id, Timestamp> reviewers = HashBasedTable.create();
    for (PatchSetApproval psa : approvals) {
      if (first == null) {
        first = psa;
      } else {
        checkArgument(
            first
                .getKey()
                .getParentKey()
                .getParentKey()
                .equals(psa.getKey().getParentKey().getParentKey()),
            "multiple change IDs: %s, %s",
            first.getKey(),
            psa.getKey());
      }
      Account.Id id = psa.getAccountId();
      reviewers.put(REVIEWER, id, psa.getGranted());
      if (psa.getValue() != 0) {
        reviewers.remove(CC, id);
      }
    }
    return new ReviewerSet(reviewers);
  }

  public static ReviewerSet fromTable(Table<ReviewerStateInternal, Account.Id, Timestamp> table) {
    return new ReviewerSet(table);
  }

  public static ReviewerSet empty() {
    return EMPTY;
  }

  private final ImmutableTable<ReviewerStateInternal, Account.Id, Timestamp> table;
  private ImmutableSet<Account.Id> accounts;

  private ReviewerSet(Table<ReviewerStateInternal, Account.Id, Timestamp> table) {
    this.table = ImmutableTable.copyOf(table);
  }

  public ImmutableSet<Account.Id> all() {
    if (accounts == null) {
      // Idempotent and immutable, don't bother locking.
      accounts = ImmutableSet.copyOf(table.columnKeySet());
    }
    return accounts;
  }

  public ImmutableSet<Account.Id> byState(ReviewerStateInternal state) {
    return table.row(state).keySet();
  }

  public ImmutableTable<ReviewerStateInternal, Account.Id, Timestamp> asTable() {
    return table;
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof ReviewerSet) && table.equals(((ReviewerSet) o).table);
  }

  @Override
  public int hashCode() {
    return table.hashCode();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + table;
  }
}
