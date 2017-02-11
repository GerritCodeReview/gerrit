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

package com.google.gerrit.server.change;

import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.change.DeleteAssignee.Input;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.extensions.events.AssigneeChanged;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DeleteAssignee implements RestModifyView<ChangeResource, Input> {
  public static class Input {}

  private final BatchUpdate.Factory batchUpdateFactory;
  private final ChangeMessagesUtil cmUtil;
  private final Provider<ReviewDb> db;
  private final AssigneeChanged assigneeChanged;
  private final IdentifiedUser.GenericFactory userFactory;
  private final AccountLoader.Factory accountLoaderFactory;

  @Inject
  DeleteAssignee(
      BatchUpdate.Factory batchUpdateFactory,
      ChangeMessagesUtil cmUtil,
      Provider<ReviewDb> db,
      AssigneeChanged assigneeChanged,
      IdentifiedUser.GenericFactory userFactory,
      AccountLoader.Factory accountLoaderFactory) {
    this.batchUpdateFactory = batchUpdateFactory;
    this.cmUtil = cmUtil;
    this.db = db;
    this.assigneeChanged = assigneeChanged;
    this.userFactory = userFactory;
    this.accountLoaderFactory = accountLoaderFactory;
  }

  @Override
  public Response<AccountInfo> apply(ChangeResource rsrc, Input input)
      throws RestApiException, UpdateException, OrmException {
    try (BatchUpdate bu =
        batchUpdateFactory.create(db.get(), rsrc.getProject(), rsrc.getUser(), TimeUtil.nowTs())) {
      Op op = new Op();
      bu.addOp(rsrc.getChange().getId(), op);
      bu.execute();
      Account.Id deletedAssignee = op.getDeletedAssignee();
      return deletedAssignee == null
          ? Response.none()
          : Response.ok(accountLoaderFactory.create(true).fillOne(deletedAssignee));
    }
  }

  private class Op extends BatchUpdate.Op {
    private Change change;
    private Account deletedAssignee;

    @Override
    public boolean updateChange(ChangeContext ctx) throws RestApiException, OrmException {
      if (!ctx.getControl().canEditAssignee()) {
        throw new AuthException("Delete Assignee not permitted");
      }
      change = ctx.getChange();
      ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
      Account.Id currentAssigneeId = change.getAssignee();
      if (currentAssigneeId == null) {
        return false;
      }
      IdentifiedUser deletedAssigneeUser = userFactory.create(currentAssigneeId);
      deletedAssignee = deletedAssigneeUser.getAccount();
      // noteDb
      update.removeAssignee();
      // reviewDb
      change.setAssignee(null);
      addMessage(ctx, update, deletedAssigneeUser);
      return true;
    }

    public Account.Id getDeletedAssignee() {
      return deletedAssignee != null ? deletedAssignee.getId() : null;
    }

    private void addMessage(
        BatchUpdate.ChangeContext ctx, ChangeUpdate update, IdentifiedUser deletedAssignee)
        throws OrmException {
      ChangeMessage cmsg =
          ChangeMessagesUtil.newMessage(
              ctx,
              "Assignee deleted: " + deletedAssignee.getNameEmail(),
              ChangeMessagesUtil.TAG_DELETE_ASSIGNEE);
      cmUtil.addChangeMessage(ctx.getDb(), update, cmsg);
    }

    @Override
    public void postUpdate(Context ctx) throws OrmException {
      assigneeChanged.fire(change, ctx.getAccount(), deletedAssignee, ctx.getWhen());
    }
  }
}
