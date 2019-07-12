// Copyright (C) 2017 The Android Open Source Project
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

import com.google.gerrit.extensions.api.config.ConsistencyCheckInfo.ConsistencyProblemInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class AccountsConsistencyChecker {
  private final Accounts accounts;

  @Inject
  AccountsConsistencyChecker(Accounts accounts) {
    this.accounts = accounts;
  }

  public List<ConsistencyProblemInfo> check() throws IOException {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();

    for (AccountState accountState : accounts.all()) {
      Account account = accountState.getAccount();
      if (account.getPreferredEmail() != null) {
        if (!accountState
            .getExternalIds()
            .stream()
            .anyMatch(e -> account.getPreferredEmail().equals(e.email()))) {
          addError(
              String.format(
                  "Account '%s' has no external ID for its preferred email '%s'",
                  account.getId().get(), account.getPreferredEmail()),
              problems);
        }
      }
    }

    return problems;
  }

  private static void addError(String error, List<ConsistencyProblemInfo> problems) {
    problems.add(new ConsistencyProblemInfo(ConsistencyProblemInfo.Status.ERROR, error));
  }
}
