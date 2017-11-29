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

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.index.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class Abandon extends RetryingRestModifyView<ChangeResource, AbandonInput, ChangeInfo>
    implements UiAction<ChangeResource> {
  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson.Factory json;
  private final AbandonOp.Factory abandonOpFactory;
  private final NotifyUtil notifyUtil;

  @Inject
  Abandon(
      Provider<ReviewDb> dbProvider,
      ChangeJson.Factory json,
      RetryHelper retryHelper,
      AbandonOp.Factory abandonOpFactory,
      NotifyUtil notifyUtil) {
    super(retryHelper);
    this.dbProvider = dbProvider;
    this.json = json;
    this.abandonOpFactory = abandonOpFactory;
    this.notifyUtil = notifyUtil;
  }

  @Override
  protected ChangeInfo applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource req, AbandonInput input)
      throws RestApiException, UpdateException, OrmException, PermissionBackendException,
          IOException, ConfigInvalidException {
    req.permissions().database(dbProvider).check(ChangePermission.ABANDON);

    NotifyHandling notify = input.notify == null ? defaultNotify(req.getChange()) : input.notify;
    Change change =
        abandon(
            updateFactory,
            req.getNotes(),
            req.getUser(),
            input.message,
            notify,
            notifyUtil.resolveAccounts(input.notifyDetails));
    return json.noOptions().format(change);
  }

  private NotifyHandling defaultNotify(Change change) {
    return change.hasReviewStarted() ? NotifyHandling.ALL : NotifyHandling.OWNER;
  }

  public Change abandon(BatchUpdate.Factory updateFactory, ChangeNotes notes, CurrentUser user)
      throws RestApiException, UpdateException {
    return abandon(
        updateFactory,
        notes,
        user,
        "",
        defaultNotify(notes.getChange()),
        ImmutableListMultimap.of());
  }

  public Change abandon(
      BatchUpdate.Factory updateFactory, ChangeNotes notes, CurrentUser user, String msgTxt)
      throws RestApiException, UpdateException {
    return abandon(
        updateFactory,
        notes,
        user,
        msgTxt,
        defaultNotify(notes.getChange()),
        ImmutableListMultimap.of());
  }

  public Change abandon(
      BatchUpdate.Factory updateFactory,
      ChangeNotes notes,
      CurrentUser user,
      String msgTxt,
      NotifyHandling notifyHandling,
      ListMultimap<RecipientType, Account.Id> accountsToNotify)
      throws RestApiException, UpdateException {
    Account account = user.isIdentifiedUser() ? user.asIdentifiedUser().getAccount() : null;
    AbandonOp op = abandonOpFactory.create(account, msgTxt, notifyHandling, accountsToNotify);
    try (BatchUpdate u =
        updateFactory.create(dbProvider.get(), notes.getProjectName(), user, TimeUtil.nowTs())) {
      u.addOp(notes.getChangeId(), op).execute();
    }
    return op.getChange();
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
    Account account = user.isIdentifiedUser() ? user.asIdentifiedUser().getAccount() : null;
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
            abandonOpFactory.create(account, msgTxt, notifyHandling, accountsToNotify));
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

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    Change change = rsrc.getChange();
    return new UiAction.Description()
        .setLabel("Abandon")
        .setTitle("Abandon the change")
        .setVisible(
            and(
                change.getStatus().isOpen(),
                rsrc.permissions().database(dbProvider).testCond(ChangePermission.ABANDON)));
  }
}
