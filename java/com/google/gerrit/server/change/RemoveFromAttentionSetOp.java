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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.Objects.requireNonNull;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.AttentionSetUpdate.Operation;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Remove a specified user from the attention set. */
public class RemoveFromAttentionSetOp implements BatchUpdateOp {

  public interface Factory {
    RemoveFromAttentionSetOp create(Set<Account.Id> attentionUserIdSet, String reason);
  }

  private final ChangeData.Factory changeDataFactory;
  private final ChangeMessagesUtil cmUtil;
  private final Set<Account.Id> attentionUserIdSet;
  private final String reason;

  @Inject
  RemoveFromAttentionSetOp(
      ChangeData.Factory changeDataFactory,
      ChangeMessagesUtil cmUtil,
      @Assisted Set<Account.Id> attentionUserIdSet,
      @Assisted String reason) {
    this.changeDataFactory = changeDataFactory;
    this.cmUtil = cmUtil;
    this.attentionUserIdSet = requireNonNull(attentionUserIdSet, "users");
    this.reason = requireNonNull(reason, "reason");
  }

  @Override
  public boolean updateChange(ChangeContext ctx) throws RestApiException {
    ChangeData changeData = changeDataFactory.create(ctx.getNotes());
    Map<Account.Id, AttentionSetUpdate> attentionMap =
        changeData.attentionSet().stream()
            .collect(toImmutableMap(AttentionSetUpdate::account, Function.identity()));
    ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
    String message = "Removed attention set: ";
    Set<AttentionSetUpdate> attentionSetUpdates = new HashSet();
    for (Account.Id attentionUserId : attentionUserIdSet) {
      AttentionSetUpdate existingEntry = attentionMap.get(attentionUserId);
      if (existingEntry == null || existingEntry.operation() == Operation.REMOVE) {
        // We can ignore accounts that are not in the attention set.
        continue;
      }
      attentionSetUpdates.add(
          AttentionSetUpdate.createForWrite(
              attentionUserId, AttentionSetUpdate.Operation.REMOVE, reason));
      message += attentionUserId + " ";
    }
    if (!attentionSetUpdates.isEmpty()) {
      addMessage(ctx, update, message);
      update.setAttentionSetUpdates(attentionSetUpdates);
      return true;
    }
    return false;
  }

  private void addMessage(ChangeContext ctx, ChangeUpdate update, String message) {
    cmUtil.addChangeMessage(
        update,
        ChangeMessagesUtil.newMessage(ctx, message, ChangeMessagesUtil.TAG_UPDATE_ATTENTION_SET));
  }
}
