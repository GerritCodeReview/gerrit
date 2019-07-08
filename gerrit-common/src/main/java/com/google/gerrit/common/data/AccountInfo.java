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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.client.Account;

/** Summary information about an {@link Account}, for simple tabular displays. */
public class AccountInfo {
  protected Account.Id id;
  protected String fullName;
  protected String preferredEmail;
  protected String username;

  protected AccountInfo() {}

  /**
   * Create an 'Anonymous Coward' account info, when only the id is known.
   *
   * <p>This constructor should only be a last-ditch effort, when the usual account lookup has
   * failed and a stale account id has been discovered in the data store.
   */
  public AccountInfo(final Account.Id id) {
    this.id = id;
  }

  /**
   * Create an account description from a real data store record.
   *
   * @param a the data store record holding the specific account details.
   */
  public AccountInfo(final Account a) {
    id = a.getId();
    fullName = a.getFullName();
    preferredEmail = a.getPreferredEmail();
    username = a.getUserName();
  }

  /** @return the unique local id of the account */
  public Account.Id getId() {
    return id;
  }

  public void setFullName(String n) {
    fullName = n;
  }

  /** @return the full name of the account holder; null if not supplied */
  public String getFullName() {
    return fullName;
  }

  /** @return the email address of the account holder; null if not supplied */
  public String getPreferredEmail() {
    return preferredEmail;
  }

  public void setPreferredEmail(final String email) {
    preferredEmail = email;
  }

  /** @return the username of the account holder */
  public String getUsername() {
    return username;
  }
}
