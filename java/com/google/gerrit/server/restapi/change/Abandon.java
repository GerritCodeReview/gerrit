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

import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.AbandonInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.AbandonOp;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.StoreSubmitRequirementsOp;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class Abandon
    implements RestModifyView<ChangeResource, AbandonInput>, UiAction<ChangeResource> {
  private final BatchUpdate.Factory updateFactory;
  private final ChangeJson.Factory json;
  private final AbandonOp.Factory abandonOpFactory;
  private final NotifyResolver notifyResolver;
  private final PatchSetUtil patchSetUtil;
  private final StoreSubmitRequirementsOp.Factory storeSubmitRequirementsOpFactory;

  @Inject
  Abandon(
      BatchUpdate.Factory updateFactory,
      ChangeJson.Factory json,
      AbandonOp.Factory abandonOpFactory,
      NotifyResolver notifyResolver,
      PatchSetUtil patchSetUtil,
      StoreSubmitRequirementsOp.Factory storeSubmitRequirementsOpFactory) {
    this.updateFactory = updateFactory;
    this.json = json;
    this.abandonOpFactory = abandonOpFactory;
    this.notifyResolver = notifyResolver;
    this.patchSetUtil = patchSetUtil;
    this.storeSubmitRequirementsOpFactory = storeSubmitRequirementsOpFactory;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource rsrc, AbandonInput input)
      throws RestApiException, UpdateException, PermissionBackendException, IOException,
          ConfigInvalidException {
    // Not allowed to abandon if the current patch set is locked.
    patchSetUtil.checkPatchSetNotLocked(rsrc.getNotes());

    rsrc.permissions().check(ChangePermission.ABANDON);

    NotifyHandling notify = input.notify == null ? defaultNotify(rsrc.getChange()) : input.notify;
    Change change =
        abandon(
            updateFactory,
            rsrc.getNotes(),
            rsrc.getUser(),
            input.message,
            notifyResolver.resolve(notify, input.notifyDetails));
    return Response.ok(json.noOptions().format(change));
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
        NotifyResolver.Result.create(defaultNotify(notes.getChange())));
  }

  public Change abandon(
      BatchUpdate.Factory updateFactory, ChangeNotes notes, CurrentUser user, String msgTxt)
      throws RestApiException, UpdateException {
    return abandon(
        updateFactory,
        notes,
        user,
        msgTxt,
        NotifyResolver.Result.create(defaultNotify(notes.getChange())));
  }

  public Change abandon(
      BatchUpdate.Factory updateFactory,
      ChangeNotes notes,
      CurrentUser user,
      String msgTxt,
      NotifyResolver.Result notify)
      throws RestApiException, UpdateException {
    AccountState accountState = user.isIdentifiedUser() ? user.asIdentifiedUser().state() : null;
    AbandonOp op = abandonOpFactory.create(accountState, msgTxt);
    try (BatchUpdate u = updateFactory.create(notes.getProjectName(), user, TimeUtil.nowTs())) {
      u.setNotify(notify);
      u.addOp(notes.getChangeId(), op);
      u.addOp(notes.getChangeId(), storeSubmitRequirementsOpFactory.create());
      u.execute();
    }
    return op.getChange();
  }

  @Override
  public UiAction.Description getDescription(ChangeResource rsrc) throws IOException {
    UiAction.Description description =
        new UiAction.Description()
            .setLabel("Abandon")
            .setTitle("Abandon the change")
            .setVisible(false);

    Change change = rsrc.getChange();
    if (!change.isNew()) {
      return description;
    }
    if (patchSetUtil.isPatchSetLocked(rsrc.getNotes())) {
      return description;
    }
    return description.setVisible(rsrc.permissions().testOrFalse(ChangePermission.ABANDON));
  }
}
