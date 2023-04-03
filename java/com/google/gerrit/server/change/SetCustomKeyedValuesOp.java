// Copyright (C) 2023 The Android Open Source Project
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
import static com.google.gerrit.server.change.CustomKeyedValuesUtil.extractCustomKeys;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.CustomKeyedValuesInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
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
import java.util.HashMap;
import java.util.Map;

public class SetCustomKeyedValuesOp implements BatchUpdateOp {
  public interface Factory {
    SetCustomKeyedValuesOp create(CustomKeyedValuesInput input);
  }

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
      PluginSetContext<CustomKeyedValueValidationListener> validationListeners,
      CustomKeyedValuesEdited customKeyedValuesEdited,
      @Assisted @Nullable CustomKeyedValuesInput input) {
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
      ImmutableMap<String, String> tryingToAdd = extractCustomKeyedValues(input.add);
      ImmutableSet<String> tryingToRemove = extractCustomKeys(input.remove);

      validationListeners.runEach(
          l -> l.validateCustomKeyedValues(update.getChange(), tryingToAdd, tryingToRemove),
          ValidationException.class);
      Map<String, String> newValues = new HashMap<>(existingCustomKeyedValues);
      Map<String, String> added = new HashMap<>();
      // Do the removes before the additions so that adding a key with a value while
      // removing the key consists of adding the key with that new value.
      for (String key : tryingToRemove) {
        if (!newValues.containsKey(key)) continue;
        update.deleteCustomKeyedValue(key);
        newValues.remove(key);
      }
      for (Map.Entry<String, String> add : tryingToAdd.entrySet()) {
        if (newValues.containsKey(add.getKey())
            && newValues.get(add.getKey()).equals(add.getValue())) {
          continue;
        }
        update.addCustomKeyedValue(add.getKey(), add.getValue());
        newValues.put(add.getKey(), add.getValue());
        added.put(add.getKey(), add.getValue());
      }
      toAdd = ImmutableMap.copyOf(added);
      toRemove =
          ImmutableSet.copyOf(
              Sets.filter(tryingToRemove, k -> existingCustomKeyedValues.containsKey(k)));
      updatedCustomKeyedValues = ImmutableMap.copyOf(newValues);
      return true;
    } catch (ValidationException | InvalidCustomKeyedValueException e) {
      throw new BadRequestException(e.getMessage(), e);
    }
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) {
    if (updated() && fireEvent) {
      customKeyedValuesEdited.fire(
          ctx.getChangeData(change),
          ctx.getAccount(),
          updatedCustomKeyedValues,
          toAdd,
          toRemove,
          ctx.getWhen());
    }
  }

  public ImmutableMap<String, String> getUpdatedCustomKeyedValues() {
    checkState(
        updatedCustomKeyedValues != null,
        "getUpdatedCustomKeyedValues() only valid after executing op");
    return updatedCustomKeyedValues;
  }

  private boolean updated() {
    return (toAdd != null && !toAdd.isEmpty()) || (toRemove != null && !toRemove.isEmpty());
  }
}
