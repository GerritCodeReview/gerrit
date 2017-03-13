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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.extensions.events.AssigneeChanged;
import com.google.gerrit.server.mail.send.SetAssigneeSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.validators.AssigneeValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetAssigneeOp implements BatchUpdateOp {
  private static final Logger log = LoggerFactory.getLogger(SetAssigneeOp.class);

  public interface Factory {
    SetAssigneeOp create(String assignee);
  }

  private final AccountsCollection accounts;
  private final ChangeMessagesUtil cmUtil;
  private final DynamicSet<AssigneeValidationListener> validationListeners;
  private final String assignee;
  private final AssigneeChanged assigneeChanged;
  private final SetAssigneeSender.Factory setAssigneeSenderFactory;
  private final Provider<IdentifiedUser> user;
  private final IdentifiedUser.GenericFactory userFactory;

  private Change change;
  private Account newAssignee;
  private Account oldAssignee;

  @AssistedInject
  SetAssigneeOp(
      AccountsCollection accounts,
      ChangeMessagesUtil cmUtil,
      DynamicSet<AssigneeValidationListener> validationListeners,
      AssigneeChanged assigneeChanged,
      SetAssigneeSender.Factory setAssigneeSenderFactory,
      Provider<IdentifiedUser> user,
      IdentifiedUser.GenericFactory userFactory,
      @Assisted String assignee) {
    this.accounts = accounts;
    this.cmUtil = cmUtil;
    this.validationListeners = validationListeners;
    this.assigneeChanged = assigneeChanged;
    this.setAssigneeSenderFactory = setAssigneeSenderFactory;
    this.user = user;
    this.userFactory = userFactory;
    this.assignee = checkNotNull(assignee);
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws OrmException, RestApiException {
    change = ctx.getChange();
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    IdentifiedUser newAssigneeUser = accounts.parse(assignee);
    newAssignee = newAssigneeUser.getAccount();
    IdentifiedUser oldAssigneeUser = null;
    if (change.getAssignee() != null) {
      oldAssigneeUser = userFactory.create(change.getAssignee());
      oldAssignee = oldAssigneeUser.getAccount();
      if (newAssignee.equals(oldAssignee)) {
        return false;
      }
    }
    if (!newAssignee.isActive()) {
      throw new UnprocessableEntityException(
          String.format("Account of %s is not active", assignee));
    }
    if (!ctx.getControl().forUser(newAssigneeUser).isRefVisible()) {
      throw new AuthException(
          String.format("Change %s is not visible to %s.", change.getChangeId(), assignee));
    }
    try {
      for (AssigneeValidationListener validator : validationListeners) {
        validator.validateAssignee(change, newAssignee);
      }
    } catch (ValidationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
    // notedb
    update.setAssignee(newAssignee.getId());
    // reviewdb
    change.setAssignee(newAssignee.getId());
    addMessage(ctx, update, oldAssigneeUser, newAssigneeUser);
    return true;
  }

  private void addMessage(
      ChangeContext ctx,
      ChangeUpdate update,
      IdentifiedUser previousAssignee,
      IdentifiedUser newAssignee)
      throws OrmException {
    StringBuilder msg = new StringBuilder();
    msg.append("Assignee ");
    if (previousAssignee == null) {
      msg.append("added: ");
      msg.append(newAssignee.getNameEmail());
    } else {
      msg.append("changed from: ");
      msg.append(previousAssignee.getNameEmail());
      msg.append(" to: ");
      msg.append(newAssignee.getNameEmail());
    }
    ChangeMessage cmsg =
        ChangeMessagesUtil.newMessage(ctx, msg.toString(), ChangeMessagesUtil.TAG_SET_ASSIGNEE);
    cmUtil.addChangeMessage(ctx.getDb(), update, cmsg);
  }

  @Override
  public void postUpdate(Context ctx) throws OrmException {
    try {
      SetAssigneeSender cm =
          setAssigneeSenderFactory.create(change.getProject(), change.getId(), newAssignee.getId());
      cm.setFrom(user.get().getAccountId());
      cm.send();
    } catch (Exception err) {
      log.error("Cannot send email to new assignee of change " + change.getId(), err);
    }
    assigneeChanged.fire(change, ctx.getAccount(), oldAssignee, ctx.getWhen());
  }

  public Account.Id getNewAssignee() {
    return newAssignee != null ? newAssignee.getId() : null;
  }
}
