// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import com.google.gerrit.common.Nullable;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a single user included in the attention set. Used in the API. See {@link
 * com.google.gerrit.entities.AttentionSetUpdate} for the internal representation.
 *
 * <p>See <a href="https://www.gerritcodereview.com/design-docs/attention-set.html">here</a> for
 * background.
 */
public class AttentionSetInfo {
  /** The user included in the attention set. */
  public AccountInfo account;

  /** The timestamp of the last update. */
  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  public Timestamp lastUpdate;

  /** The human readable reason why the user was added. */
  public String reason;

  /**
   * The user that might be mentioned in {@link #reason} as the one who caused the update. This is
   * needed since {@link #reason} contains the account in pseudonymized form and is expanded in the
   * frontend. {@code null} if there is no such account.
   */
  @Nullable public AccountInfo reasonAccount;

  public AttentionSetInfo(
      AccountInfo account,
      Timestamp lastUpdate,
      String reason,
      @Nullable AccountInfo reasonAccount) {
    this.account = account;
    this.lastUpdate = lastUpdate;
    this.reason = reason;
    this.reasonAccount = reasonAccount;
  }

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public AttentionSetInfo(
      AccountInfo account, Instant lastUpdate, String reason, @Nullable AccountInfo reasonAccount) {
    this.account = account;
    this.lastUpdate = Timestamp.from(lastUpdate);
    this.reason = reason;
    this.reasonAccount = reasonAccount;
  }

  public AttentionSetInfo() {}

  @Override
  public boolean equals(Object o) {
    if (o instanceof AttentionSetInfo) {
      AttentionSetInfo attentionSetInfo = (AttentionSetInfo) o;
      return Objects.equals(account, attentionSetInfo.account)
          && Objects.equals(lastUpdate, attentionSetInfo.lastUpdate)
          && Objects.equals(reason, attentionSetInfo.reason)
          && Objects.equals(reasonAccount, attentionSetInfo.reasonAccount);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hash(account, lastUpdate, reason, reasonAccount);
  }
}
