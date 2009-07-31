// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.client.reviewdb.Account;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.servlet.RequestScoped;

/** An authenticated user. */
@RequestScoped
public class IdentifiedUser extends CurrentUser {
  public interface Factory {
    IdentifiedUser create(Account.Id id);
  }

  private final Account.Id accountId;

  @Inject
  IdentifiedUser(@Assisted final Account.Id i) {
    accountId = i;
  }

  /** The account identity for the user. */
  public Account.Id getAccountId() {
    return accountId;
  }

  @Override
  public String toString() {
    return "CurrentUser[" + getAccountId() + "]";
  }
}
