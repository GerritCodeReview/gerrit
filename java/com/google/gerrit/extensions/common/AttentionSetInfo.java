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

import java.sql.Timestamp;

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
  public Timestamp lastUpdate;
  /** The human readable reason why the user was added. */
  public String reason;

  public AttentionSetInfo(AccountInfo account, Timestamp lastUpdate, String reason) {
    this.account = account;
    this.lastUpdate = lastUpdate;
    this.reason = reason;
  }
}
