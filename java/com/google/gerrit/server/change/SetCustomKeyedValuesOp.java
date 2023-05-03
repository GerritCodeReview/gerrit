// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.change.CustomKeyedValuesUtil.extractCustomKeyedValues;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.CustomKeyedValuesInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.change.CustomKeyedValuesUtil.InvalidCustomKeyedValueException;
import com.google.gerrit.server.extensions.events.CustomKeyedValuesEdited;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.gerrit.server.validators.CustomKeyedValueValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Collection;

public class SetCustomKeyedValuesOp implements BatchUpdateOp {
  public interface Factory {
    SetCustomKeyedValuesOp create(CustomKeyedValuesInput input);
  }

  private final ChangeMessagesUtil cmUtil;
  private final PluginSetContext<CustomKeyedValueValidationListener> validationListeners;
  private final CustomKeyedValuesEdited customKeyedValuesEdited;
  private final CustomKeyedValuesInput input;

  private boolean fireEvent = true;

  private Change change;
  private ImmutableMap<String, String> toAdd;
  private ImmutableSet<String> toRemove;
  private ImmutableMap<String, String> updatedCustomKeyedValues;

  @Inject
  SetCustomKeyedValuesOp(
      ChangeMessagesUtil cmUtil,
      PluginSetContext<HashtagValidationListener> validationListeners,
      CustomKeyedValuesEdited customKeyedValuesEdited,
      @Assisted @Nullable CustomKeyedValuesInput input) {
    this.cmUtil = cmUtil;
    this.validationListeners = validationListeners;
    this.customKeyedValuesEdited = customKeyedValuesEdited;
    this.input = input;
  }

  public SetCustomKeyedValuesOp setFireEvent(boolean fireEvent) {
    this.fireEvent = fireEvent;
    return this;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws AuthException, BadRequestException, MethodNotAllowedException, IOException {
    if (input == null || (input.add == null && input.remove == null)) {
      updatedCustomKeyedValues = ImmutableMap.of();
      return false;
    }

    change = ctx.getChange();
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    ChangeNotes notes = update.getNotes().load();

    try {
      ImmutableMap<String, String> existingCustomKeyedValues = notes.getCustomKeyedValues();
      Set<String> updated = new HashSet<>();
      toAdd = new HashSet<>(extractTags(input.add));
      toRemove = new HashSet<>(extractTags(input.remove));

      validationListeners.runEach(
          l -> l.validateHashtags(update.getChange(), toAdd, toRemove), ValidationException.class);
      updated.addAll(existingHashtags);
      toAdd.removeAll(existingHashtags);
      toRemove.retainAll(existingHashtags);
      if (updated()) {
        updated.addAll(toAdd);
        updated.removeAll(toRemove);
        update.setHashtags(updated);
        addMessage(ctx);
      }

      updatedCustomKeyedValues = ImmutableMap.copyOf(updated);
      return true;
    } catch (ValidationException | InvalidHashtagException e) {
      throw new BadRequestException(e.getMessage(), e);
    }
  }

  private void addMessage(ChangeContext ctx) {
    StringBuilder msg = new StringBuilder();
    appendHashtagMessage(msg, "added", toAdd);
    appendHashtagMessage(msg, "removed", toRemove);
    cmUtil.setChangeMessage(ctx, msg.toString(), ChangeMessagesUtil.TAG_SET_HASHTAGS);
  }

  private void appendHashtagMessage(StringBuilder b, String action, Set<String> hashtags) {
    if (isNullOrEmpty(hashtags)) {
      return;
    }

    if (b.length() > 0) {
      b.append("\n");
    }
    b.append("Hashtag");
    if (hashtags.size() > 1) {
      b.append("s");
    }
    b.append(" ");
    b.append(action);
    b.append(": ");
    b.append(Joiner.on(", ").join(Ordering.natural().sortedCopy(hashtags)));
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) {
    if (updated() && fireEvent) {
      hashtagsEdited.fire(
          ctx.getChangeData(change),
          ctx.getAccount(),
          updatedCustomKeyedValues,
          toAdd,
          toRemove,
          ctx.getWhen());
    }
  }

  public ImmutableMap<String, String> getUpdatedCustomKeyedValues() {
    checkState(updatedCustomKeyedValues != null, "getUpdatedCustomKeyedValues() only valid after executing op");
    return updatedCustomKeyedValues;
  }

  private boolean updated() {
    return !isNullOrEmpty(toAdd) || !isNullOrEmpty(toRemove);
  }

  private static boolean isNullOrEmpty(Collection<?> coll) {
    return coll == null || coll.isEmpty();
  }
}
