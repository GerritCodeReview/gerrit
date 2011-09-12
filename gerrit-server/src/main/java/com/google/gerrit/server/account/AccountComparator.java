// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.gerrit.reviewdb.Account;

import java.util.Comparator;

public class AccountComparator implements Comparator<Account> {

  @Override
  public int compare(final Account account1, final Account account2) {
    if (account1.getPreferredEmail() != null && !account1.getPreferredEmail().isEmpty()
        && account2.getPreferredEmail() != null && !account2.getPreferredEmail().isEmpty()) {
      return account1.getPreferredEmail().compareTo(
          account2.getPreferredEmail());
    }
    if (account1.getFullName() != null && !account1.getFullName().isEmpty()
        && account2.getFullName() != null && !account2.getFullName().isEmpty()) {
      return account1.getFullName().compareTo(account2.getFullName());
    }
    return Integer.valueOf(account1.getId().get()).compareTo(
        Integer.valueOf(account2.getId().get()));
  }
}
