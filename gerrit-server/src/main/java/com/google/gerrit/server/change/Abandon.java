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
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.AbandonOp;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Abandon
    implements RestModifyView<ChangeResource, AbandonInput>, UiAction<ChangeResource> {
  private static final Logger log = LoggerFactory.getLogger(Abandon.class);

  private final Provider<ReviewDb> dbProvider;
  private final ChangeJson.Factory json;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final AbandonOp.Factory abandonOpFactory;
  private final NotifyUtil notifyUtil;

  @Inject
  Abandon(
      Provider<ReviewDb> dbProvider,
      ChangeJson.Factory json,
      BatchUpdate.Factory batchUpdateFactory,
      AbandonOp.Factory abandonOpFactory,
      NotifyUtil notifyUtil) {
    this.dbProvider = dbProvider;
    this.json = json;
    this.batchUpdateFactory = batchUpdateFactory;
    this.abandonOpFactory = abandonOpFactory;
    this.notifyUtil = notifyUtil;
  }

  @Override
  public ChangeInfo apply(ChangeResource req, AbandonInput input)
      throws RestApiException, UpdateException, OrmException {
    ChangeControl control = req.getControl();
    if (!control.canAbandon(dbProvider.get())) {
      throw new AuthException("abandon not permitted");
    }
    Change change =
        abandon(
            control, input.message, input.notify, notifyUtil.resolveAccounts(input.notifyDetails));
    return json.noOptions().format(change);
  }

  public Change abandon(ChangeControl control) throws RestApiException, UpdateException {
    return abandon(control, "", NotifyHandling.ALL, ImmutableListMultimap.of());
  }

  public Change abandon(ChangeControl control, String msgTxt)
      throws RestApiException, UpdateException {
    return abandon(control, msgTxt, NotifyHandling.ALL, ImmutableListMultimap.of());
  }

  public Change abandon(
      ChangeControl control,
      String msgTxt,
      NotifyHandling notifyHandling,
      ListMultimap<RecipientType, Account.Id> accountsToNotify)
      throws RestApiException, UpdateException {
    CurrentUser user = control.getUser();
    Account account = user.isIdentifiedUser() ? user.asIdentifiedUser().getAccount() : null;
    AbandonOp op = abandonOpFactory.create(account, msgTxt, notifyHandling, accountsToNotify);
    try (BatchUpdate u =
        batchUpdateFactory.create(
            dbProvider.get(),
            control.getProject().getNameKey(),
            control.getUser(),
            TimeUtil.nowTs())) {
      u.addOp(control.getId(), op).execute();
    }
    return op.getChange();
  }

  /**
   * If an extension has more than one changes to abandon that belong to the same project, they
   * should use the batch instead of abandoning one by one.
   *
   * <p>It's the caller's responsibility to ensure that all jobs inside the same batch have the
   * matching project from its ChangeControl. Violations will result in a ResourceConflictException.
   */
  public void batchAbandon(
      Project.NameKey project,
      CurrentUser user,
      Collection<ChangeControl> controls,
      String msgTxt,
      NotifyHandling notifyHandling,
      ListMultimap<RecipientType, Account.Id> accountsToNotify)
      throws RestApiException, UpdateException {
    if (controls.isEmpty()) {
      return;
    }
    Account account = user.isIdentifiedUser() ? user.asIdentifiedUser().getAccount() : null;
    try (BatchUpdate u =
        batchUpdateFactory.create(dbProvider.get(), project, user, TimeUtil.nowTs())) {
      for (ChangeControl control : controls) {
        if (!project.equals(control.getProject().getNameKey())) {
          throw new ResourceConflictException(
              String.format(
                  "Project name \"%s\" doesn't match \"%s\"",
                  control.getProject().getNameKey().get(), project.get()));
        }
        u.addOp(
            control.getId(),
            abandonOpFactory.create(account, msgTxt, notifyHandling, accountsToNotify));
      }
      u.execute();
    }
  }

  public void batchAbandon(
      Project.NameKey project, CurrentUser user, Collection<ChangeControl> controls, String msgTxt)
      throws RestApiException, UpdateException {
    batchAbandon(project, user, controls, msgTxt, NotifyHandling.ALL, ImmutableListMultimap.of());
  }

  public void batchAbandon(
      Project.NameKey project, CurrentUser user, Collection<ChangeControl> controls)
      throws RestApiException, UpdateException {
    batchAbandon(project, user, controls, "", NotifyHandling.ALL, ImmutableListMultimap.of());
  }

  @Override
  public UiAction.Description getDescription(ChangeResource resource) {
    boolean canAbandon = false;
    try {
      canAbandon = resource.getControl().canAbandon(dbProvider.get());
    } catch (OrmException e) {
      log.error("Cannot check canAbandon status. Assuming false.", e);
    }
    return new UiAction.Description()
        .setLabel("Abandon")
        .setTitle("Abandon the change")
        .setVisible(
            resource.getChange().getStatus().isOpen()
                && resource.getChange().getStatus() != Change.Status.DRAFT
                && canAbandon);
  }
}
