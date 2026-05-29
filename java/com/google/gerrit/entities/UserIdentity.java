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

package com.google.gerrit.entities;

import java.time.Instant;

public final class UserIdentity {
  /** Full name of the user. */
  private String name;

  /** Email address (or user@host style string anyway). */
  private String email;

  /** Username of the user. */
  private String username;

  /** Time (in UTC) when the identity was constructed. */
  private Instant when;

  /** Offset from UTC */
  private int tz;

  /** If the user has a Gerrit account, their account identity. */
  private Account.Id accountId;

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

  public Instant getDate() {
    return when;
  }

  public void setDate(Instant d) {
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
