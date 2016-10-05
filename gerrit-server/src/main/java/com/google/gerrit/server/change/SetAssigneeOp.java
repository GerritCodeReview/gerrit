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
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfoCacheFactory;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.extensions.events.AssigneeChanged;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.validators.AssigneeValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

public class SetAssigneeOp extends BatchUpdate.Op {
  public interface Factory {
    SetAssigneeOp create(AssigneeInput input);
  }

  private final AccountsCollection accounts;
  private final ChangeMessagesUtil cmUtil;
  private final AccountInfoCacheFactory.Factory accountInfosFactory;
  private final DynamicSet<AssigneeValidationListener> validationListeners;
  private final AssigneeInput input;
  private final String anonymousCowardName;
  private final AssigneeChanged assigneeChanged;

  private Change change;
  private Account newAssignee;
  private Account oldAssignee;

  @AssistedInject
  SetAssigneeOp(AccountsCollection accounts,
      ChangeMessagesUtil cmUtil,
      AccountInfoCacheFactory.Factory accountInfosFactory,
      DynamicSet<AssigneeValidationListener> validationListeners,
      AssigneeChanged assigneeChanged,
      @AnonymousCowardName String anonymousCowardName,
      @Assisted AssigneeInput input) {
    this.accounts = accounts;
    this.cmUtil = cmUtil;
    this.accountInfosFactory = accountInfosFactory;
    this.validationListeners = validationListeners;
    this.assigneeChanged = assigneeChanged;
    this.anonymousCowardName = anonymousCowardName;
    this.input = input;
  }

  @Override
  public boolean updateChange(BatchUpdate.ChangeContext ctx)
      throws OrmException, RestApiException {
    if (!ctx.getControl().canEditAssignee()) {
      throw new AuthException("Changing Assignee not permitted");
    }
    change = ctx.getChange();
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    Optional<Account.Id> oldAssigneeId =
        Optional.fromNullable(change.getAssignee());
    if (input.assignee == null) {
      if (oldAssigneeId.isPresent()) {
        throw new BadRequestException("Cannot set Assignee to empty");
      }
      return false;
    }
    oldAssignee = null;
    if (oldAssigneeId.isPresent()) {
      oldAssignee = accountInfosFactory.create().get(oldAssigneeId.get());
    }
    IdentifiedUser newAssigneeUser = accounts.parse(input.assignee);
    if (oldAssigneeId.isPresent() &&
        oldAssigneeId.get().equals(newAssigneeUser.getAccountId())) {
      newAssignee = oldAssignee;
      return false;
    }
    if (!newAssigneeUser.getAccount().isActive()) {
      throw new UnprocessableEntityException(String.format(
          "Account of %s is not active", input.assignee));
    }
    if (!ctx.getControl().forUser(newAssigneeUser).isRefVisible()) {
      throw new AuthException(String.format(
          "Change %s is not visible to %s.",
          change.getChangeId(),
          input.assignee));
    }
    try {
      for (AssigneeValidationListener validator : validationListeners) {
        validator.validateAssignee(change, newAssigneeUser.getAccount());
      }
    } catch (ValidationException e) {
      throw new BadRequestException(e.getMessage());
    }
    // notedb
    update.setAssignee(newAssigneeUser.getAccountId());
    // reviewdb
    change.setAssignee(newAssigneeUser.getAccountId());
    this.newAssignee = newAssigneeUser.getAccount();
    addMessage(ctx, update, oldAssignee);
    return true;
  }

  private void addMessage(BatchUpdate.ChangeContext ctx, ChangeUpdate update,
      Account previousAssignee) throws OrmException {
    StringBuilder msg = new StringBuilder();
    msg.append("Assignee ");
    if (previousAssignee == null) {
      msg.append("added: ");
      msg.append(newAssignee.getName(anonymousCowardName));
    } else {
      msg.append("changed from: ");
      msg.append(previousAssignee.getName(anonymousCowardName));
      msg.append(" to: ");
      msg.append(newAssignee.getName(anonymousCowardName));
    }
    ChangeMessage cmsg = ChangeMessagesUtil.newMessage(ctx, msg.toString());
    cmUtil.addChangeMessage(ctx.getDb(), update, cmsg);
  }

  @Override
  public void postUpdate(Context ctx) throws OrmException {
    assigneeChanged.fire(change, ctx.getAccount(), oldAssignee, ctx.getWhen());
  }

  public Account getNewAssignee() {
    return newAssignee;
  }
}
