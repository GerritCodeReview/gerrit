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
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.AttentionSetUpdate.Operation;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Add a specified user to the attention set. */
public class AddToAttentionSetOp implements BatchUpdateOp {

  public interface Factory {
    AddToAttentionSetOp create(IdentifiedUser attentionUser, String reason);
  }

  private final ChangeData.Factory changeDataFactory;
  private final ChangeMessagesUtil cmUtil;
  private final IdentifiedUser attentionUser;
  private final String reason;

  @Inject
  AddToAttentionSetOp(
      ChangeData.Factory changeDataFactory,
      ChangeMessagesUtil cmUtil,
      @Assisted IdentifiedUser attentionUser,
      @Assisted String reason) {
    this.changeDataFactory = changeDataFactory;
    this.cmUtil = cmUtil;
    this.attentionUser = requireNonNull(attentionUser, "user");
    this.reason = requireNonNull(reason, "reason");
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws RestApiException {
    ChangeData changeData = changeDataFactory.create(ctx.getNotes());
    Map<Account.Id, AttentionSetUpdate> attentionMap =
        changeData.attentionSet().stream()
            .collect(Collectors.toMap(AttentionSetUpdate::account, Function.identity()));
    AttentionSetUpdate existingEntry = attentionMap.get(attentionUser.getAccountId());
    if (existingEntry != null && existingEntry.operation() == Operation.ADD) {
      return false;
    }

    ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
    update.setAttentionSetUpdates(
        ImmutableList.of(
            AttentionSetUpdate.createForWrite(
                attentionUser.getAccountId(), AttentionSetUpdate.Operation.ADD, reason)));
    addMessage(ctx, update);
    return true;
  }

  private void addMessage(ChangeContext ctx, ChangeUpdate update) {
    String message = "Added to attention set: " + attentionUser.getNameEmail();
    cmUtil.addChangeMessage(
        update,
        ChangeMessagesUtil.newMessage(ctx, message, ChangeMessagesUtil.TAG_UPDATE_ATTENTION_SET));
  }
}
