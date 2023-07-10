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

package com.google.gerrit.extensions.common;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Representation of a (detailed) account in the REST API.
 *
 * <p>This class determines the JSON format of (detailed) accounts in the REST API.
 *
 * <p>This class extends {@link AccountInfo} (which defines fields for account properties that are
 * frequently used) and provides additional fields for account details which are needed only in some
 * cases.
 */
public class AccountDetailInfo extends AccountInfo {
  /** The timestamp of when the account was registered. */
  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  public Timestamp registeredOn;

  public AccountDetailInfo(Integer id) {
    super(id);
  }

  // TODO(issue-15508): Migrate timestamp fields in *Info/*Input classes from type Timestamp to
  // Instant
  @SuppressWarnings("JdkObsolete")
  public void setRegisteredOn(Instant registeredOn) {
    this.registeredOn = Timestamp.from(registeredOn);
  }

  public AccountDetailInfo() {}
}
