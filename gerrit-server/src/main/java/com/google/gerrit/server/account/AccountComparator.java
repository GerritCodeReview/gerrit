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
    if (isSet(account1.getPreferredEmail())
        && isSet(account2.getPreferredEmail())) {
      return account1.getPreferredEmail().compareTo(
          account2.getPreferredEmail());
    }
    if (isSet(account1.getFullName()) && isSet(account2.getFullName())) {
      return account1.getFullName().compareTo(account2.getFullName());
    }
    return Integer.valueOf(account1.getId().get()).compareTo(
        Integer.valueOf(account2.getId().get()));
  }

  private static boolean isSet(final String value) {
    return value != null && !"".equals(value);
  }
}
