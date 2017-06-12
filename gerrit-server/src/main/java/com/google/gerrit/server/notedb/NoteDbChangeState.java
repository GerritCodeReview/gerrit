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
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;
import static com.google.gerrit.reviewdb.client.RefNames.refsDraftComments;
import static com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage.NOTE_DB;
import static com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage.REVIEW_DB;
import static org.eclipse.jgit.lib.ObjectId.zeroId;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gerrit.server.git.RefCache;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

/**
 * The state of all relevant NoteDb refs across all repos corresponding to a given Change entity.
 *
 * <p>Stored serialized in the {@code Change#noteDbState} field, and used to determine whether the
 * state in NoteDb is out of date.
 *
 * <p>Serialized in one of the forms:
 *
 * <ul>
 *   <li>[meta-sha],[account1]=[drafts-sha],[account2]=[drafts-sha]...
 *   <li>R[meta-sha],[account1]=[drafts-sha],[account2]=[drafts-sha]...
 *   <li>N
 * </ul>
 *
 * in numeric account ID order, with hex SHA-1s for human readability.
 */
public class NoteDbChangeState {
  public static final String NOTE_DB_PRIMARY_STATE = "N";

  public enum PrimaryStorage {
    REVIEW_DB('R'),
    NOTE_DB('N');

    private final char code;

    private PrimaryStorage(char code) {
      this.code = code;
    }

    public static PrimaryStorage of(Change c) {
      return of(NoteDbChangeState.parse(c));
    }

    public static PrimaryStorage of(NoteDbChangeState s) {
      return s != null ? s.getPrimaryStorage() : REVIEW_DB;
    }
  }

  @AutoValue
  public abstract static class Delta {
    static Delta create(
        Change.Id changeId,
        Optional<ObjectId> newChangeMetaId,
        Map<Account.Id, ObjectId> newDraftIds) {
      if (newDraftIds == null) {
        newDraftIds = ImmutableMap.of();
      }
      return new AutoValue_NoteDbChangeState_Delta(
          changeId, newChangeMetaId, ImmutableMap.copyOf(newDraftIds));
    }

    abstract Change.Id changeId();

    abstract Optional<ObjectId> newChangeMetaId();

    abstract ImmutableMap<Account.Id, ObjectId> newDraftIds();
  }

  @AutoValue
  public abstract static class RefState {
    @VisibleForTesting
    public static RefState create(ObjectId changeMetaId, Map<Account.Id, ObjectId> draftIds) {
      return new AutoValue_NoteDbChangeState_RefState(
          changeMetaId.copy(),
          ImmutableMap.copyOf(Maps.filterValues(draftIds, id -> !zeroId().equals(id))));
    }

    private static Optional<RefState> parse(Change.Id changeId, List<String> parts) {
      checkArgument(!parts.isEmpty(), "missing state string for change %s", changeId);
      ObjectId changeMetaId = ObjectId.fromString(parts.get(0));
      Map<Account.Id, ObjectId> draftIds = Maps.newHashMapWithExpectedSize(parts.size() - 1);
      Splitter s = Splitter.on('=');
      for (int i = 1; i < parts.size(); i++) {
        String p = parts.get(i);
        List<String> draftParts = s.splitToList(p);
        checkArgument(
            draftParts.size() == 2, "invalid draft state part for change %s: %s", changeId, p);
        draftIds.put(Account.Id.parse(draftParts.get(0)), ObjectId.fromString(draftParts.get(1)));
      }
      return Optional.of(create(changeMetaId, draftIds));
    }

    abstract ObjectId changeMetaId();

    abstract ImmutableMap<Account.Id, ObjectId> draftIds();

    @Override
    public String toString() {
      return appendTo(new StringBuilder()).toString();
    }

    StringBuilder appendTo(StringBuilder sb) {
      sb.append(changeMetaId().name());
      for (Account.Id id : ReviewDbUtil.intKeyOrdering().sortedCopy(draftIds().keySet())) {
        sb.append(',').append(id.get()).append('=').append(draftIds().get(id).name());
      }
      return sb;
    }
  }

  public static NoteDbChangeState parse(Change c) {
    return c != null ? parse(c.getId(), c.getNoteDbState()) : null;
  }

  @VisibleForTesting
  public static NoteDbChangeState parse(Change.Id id, String str) {
    if (Strings.isNullOrEmpty(str)) {
      // Return null rather than Optional as this is what goes in the field in
      // ReviewDb.
      return null;
    }
    List<String> parts = Splitter.on(',').splitToList(str);

    // Only valid NOTE_DB state is "N".
    String first = parts.get(0);
    if (parts.size() == 1 && first.charAt(0) == NOTE_DB.code) {
      return new NoteDbChangeState(id, NOTE_DB, Optional.empty());
    }

    // Otherwise it must be REVIEW_DB, either "R,<RefState>" or just
    // "<RefState>". Allow length > 0 for forward compatibility.
    if (first.length() > 0) {
      Optional<RefState> refState;
      if (first.charAt(0) == REVIEW_DB.code) {
        refState = RefState.parse(id, parts.subList(1, parts.size()));
      } else {
        refState = RefState.parse(id, parts);
      }
      return new NoteDbChangeState(id, REVIEW_DB, refState);
    }
    throw new IllegalArgumentException("invalid state string for change " + id + ": " + str);
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
    if (oldState != null && oldState.getPrimaryStorage() == NOTE_DB) {
      // NOTE_DB state doesn't include RefState, so applying a delta is a no-op.
      return oldState;
    }

    ObjectId changeMetaId;
    if (delta.newChangeMetaId().isPresent()) {
      changeMetaId = delta.newChangeMetaId().get();
      if (changeMetaId.equals(ObjectId.zeroId())) {
        change.setNoteDbState(null);
        return null;
      }
    } else {
      changeMetaId = oldState.getChangeMetaId();
    }

    Map<Account.Id, ObjectId> draftIds = new HashMap<>();
    if (oldState != null) {
      draftIds.putAll(oldState.getDraftIds());
    }
    for (Map.Entry<Account.Id, ObjectId> e : delta.newDraftIds().entrySet()) {
      if (e.getValue().equals(ObjectId.zeroId())) {
        draftIds.remove(e.getKey());
      } else {
        draftIds.put(e.getKey(), e.getValue());
      }
    }

    NoteDbChangeState state =
        new NoteDbChangeState(
            change.getId(),
            oldState != null ? oldState.getPrimaryStorage() : REVIEW_DB,
            Optional.of(RefState.create(changeMetaId, draftIds)));
    change.setNoteDbState(state.toString());
    return state;
  }

  public static boolean isChangeUpToDate(
      @Nullable NoteDbChangeState state, RefCache changeRepoRefs, Change.Id changeId)
      throws IOException {
    if (PrimaryStorage.of(state) == NOTE_DB) {
      return true; // Primary storage is NoteDb, up to date by definition.
    }
    if (state == null) {
      return !changeRepoRefs.get(changeMetaRef(changeId)).isPresent();
    }
    return state.isChangeUpToDate(changeRepoRefs);
  }

  public static boolean areDraftsUpToDate(
      @Nullable NoteDbChangeState state,
      RefCache draftsRepoRefs,
      Change.Id changeId,
      Account.Id accountId)
      throws IOException {
    if (PrimaryStorage.of(state) == NOTE_DB) {
      return true; // Primary storage is NoteDb, up to date by definition.
    }
    if (state == null) {
      return !draftsRepoRefs.get(refsDraftComments(changeId, accountId)).isPresent();
    }
    return state.areDraftsUpToDate(draftsRepoRefs, accountId);
  }

  private final Change.Id changeId;
  private final PrimaryStorage primaryStorage;
  private final Optional<RefState> refState;

  public NoteDbChangeState(
      Change.Id changeId, PrimaryStorage primaryStorage, Optional<RefState> refState) {
    this.changeId = checkNotNull(changeId);
    this.primaryStorage = checkNotNull(primaryStorage);
    this.refState = refState;

    switch (primaryStorage) {
      case REVIEW_DB:
        checkArgument(
            refState.isPresent(),
            "expected RefState for change %s with primary storage %s",
            changeId,
            primaryStorage);
        break;
      case NOTE_DB:
        checkArgument(
            !refState.isPresent(),
            "expected no RefState for change %s with primary storage %s",
            changeId,
            primaryStorage);
        break;
      default:
        throw new IllegalStateException("invalid PrimaryStorage: " + primaryStorage);
    }
  }

  public PrimaryStorage getPrimaryStorage() {
    return primaryStorage;
  }

  public boolean isChangeUpToDate(RefCache changeRepoRefs) throws IOException {
    if (primaryStorage == NOTE_DB) {
      return true; // Primary storage is NoteDb, up to date by definition.
    }
    Optional<ObjectId> id = changeRepoRefs.get(changeMetaRef(changeId));
    if (!id.isPresent()) {
      return getChangeMetaId().equals(ObjectId.zeroId());
    }
    return id.get().equals(getChangeMetaId());
  }

  public boolean areDraftsUpToDate(RefCache draftsRepoRefs, Account.Id accountId)
      throws IOException {
    if (primaryStorage == NOTE_DB) {
      return true; // Primary storage is NoteDb, up to date by definition.
    }
    Optional<ObjectId> id = draftsRepoRefs.get(refsDraftComments(changeId, accountId));
    if (!id.isPresent()) {
      return !getDraftIds().containsKey(accountId);
    }
    return id.get().equals(getDraftIds().get(accountId));
  }

  public boolean isUpToDate(RefCache changeRepoRefs, RefCache draftsRepoRefs) throws IOException {
    if (primaryStorage == NOTE_DB) {
      return true; // Primary storage is NoteDb, up to date by definition.
    }
    if (!isChangeUpToDate(changeRepoRefs)) {
      return false;
    }
    for (Account.Id accountId : getDraftIds().keySet()) {
      if (!areDraftsUpToDate(draftsRepoRefs, accountId)) {
        return false;
      }
    }
    return true;
  }

  @VisibleForTesting
  Change.Id getChangeId() {
    return changeId;
  }

  @VisibleForTesting
  public ObjectId getChangeMetaId() {
    return refState().changeMetaId();
  }

  @VisibleForTesting
  ImmutableMap<Account.Id, ObjectId> getDraftIds() {
    return refState().draftIds();
  }

  @VisibleForTesting
  Optional<RefState> getRefState() {
    return refState;
  }

  private RefState refState() {
    checkState(refState.isPresent(), "state for %s has no RefState: %s", changeId, this);
    return refState.get();
  }

  @Override
  public String toString() {
    switch (primaryStorage) {
      case REVIEW_DB:
        // Don't include enum field, just IDs (though parse would accept it).
        return refState().toString();
      case NOTE_DB:
        return NOTE_DB_PRIMARY_STATE;
      default:
        throw new IllegalArgumentException("Unsupported PrimaryStorage: " + primaryStorage);
    }
  }
}
