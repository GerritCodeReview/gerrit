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
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class DeleteAssignee implements
    RestModifyView<ChangeResource, AssigneeInput> {
  private BatchUpdate.Factory batchUpdateFactory;
  private final AccountsCollection accounts;
  private final NotesMigration notesMigration;
  private final ChangeMessagesUtil cmUtil;
  private final Provider<ReviewDb> db;
  private String anonymousCowardName;

  @Inject
  DeleteAssignee(AccountsCollection accounts,
      NotesMigration notesMigration,
      BatchUpdate.Factory batchUpdateFactory,
      ChangeMessagesUtil cmUtil,
      Provider<ReviewDb> db,
      @AnonymousCowardName String anonymousCowardName) {
    this.batchUpdateFactory = batchUpdateFactory;
    this.accounts = accounts;
    this.notesMigration = notesMigration;
    this.cmUtil = cmUtil;
    this.db = db;
    this.anonymousCowardName = anonymousCowardName;
  }

  @Override
  public Object apply(ChangeResource rsrc, AssigneeInput input)
      throws AuthException, BadRequestException, ResourceConflictException,
      Exception {
    try (BatchUpdate bu = batchUpdateFactory.create(db.get(),
        rsrc.getProject(),
        rsrc.getUser(), TimeUtil.nowTs())) {
      Op op = new Op(input);
      bu.addOp(rsrc.getChange().getId(), op);
      bu.execute();
    }
    return Response.none();
  }

  private class Op extends BatchUpdate.Op {
    private AssigneeInput input;

    Op(AssigneeInput input) {
      this.input = input;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws OrmException, AuthException, UnprocessableEntityException,
        BadRequestException {
      if (!notesMigration.readChanges()) {
        throw new BadRequestException(
            "Cannot add Assignee; NoteDb is disabled");
      }
      if (!ctx.getControl().canEditAssignee()) {
        throw new AuthException("Delete Assignee not permitted");
      }
      if (input.assignee == null) {
        return false;
      }
      IdentifiedUser toDelete = accounts.parse(input.assignee);
      ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
      Optional<Account.Id> currentAssignee = update.getNotes().getAssignee();
      if (currentAssignee.isPresent() &&
          currentAssignee.get().get() != toDelete.getAccount().getId().get()) {
        throw new BadRequestException(
            String.format("User %s is not assigned to change %s",
                input.assignee, ctx.getChange().getChangeId()));
      }
      update.deleteAssignee();
      addMessage(ctx, update, toDelete.getAccount());
      return true;
    }

    private void addMessage(BatchUpdate.ChangeContext ctx,
        ChangeUpdate update, Account deleted) throws OrmException {
      StringBuilder msg = new StringBuilder();
      msg.append("Assignee ");
      msg.append("deleted: ");
      msg.append(deleted.getName(anonymousCowardName));
      ChangeMessage cmsg = new ChangeMessage(
          new ChangeMessage.Key(
              ctx.getChange().getId(),
              ChangeUtil.messageUUID(ctx.getDb())),
          ctx.getAccountId(), ctx.getWhen(),
          ctx.getChange().currentPatchSetId());
      cmsg.setMessage(msg.toString());
      cmUtil.addChangeMessage(ctx.getDb(), update, cmsg);
    }
  }
}
