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

package com.google.gerrit.client.account;

import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.Link;
import com.google.gerrit.client.changes.AccountDashboardScreen;
import com.google.gerrit.client.data.AccountInfo;
import com.google.gerrit.client.ui.DirectScreenLink;
import com.google.gerrit.client.ui.Screen;

/** Link to any user's account dashboard. */
public class AccountDashboardLink extends DirectScreenLink {
  private static String name(final AccountInfo ai) {
    if (ai.getFullName() != null) {
      return ai.getFullName();
    }
    if (ai.getPreferredEmail() != null) {
      return ai.getPreferredEmail();
    }
    return Gerrit.C.anonymousCoward();
  }

  private AccountInfo account;

  public AccountDashboardLink(final AccountInfo ai) {
    this(name(ai), ai);
  }

  public AccountDashboardLink(final String text, final AccountInfo ai) {
    super(text, Link.toAccountDashboard(ai));
    account = ai;
  }

  @Override
  protected Screen createScreen() {
    return new AccountDashboardScreen(account.getId());
  }
}
