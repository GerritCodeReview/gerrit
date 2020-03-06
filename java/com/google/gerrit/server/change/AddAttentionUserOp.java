// Copyright (C) 2020 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionStatus;
import com.google.gerrit.entities.AttentionStatus.Operation;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** ö Add docs. Also check this and other existing names; "AttentionFoo" -> "AttentionUser" */
public class AddAttentionUserOp implements BatchUpdateOp {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    AddAttentionUserOp create(IdentifiedUser attentionUser);
  }

  private final ChangeData.Factory changeDataFactory;
  private final ChangeMessagesUtil cmUtil;
  private final IdentifiedUser attentionUser;

  @Inject
  AddAttentionUserOp(
      ChangeData.Factory changeDataFactory,
      ChangeMessagesUtil cmUtil,
      @Assisted IdentifiedUser attentionUser) {
    this.changeDataFactory = changeDataFactory;
    this.cmUtil = cmUtil;
    this.attentionUser = requireNonNull(attentionUser, "user");
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws RestApiException {
    ChangeData cd = changeDataFactory.create(ctx.getNotes());
    Map<Account.Id, AttentionStatus> attentionMap =
        cd.attentionStatus().stream()
            .collect(Collectors.toMap(AttentionStatus::account, Function.identity()));
    AttentionStatus existingEntry = attentionMap.get(attentionUser.getAccountId());
    if (existingEntry != null && existingEntry.operation() == Operation.ADD) {
      // ö Should probably log; this shouldn't happen.
      return false;
    }

    // TODO(zieren@google.com): Consider supporting validation listener.

    ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
    update.setAttentionUpdates(
        ImmutableList.of(
            AttentionStatus.createForWrite(
                attentionUser.getAccountId(), AttentionStatus.Operation.ADD, "why not?")));
    addMessage(ctx, update);
    return true;
  }

  private void addMessage(ChangeContext ctx, ChangeUpdate update) {
    String message = "Added to attention set: " + attentionUser.getNameEmail();
    cmUtil.addChangeMessage(
        update, ChangeMessagesUtil.newMessage(ctx, message, ChangeMessagesUtil.TAG_ADD_ATTENTION));
  }

  @Override
  public void postUpdate(Context ctx) {
    // ö Do this.
    //   try {
    //     SetAssigneeSender cm =
    //         setAssigneeSenderFactory.create(change.getProject(), change.getId(),
    // user.getAccountId());
    //     cm.setFrom(user.getAccountId());
    //     cm.send();
    //   } catch (Exception err) {
    //     logger.atSevere().withCause(err).log(
    //         "Cannot send email to new assignee of change %s", change.getId());
    //   }
    //   assigneeChanged.fire(
    //       change, ctx.getAccount(), oldAssignee != null ? oldAssignee.state() : null,
    // ctx.getWhen());
  }
}
