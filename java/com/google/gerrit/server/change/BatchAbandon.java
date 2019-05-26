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

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.config.ChangeCleanupConfig;
import com.google.gerrit.server.plugincontext.PluginItemContext;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;

@Singleton
public class BatchAbandon {
  private final AbandonOp.Factory abandonOpFactory;
  private final ChangeCleanupConfig cfg;
  private final PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore;

  @Inject
  BatchAbandon(
      AbandonOp.Factory abandonOpFactory,
      ChangeCleanupConfig cfg,
      PluginItemContext<AccountPatchReviewStore> accountPatchReviewStore) {
    this.abandonOpFactory = abandonOpFactory;
    this.cfg = cfg;
    this.accountPatchReviewStore = accountPatchReviewStore;
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
      NotifyResolver.Result notify)
      throws RestApiException, UpdateException {
    if (changes.isEmpty()) {
      return;
    }
    AccountState accountState = user.isIdentifiedUser() ? user.asIdentifiedUser().state() : null;
    try (BatchUpdate u = updateFactory.create(project, user, TimeUtil.nowTs())) {
      u.setNotify(notify);
      for (ChangeData change : changes) {
        if (!project.equals(change.project())) {
          throw new ResourceConflictException(
              String.format(
                  "Project name \"%s\" doesn't match \"%s\"",
                  change.project().get(), project.get()));
        }
        u.addOp(change.getId(), abandonOpFactory.create(accountState, msgTxt));
      }
      u.execute();

      if (cfg.getCleanupAccountPatchReview()) {
        cleanupAccountPatchReview(changes);
      }
    }
  }

  public void batchAbandon(
      BatchUpdate.Factory updateFactory,
      Project.NameKey project,
      CurrentUser user,
      Collection<ChangeData> changes,
      String msgTxt)
      throws RestApiException, UpdateException {
    batchAbandon(updateFactory, project, user, changes, msgTxt, NotifyResolver.Result.all());
  }

  public void batchAbandon(
      BatchUpdate.Factory updateFactory,
      Project.NameKey project,
      CurrentUser user,
      Collection<ChangeData> changes)
      throws RestApiException, UpdateException {
    batchAbandon(updateFactory, project, user, changes, "", NotifyResolver.Result.all());
  }

  private void cleanupAccountPatchReview(Collection<ChangeData> changes) {
    for (ChangeData change : changes) {
      accountPatchReviewStore.run(s -> s.clearReviewed(change.getId()));
    }
  }
}
