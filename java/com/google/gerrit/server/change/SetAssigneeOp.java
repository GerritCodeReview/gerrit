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

import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.AssigneeChanged;
import com.google.gerrit.server.mail.send.SetAssigneeSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.validators.AssigneeValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

public class SetAssigneeOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    SetAssigneeOp create(IdentifiedUser assignee);
  }

  private final ChangeMessagesUtil cmUtil;
  private final PluginSetContext<AssigneeValidationListener> validationListeners;
  private final IdentifiedUser newAssignee;
  private final AssigneeChanged assigneeChanged;
  private final SetAssigneeSender.Factory setAssigneeSenderFactory;
  private final Provider<IdentifiedUser> user;
  private final IdentifiedUser.GenericFactory userFactory;

  private Change change;
  private IdentifiedUser oldAssignee;

  @Inject
  SetAssigneeOp(
      ChangeMessagesUtil cmUtil,
      PluginSetContext<AssigneeValidationListener> validationListeners,
      AssigneeChanged assigneeChanged,
      SetAssigneeSender.Factory setAssigneeSenderFactory,
      Provider<IdentifiedUser> user,
      IdentifiedUser.GenericFactory userFactory,
      @Assisted IdentifiedUser newAssignee) {
    this.cmUtil = cmUtil;
    this.validationListeners = validationListeners;
    this.assigneeChanged = assigneeChanged;
    this.setAssigneeSenderFactory = setAssigneeSenderFactory;
    this.user = user;
    this.userFactory = userFactory;
    this.newAssignee = requireNonNull(newAssignee, "assignee");
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws StorageException, RestApiException {
    change = ctx.getChange();
    if (newAssignee.getAccountId().equals(change.getAssignee())) {
      return false;
    }
    try {
      validationListeners.runEach(
          l -> l.validateAssignee(change, newAssignee.getAccount()), ValidationException.class);
    } catch (ValidationException e) {
      throw new ResourceConflictException(e.getMessage(), e);
    }

    if (change.getAssignee() != null) {
      oldAssignee = userFactory.create(change.getAssignee());
    }

    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    // notedb
    update.setAssignee(newAssignee.getAccountId());
    // reviewdb
    change.setAssignee(newAssignee.getAccountId());
    addMessage(ctx, update);
    return true;
  }

  private void addMessage(ChangeContext ctx, ChangeUpdate update) {
    StringBuilder msg = new StringBuilder();
    msg.append("Assignee ");
    if (oldAssignee == null) {
      msg.append("added: ");
      msg.append(newAssignee.getNameEmail());
    } else {
      msg.append("changed from: ");
      msg.append(oldAssignee.getNameEmail());
      msg.append(" to: ");
      msg.append(newAssignee.getNameEmail());
    }
    ChangeMessage cmsg =
        ChangeMessagesUtil.newMessage(ctx, msg.toString(), ChangeMessagesUtil.TAG_SET_ASSIGNEE);
    cmUtil.addChangeMessage(update, cmsg);
  }

  @Override
  public void postUpdate(Context ctx) throws StorageException {
    try {
      SetAssigneeSender cm =
          setAssigneeSenderFactory.create(
              change.getProject(), change.getId(), newAssignee.getAccountId());
      cm.setFrom(user.get().getAccountId());
      cm.send();
    } catch (Exception err) {
      logger.atSevere().withCause(err).log(
          "Cannot send email to new assignee of change %s", change.getId());
    }
    assigneeChanged.fire(
        change, ctx.getAccount(), oldAssignee != null ? oldAssignee.state() : null, ctx.getWhen());
  }
}
