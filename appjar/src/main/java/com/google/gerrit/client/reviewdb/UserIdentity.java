// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Column;

import java.sql.Timestamp;

public final class UserIdentity {
  /** Full name of the user. */
  @Column
  protected String name;

  /** Email address (or user@host style string anyway). */
  @Column
  protected String email;

  /** Time (in UTC) when the identity was constructed. */
  @Column
  protected Timestamp when;

  /** Offset from UTC */
  @Column
  protected int tz;

  public String getName() {
    return name;
  }

  public void setName(final String n) {
    name = n;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(final String e) {
    email = e;
  }

  public Timestamp getDate() {
    return when;
  }

  public void setDate(final Timestamp d) {
    when = d;
  }

  public int getTimeZone() {
    return tz;
  }

  public void setTimeZone(final int offset) {
    tz = offset;
  }
}
