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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.PatchSetUtil.isPatchSetLocked;

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
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.AbandonOp;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Abandon extends RetryingRestModifyView<ChangeResource, AbandonInput, ChangeInfo>
    implements UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Abandon.class);

  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson.Factory json;
  private final AbandonOp.Factory abandonOpFactory;
  private final NotifyUtil notifyUtil;
  private final ApprovalsUtil approvalsUtil;
  private final ProjectCache projectCache;

  @Inject
  Abandon(
      Provider<ReviewDb> dbProvider,
      ChangeJson.Factory json,
      RetryHelper retryHelper,
      AbandonOp.Factory abandonOpFactory,
      NotifyUtil notifyUtil,
      ApprovalsUtil approvalsUtil,
      ProjectCache projectCache) {
    super(retryHelper);
    this.dbProvider = dbProvider;
    this.json = json;
    this.abandonOpFactory = abandonOpFactory;
    this.notifyUtil = notifyUtil;
    this.approvalsUtil = approvalsUtil;
    this.projectCache = projectCache;
  }

  @Override
  protected ChangeInfo applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, AbandonInput input)
      throws RestApiException, UpdateException, OrmException, PermissionBackendException,
          IOException, ConfigInvalidException {
    // Not allowed to abandon if the current patch set is locked.
    if (isPatchSetLocked(
        approvalsUtil, projectCache, dbProvider.get(), rsrc.getNotes(), rsrc.getUser())) {
      throw new ResourceConflictException(
          String.format("The current patch set of change %s is locked", rsrc.getId()));
    }

    rsrc.permissions().database(dbProvider).check(ChangePermission.ABANDON);

    NotifyHandling notify = input.notify == null ? defaultNotify(rsrc.getChange()) : input.notify;
    Change change =
        abandon(
            updateFactory,
            rsrc.getNotes(),
            rsrc.getUser(),
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
    AccountState accountState = user.isIdentifiedUser() ? user.asIdentifiedUser().state() : null;
    AbandonOp op = abandonOpFactory.create(accountState, msgTxt, notifyHandling, accountsToNotify);
    try (BatchUpdate u =
        updateFactory.create(dbProvider.get(), notes.getProjectName(), user, TimeUtil.nowTs())) {
      u.addOp(notes.getChangeId(), op).execute();
    }
    return op.getChange();
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) {
    UiAction.Description description =
        new UiAction.Description()
            .setLabel("Abandon")
            .setTitle("Abandon the change")
            .setVisible(false);

    Change change = rsrc.getChange();
    if (!change.getStatus().isOpen()) {
      return description;
    }

    try {
      if (isPatchSetLocked(
          approvalsUtil, projectCache, dbProvider.get(), rsrc.getNotes(), rsrc.getUser())) {
        return description;
      }
    } catch (OrmException | IOException e) {
      log.error(
          String.format(
              "Failed to check if the current patch set of change %s is locked", change.getId()),
          e);
      return description;
    }

    return description.setVisible(rsrc.permissions().testOrFalse(ChangePermission.ABANDON));
  }
}
