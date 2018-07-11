// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.google.gerrit.mail.Address;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import java.sql.Timestamp;

/**
 * Set of reviewers on a change that do not have a Gerrit account and were added by email instead.
 *
 * <p>A given account may appear in multiple states and at different timestamps. No reviewers with
 * state {@link ReviewerStateInternal#REMOVED} are ever exposed by this interface.
 */
public class ReviewerByEmailSet {
  private static final ReviewerByEmailSet EMPTY = new ReviewerByEmailSet(ImmutableTable.of());

  public static ReviewerByEmailSet fromTable(
      Table<ReviewerStateInternal, Address, Timestamp> table) {
    return new ReviewerByEmailSet(table);
  }

  public static ReviewerByEmailSet empty() {
    return EMPTY;
  }

  private final ImmutableTable<ReviewerStateInternal, Address, Timestamp> table;
  private ImmutableSet<Address> users;

  private ReviewerByEmailSet(Table<ReviewerStateInternal, Address, Timestamp> table) {
    this.table = ImmutableTable.copyOf(table);
  }

  public ImmutableSet<Address> all() {
    if (users == null) {
      // Idempotent and immutable, don't bother locking.
      users = ImmutableSet.copyOf(table.columnKeySet());
    }
    return users;
  }

  public ImmutableSet<Address> byState(ReviewerStateInternal state) {
    return table.row(state).keySet();
  }

  public ImmutableTable<ReviewerStateInternal, Address, Timestamp> asTable() {
    return table;
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof ReviewerByEmailSet) && table.equals(((ReviewerByEmailSet) o).table);
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
