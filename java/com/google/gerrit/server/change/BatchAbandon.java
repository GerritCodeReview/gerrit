// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collection;

@Singleton
public class BatchAbandon {
  private final Provider<ReviewDb> dbProvider;
  private final AbandonOp.Factory abandonOpFactory;

  @Inject
  BatchAbandon(Provider<ReviewDb> dbProvider, AbandonOp.Factory abandonOpFactory) {
    this.dbProvider = dbProvider;
    this.abandonOpFactory = abandonOpFactory;
  }

  /**
   * If an extension has more than one changes to abandon that belong to the same project, they
   * should use the batch instead of abandoning one by one.
   *
   * <p>It's the caller's responsibility to ensure that all jobs inside the same batch have the
   * matching project from its ChangeData. Violations will result in a ResourceConflictException.
   */
  public void batchAbandon(
      BatchUpdate.Factory updateFactory,
      Project.NameKey project,
      CurrentUser user,
      Collection<ChangeData> changes,
      String msgTxt,
      NotifyHandling notifyHandling,
      ListMultimap<RecipientType, Account.Id> accountsToNotify)
      throws RestApiException, UpdateException {
    if (changes.isEmpty()) {
      return;
    }
    AccountState accountState = user.isIdentifiedUser() ? user.asIdentifiedUser().state() : null;
    try (BatchUpdate u = updateFactory.create(dbProvider.get(), project, user, TimeUtil.nowTs())) {
      for (ChangeData change : changes) {
        if (!project.equals(change.project())) {
          throw new ResourceConflictException(
              String.format(
                  "Project name \"%s\" doesn't match \"%s\"",
                  change.project().get(), project.get()));
        }
        u.addOp(
            change.getId(),
            abandonOpFactory.create(accountState, msgTxt, notifyHandling, accountsToNotify));
      }
      u.execute();
    }
  }

  public void batchAbandon(
      BatchUpdate.Factory updateFactory,
      Project.NameKey project,
      CurrentUser user,
      Collection<ChangeData> changes,
      String msgTxt)
      throws RestApiException, UpdateException {
    batchAbandon(
        updateFactory,
        project,
        user,
        changes,
        msgTxt,
        NotifyHandling.ALL,
        ImmutableListMultimap.of());
  }

  public void batchAbandon(
      BatchUpdate.Factory updateFactory,
      Project.NameKey project,
      CurrentUser user,
      Collection<ChangeData> changes)
      throws RestApiException, UpdateException {
    batchAbandon(
        updateFactory, project, user, changes, "", NotifyHandling.ALL, ImmutableListMultimap.of());
  }
}
