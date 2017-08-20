// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.reviewdb.client;

import java.sql.Timestamp;

public final class UserIdentity {
  /** Full name of the user. */
  protected String name;

  /** Email address (or user@host style string anyway). */
  protected String email;

  /** Username of the user. */
  protected String username;

  /** Time (in UTC) when the identity was constructed. */
  protected Timestamp when;

  /** Offset from UTC */
  protected int tz;

  /** If the user has a Gerrit account, their account identity. */
  protected Account.Id accountId;

  public String getName() {
    return name;
  }

  public void setName(String n) {
    name = n;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String e) {
    email = e;
  }

  public String getUsername() {
    return username;
  }

  public Timestamp getDate() {
    return when;
  }

  public void setDate(Timestamp d) {
    when = d;
  }

  public int getTimeZone() {
    return tz;
  }

  public void setTimeZone(int offset) {
    tz = offset;
  }

  public Account.Id getAccount() {
    return accountId;
  }

  public void setAccount(Account.Id id) {
    accountId = id;
  }
}
