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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.extensions.events.HashtagsEdited;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.BatchUpdate.Context;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.notedb.NotesMigration;
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

  private final NotesMigration notesMigration;
  private final ChangeMessagesUtil cmUtil;
  private final DynamicSet<HashtagValidationListener> validationListeners;
  private final HashtagsEdited hashtagsEdited;
  private final HashtagsInput input;

  private boolean fireEvent = true;

  private Change change;
  private Set<String> toAdd;
  private Set<String> toRemove;
  private ImmutableSortedSet<String> updatedHashtags;

  @AssistedInject
  SetHashtagsOp(
      NotesMigration notesMigration,
      ChangeMessagesUtil cmUtil,
      DynamicSet<HashtagValidationListener> validationListeners,
      HashtagsEdited hashtagsEdited,
      @Assisted @Nullable HashtagsInput input) {
    this.notesMigration = notesMigration;
    this.cmUtil = cmUtil;
    this.validationListeners = validationListeners;
    this.hashtagsEdited = hashtagsEdited;
    this.input = input;
  }

  public SetHashtagsOp setFireEvent(boolean fireEvent) {
    this.fireEvent = fireEvent;
    return this;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws AuthException, BadRequestException, MethodNotAllowedException, OrmException,
          IOException {
    if (!notesMigration.readChanges()) {
      throw new MethodNotAllowedException("Cannot add hashtags; NoteDb is disabled");
    }
    if (input == null || (input.add == null && input.remove == null)) {
      updatedHashtags = ImmutableSortedSet.of();
      return false;
    }
    if (!ctx.getControl().canEditHashtags()) {
      throw new AuthException("Editing hashtags not permitted");
    }
    change = ctx.getChange();
    ChangeUpdate update = ctx.getUpdate(change.currentPatchSetId());
    ChangeNotes notes = update.getNotes().load();

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

    updated.addAll(existingHashtags);
    toAdd.removeAll(existingHashtags);
    toRemove.retainAll(existingHashtags);
    if (updated()) {
      updated.addAll(toAdd);
      updated.removeAll(toRemove);
      update.setHashtags(updated);
      addMessage(ctx, update);
    }

    updatedHashtags = ImmutableSortedSet.copyOf(updated);
    return true;
  }

  private void addMessage(ChangeContext ctx, ChangeUpdate update) throws OrmException {
    StringBuilder msg = new StringBuilder();
    appendHashtagMessage(msg, "added", toAdd);
    appendHashtagMessage(msg, "removed", toRemove);
    ChangeMessage cmsg =
        ChangeMessagesUtil.newMessage(ctx, msg.toString(), ChangeMessagesUtil.TAG_SET_HASHTAGS);
    cmUtil.addChangeMessage(ctx.getDb(), update, cmsg);
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
  public void postUpdate(Context ctx) throws OrmException {
    if (updated() && fireEvent) {
      hashtagsEdited.fire(
          change, ctx.getAccount(), updatedHashtags, toAdd, toRemove, ctx.getWhen());
    }
  }

  public ImmutableSortedSet<String> getUpdatedHashtags() {
    checkState(updatedHashtags != null, "getUpdatedHashtags() only valid after executing op");
    return updatedHashtags;
  }

  private boolean updated() {
    return !isNullOrEmpty(toAdd) || !isNullOrEmpty(toRemove);
  }

  private static boolean isNullOrEmpty(Collection<?> coll) {
    return coll == null || coll.isEmpty();
  }
}
