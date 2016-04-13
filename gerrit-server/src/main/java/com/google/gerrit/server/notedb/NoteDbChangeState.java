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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The state of all relevant NoteDb refs across all repos corresponding to a
 * given Change entity.
 * <p>
 * Stored serialized in the {@code Change#noteDbState} field, and used to
 * determine whether the state in NoteDb is out of date.
 * <p>
 * Serialized in the form:
 * <pre>
 *   [meta-sha],[account1]=[drafts-sha],[account2]=[drafts-sha]...
 * </pre>
 * in numeric account ID order, with hex SHA-1s for human readability.
 */
public class NoteDbChangeState {
  @AutoValue
  public abstract static class Delta {
    static Delta create(Change.Id changeId, Optional<ObjectId> newChangeMetaId,
        Map<Account.Id, ObjectId> newDraftIds) {
      if (newDraftIds == null) {
        newDraftIds = ImmutableMap.of();
      }
      return new AutoValue_NoteDbChangeState_Delta(
          changeId,
          newChangeMetaId,
          ImmutableMap.copyOf(newDraftIds));
    }

    abstract Change.Id changeId();
    abstract Optional<ObjectId> newChangeMetaId();
    abstract ImmutableMap<Account.Id, ObjectId> newDraftIds();
  }

  public static NoteDbChangeState parse(Change c) {
    return parse(c.getId(), c.getNoteDbState());
  }

  @VisibleForTesting
  static NoteDbChangeState parse(Change.Id id, String str) {
    if (str == null) {
      return null;
    }
    List<String> parts = Splitter.on(',').splitToList(str);
    checkArgument(!parts.isEmpty(),
        "invalid state string for change %s: %s", id, str);
    ObjectId changeMetaId = ObjectId.fromString(parts.get(0));
    Map<Account.Id, ObjectId> draftIds =
        Maps.newHashMapWithExpectedSize(parts.size() - 1);
    Splitter s = Splitter.on('=');
    for (int i = 1; i < parts.size(); i++) {
      String p = parts.get(i);
      List<String> draftParts = s.splitToList(p);
      checkArgument(draftParts.size() == 2,
          "invalid draft state part for change %s: %s", id, p);
      draftIds.put(Account.Id.parse(draftParts.get(0)),
          ObjectId.fromString(draftParts.get(1)));
    }
    return new NoteDbChangeState(id, changeMetaId, draftIds);
  }

  public static NoteDbChangeState applyDelta(Change change, Delta delta) {
    if (delta == null) {
      return null;
    }
    String oldStr = change.getNoteDbState();
    if (oldStr == null && !delta.newChangeMetaId().isPresent()) {
      // Neither an old nor a new meta ID was present, most likely because we
      // aren't writing a NoteDb graph at all for this change at this point. No
      // point in proceeding.
      return null;
    }
    NoteDbChangeState oldState = parse(change.getId(), oldStr);

    ObjectId changeMetaId;
    if (delta.newChangeMetaId().isPresent()) {
      changeMetaId = delta.newChangeMetaId().get();
      if (changeMetaId.equals(ObjectId.zeroId())) {
        change.setNoteDbState(null);
        return null;
      }
    } else {
      changeMetaId = oldState.changeMetaId;
    }

    Map<Account.Id, ObjectId> draftIds = new HashMap<>();
    if (oldState != null) {
      draftIds.putAll(oldState.draftIds);
    }
    for (Map.Entry<Account.Id, ObjectId> e : delta.newDraftIds().entrySet()) {
      if (e.getValue().equals(ObjectId.zeroId())) {
        draftIds.remove(e.getKey());
      } else {
        draftIds.put(e.getKey(), e.getValue());
      }
    }

    NoteDbChangeState state = new NoteDbChangeState(
        change.getId(), changeMetaId, draftIds);
    change.setNoteDbState(state.toString());
    return state;
  }

  public static String toString(ObjectId changeMetaId,
      Map<Account.Id, ObjectId> draftIds) {
    List<Account.Id> accountIds = Lists.newArrayList(draftIds.keySet());
    Collections.sort(accountIds, ReviewDbUtil.intKeyOrdering());
    StringBuilder sb = new StringBuilder(changeMetaId.name());
    for (Account.Id id : accountIds) {
      sb.append(',')
          .append(id.get())
          .append('=')
          .append(draftIds.get(id).name());
    }
    return sb.toString();
  }

  private final Change.Id changeId;
  private final ObjectId changeMetaId;
  private final ImmutableMap<Account.Id, ObjectId> draftIds;

  public NoteDbChangeState(Change.Id changeId, ObjectId changeMetaId,
      Map<Account.Id, ObjectId> draftIds) {
    this.changeId = checkNotNull(changeId);
    this.changeMetaId = checkNotNull(changeMetaId);
    this.draftIds = ImmutableMap.copyOf(draftIds);
  }

  public boolean isChangeUpToDate(Repository changeRepo) throws IOException {
    Ref ref = changeRepo.exactRef(ChangeNoteUtil.changeRefName(changeId));
    if (ref == null) {
      return changeMetaId.equals(ObjectId.zeroId());
    }
    return ref.getObjectId().equals(changeMetaId);
  }

  public boolean areDraftsUpToDate(Repository draftsRepo, Account.Id accountId)
      throws IOException {
    Ref ref = draftsRepo.exactRef(
        RefNames.refsDraftComments(accountId, changeId));
    if (ref == null) {
      return !draftIds.containsKey(accountId);
    }
    return ref.getObjectId().equals(draftIds.get(accountId));
  }

  @VisibleForTesting
  Change.Id getChangeId() {
    return changeId;
  }

  ObjectId getChangeMetaId() {
    return changeMetaId;
  }

  @VisibleForTesting
  ImmutableMap<Account.Id, ObjectId> getDraftIds() {
    return draftIds;
  }

  @Override
  public String toString() {
    return toString(changeMetaId, draftIds);
  }
}
