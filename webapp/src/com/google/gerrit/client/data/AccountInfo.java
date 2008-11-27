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

package com.google.gerrit.client.data;

import com.google.gerrit.client.reviewdb.Account;

public class AccountInfo {
  protected Account.Id id;
  protected String fullName;
  protected String preferredEmail;

  protected AccountInfo() {
  }

  public AccountInfo(final Account a) {
    id = a.getId();
    fullName = a.getFullName();
    preferredEmail = a.getPreferredEmail();
  }

  public Account.Id getId() {
    return id;
  }

  public String getFullName() {
    return fullName;
  }

  public String getPreferredEmail() {
    return preferredEmail;
  }
}
