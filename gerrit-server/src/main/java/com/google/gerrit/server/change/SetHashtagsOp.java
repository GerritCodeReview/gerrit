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
import static com.google.gerrit.server.change.HashtagsUtil.extractTags;

import com.google.common.collect.ImmutableSortedSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.extensions.events.HashtagsEdited;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.validators.HashtagValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class SetHashtagsOp extends BatchUpdate.Op {
  public interface Factory {
    SetHashtagsOp create(HashtagsInput input);
  }

  private final DynamicSet<HashtagValidationListener> validationListeners;
  private final HashtagsEdited hashtagsEdited;
  private final HashtagsInput input;

  private boolean runHooks = true;

  private Change change;
  private Set<String> toAdd;
  private Set<String> toRemove;
  private ImmutableSortedSet<String> updatedHashtags;

  @AssistedInject
  SetHashtagsOp(
      DynamicSet<HashtagValidationListener> validationListeners,
      HashtagsEdited hashtagsEdited,
      @Assisted @Nullable HashtagsInput input) {
    this.validationListeners = validationListeners;
    this.hashtagsEdited = hashtagsEdited;
    this.input = input;
  }

  public SetHashtagsOp setRunHooks(boolean runHooks) {
    this.runHooks = runHooks;
    return this;
  }

  @Override
  public void updateChange(ChangeContext ctx)
      throws AuthException, BadRequestException, OrmException, IOException {
    if (input == null
        || (input.add == null && input.remove == null)) {
      updatedHashtags = ImmutableSortedSet.of();
      return;
    }
    if (!ctx.getChangeControl().canEditHashtags()) {
      throw new AuthException("Editing hashtags not permitted");
    }
    ChangeUpdate update = ctx.getChangeUpdate();
    ChangeNotes notes = update.getChangeNotes().load();

    Set<String> existingHashtags = notes.getHashtags();
    Set<String> updated = new HashSet<>();
    toAdd = new HashSet<>(extractTags(input.add));
    toRemove = new HashSet<>(extractTags(input.remove));

    try {
      for (HashtagValidationListener validator : validationListeners) {
        validator.validateHashtags(update.getChange(), toAdd, toRemove);
      }
    } catch (ValidationException e) {
      throw new BadRequestException(e.getMessage());
    }

    if (existingHashtags != null && !existingHashtags.isEmpty()) {
      updated.addAll(existingHashtags);
      toAdd.removeAll(existingHashtags);
      toRemove.retainAll(existingHashtags);
    }
    if (updated()) {
      updated.addAll(toAdd);
      updated.removeAll(toRemove);
      update.setHashtags(updated);
    }

    change = update.getChange();
    updatedHashtags = ImmutableSortedSet.copyOf(updated);
  }

  @Override
  public void postUpdate(Context ctx) throws OrmException {
    if (updated() && runHooks) {
      hashtagsEdited.fire(change, ctx.getUser().getAccountId(), updatedHashtags,
          toAdd, toRemove);
    }
  }

  public ImmutableSortedSet<String> getUpdatedHashtags() {
    checkState(updatedHashtags != null,
        "getUpdatedHashtags() only valid after executing op");
    return updatedHashtags;
  }

  private boolean updated() {
    return !isNullOrEmpty(toAdd) || !isNullOrEmpty(toRemove);
  }

  private static boolean isNullOrEmpty(Collection<?> coll) {
    return coll == null || coll.isEmpty();
  }
}
