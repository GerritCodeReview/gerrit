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

import com.google.common.base.Optional;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.account.AccountJson;
import com.google.gerrit.server.change.DeleteAssignee.Input;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
public class DeleteAssignee implements
    RestModifyView<ChangeResource, Input> {
  public static class Input {

  }
  private BatchUpdate.Factory batchUpdateFactory;
  private final NotesMigration notesMigration;
  private final ChangeMessagesUtil cmUtil;
  private final Provider<ReviewDb> db;
  private final AccountInfoCacheFactory.Factory accountInfos;
  private final String anonymousCowardName;

  @Inject
  DeleteAssignee(NotesMigration notesMigration,
      BatchUpdate.Factory batchUpdateFactory,
      ChangeMessagesUtil cmUtil,
      Provider<ReviewDb> db,
      AccountInfoCacheFactory.Factory accountInfosFactory,
      @AnonymousCowardName String anonymousCowardName) {
    this.batchUpdateFactory = batchUpdateFactory;
    this.notesMigration = notesMigration;
    this.cmUtil = cmUtil;
    this.db = db;
    this.accountInfos = accountInfosFactory;
    this.anonymousCowardName = anonymousCowardName;
  }

  @Override
  public Response<AccountInfo> apply(ChangeResource rsrc, Input input)
      throws RestApiException, UpdateException
       {
    try (BatchUpdate bu = batchUpdateFactory.create(db.get(),
        rsrc.getProject(),
        rsrc.getUser(), TimeUtil.nowTs())) {
      Op op = new Op();
      bu.addOp(rsrc.getChange().getId(), op);
      bu.execute();
      if (op.getDeletedAssignee() == null) {
        return Response.none();
      }
      return Response.ok(AccountJson.toAccountInfo(op.getDeletedAssignee()));
    }
  }

  private class Op extends BatchUpdate.Op {
    private Account deletedAssignee;

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws RestApiException, OrmException{
      if (!notesMigration.readChanges()) {
        throw new BadRequestException(
            "Cannot add Assignee; NoteDb is disabled");
      }
      if (!ctx.getControl().canEditAssignee()) {
        throw new AuthException("Delete Assignee not permitted");
      }
      ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
      Optional<Account.Id> currentAssigneeId = update.getNotes().getAssignee();
      if (!currentAssigneeId.isPresent()) {
        return false;
      }
      Account account = accountInfos.create().get(currentAssigneeId.get());
      update.setAssignee(Optional.absent());
      addMessage(ctx, update, account);
      deletedAssignee = account;
      return true;
    }

    public Account getDeletedAssignee() {
      return deletedAssignee;
    }

    private void addMessage(BatchUpdate.ChangeContext ctx,
        ChangeUpdate update, Account deleted) throws OrmException {
      ChangeMessage cmsg = new ChangeMessage(
          new ChangeMessage.Key(
              ctx.getChange().getId(),
              ChangeUtil.messageUUID(ctx.getDb())),
          ctx.getAccountId(), ctx.getWhen(),
          ctx.getChange().currentPatchSetId());
      cmsg.setMessage(
          "Assignee deleted: " + deleted.getName(anonymousCowardName));
      cmUtil.addChangeMessage(ctx.getDb(), update, cmsg);
    }
  }
}
