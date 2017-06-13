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
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.externalids.ExternalIds;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class AccountsConsistencyChecker {
  private final Provider<ReviewDb> dbProvider;
  private final Accounts accounts;
  private final ExternalIds externalIds;

  @Inject
  AccountsConsistencyChecker(
      Provider<ReviewDb> dbProvider, Accounts accounts, ExternalIds externalIds) {
    this.dbProvider = dbProvider;
    this.accounts = accounts;
    this.externalIds = externalIds;
  }

  public List<ConsistencyProblemInfo> check() throws OrmException, IOException {
    List<ConsistencyProblemInfo> problems = new ArrayList<>();

    for (Account account : accounts.all(dbProvider.get())) {
      if (account.getPreferredEmail() != null) {
        if (!externalIds
            .byAccount(account.getId())
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
