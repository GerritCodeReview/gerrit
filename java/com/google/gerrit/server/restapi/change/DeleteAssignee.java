// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.extensions.events.AssigneeChanged;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class DeleteAssignee
    extends RetryingRestModifyView<ChangeResource, Input, Response<AccountInfo>> {

  private final ChangeMessagesUtil cmUtil;
  private final AssigneeChanged assigneeChanged;
  private final IdentifiedUser.GenericFactory userFactory;
  private final AccountLoader.Factory accountLoaderFactory;

  @Inject
  DeleteAssignee(
      RetryHelper retryHelper,
      ChangeMessagesUtil cmUtil,
      AssigneeChanged assigneeChanged,
      IdentifiedUser.GenericFactory userFactory,
      AccountLoader.Factory accountLoaderFactory) {
    super(retryHelper);
    this.cmUtil = cmUtil;
    this.assigneeChanged = assigneeChanged;
    this.userFactory = userFactory;
    this.accountLoaderFactory = accountLoaderFactory;
  }

  @Override
  protected Response<AccountInfo> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource rsrc, Input input)
      throws RestApiException, UpdateException, PermissionBackendException {
    rsrc.permissions().check(ChangePermission.EDIT_ASSIGNEE);

    try (BatchUpdate bu =
        updateFactory.create(rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      Op op = new Op();
      bu.addOp(rsrc.getChange().getId(), op);
      bu.execute();
      Account.Id deletedAssignee = op.getDeletedAssignee();
      return deletedAssignee == null
          ? Response.none()
          : Response.ok(accountLoaderFactory.create(true).fillOne(deletedAssignee));
    }
  }

  private class Op implements BatchUpdateOp {
    private Change change;
    private AccountState deletedAssignee;

    @Override
    public boolean updateChange(ChangeContext ctx) throws RestApiException {
      change = ctx.getChange();
      ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
      Account.Id currentAssigneeId = change.getAssignee();
      if (currentAssigneeId == null) {
        return false;
      }
      IdentifiedUser deletedAssigneeUser = userFactory.create(currentAssigneeId);
      deletedAssignee = deletedAssigneeUser.state();
      // noteDb
      update.removeAssignee();
      // reviewDb
      change.setAssignee(null);
      addMessage(ctx, update, deletedAssigneeUser);
      return true;
    }

    public Account.Id getDeletedAssignee() {
      return deletedAssignee != null ? deletedAssignee.getAccount().getId() : null;
    }

    private void addMessage(
        ChangeContext ctx, ChangeUpdate update, IdentifiedUser deletedAssignee) {
      ChangeMessage cmsg =
          ChangeMessagesUtil.newMessage(
              ctx,
              "Assignee deleted: " + deletedAssignee.getNameEmail(),
              ChangeMessagesUtil.TAG_DELETE_ASSIGNEE);
      cmUtil.addChangeMessage(update, cmsg);
    }

    @Override
    public void postUpdate(Context ctx) {
      assigneeChanged.fire(change, ctx.getAccount(), deletedAssignee, ctx.getWhen());
    }
  }
}
