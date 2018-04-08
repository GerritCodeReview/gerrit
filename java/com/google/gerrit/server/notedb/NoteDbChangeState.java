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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.git.RefCache;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDbUtil;
import com.google.gwtorm.server.OrmRuntimeException;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
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
 *   <li>R,[meta-sha],[account1]=[drafts-sha],[account2]=[drafts-sha]...
 *   <li>R=[read-only-until],[meta-sha],[account1]=[drafts-sha],[account2]=[drafts-sha]...
 *   <li>N
 *   <li>N=[read-only-until]
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

    PrimaryStorage(char code) {
      this.code = code;
    }

    public static PrimaryStorage of(@Nullable Change c) {
      return of(NoteDbChangeState.parse(c));
    }

    public static PrimaryStorage of(@Nullable NoteDbChangeState s) {
      return s != null ? s.getPrimaryStorage() : REVIEW_DB;
    }
  }

  @AutoValue
  public abstract static class Delta {
    @VisibleForTesting
    public static Delta create(
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
          ImmutableMap.copyOf(Maps.filterValues(draftIds, id -> !ObjectId.zeroId().equals(id))));
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
        Optional<Account.Id> accountId = Account.Id.tryParse(draftParts.get(0));
        checkArgument(
            accountId.isPresent(),
            "invalid account ID in draft state part for change %s: %s",
            changeId,
            p);
        draftIds.put(accountId.get(), ObjectId.fromString(draftParts.get(1)));
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

  public static NoteDbChangeState parse(@Nullable Change c) {
    return c != null ? parse(c.getId(), c.getNoteDbState()) : null;
  }

  @VisibleForTesting
  public static NoteDbChangeState parse(Change.Id id, @Nullable String str) {
    if (Strings.isNullOrEmpty(str)) {
      // Return null rather than Optional as this is what goes in the field in
      // ReviewDb.
      return null;
    }
    List<String> parts = Splitter.on(',').splitToList(str);
    String first = parts.get(0);
    Optional<Timestamp> readOnlyUntil = parseReadOnlyUntil(id, str, first);

    // Only valid NOTE_DB state is "N".
    if (parts.size() == 1 && first.charAt(0) == NOTE_DB.code) {
      return new NoteDbChangeState(id, NOTE_DB, Optional.empty(), readOnlyUntil);
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
      return new NoteDbChangeState(id, REVIEW_DB, refState, readOnlyUntil);
    }
    throw invalidState(id, str);
  }

  private static Optional<Timestamp> parseReadOnlyUntil(
      Change.Id id, String fullStr, String first) {
    if (first.length() > 2 && first.charAt(1) == '=') {
      Long ts = Longs.tryParse(first.substring(2));
      if (ts == null) {
        throw invalidState(id, fullStr);
      }
      return Optional.of(new Timestamp(ts));
    }
    return Optional.empty();
  }

  private static IllegalArgumentException invalidState(Change.Id id, String str) {
    return new IllegalArgumentException("invalid state string for change " + id + ": " + str);
  }

  /**
   * Apply a delta to the state stored in a change entity.
   *
   * <p>This method does not check whether the old state was read-only; it is up to the caller to
   * not violate read-only semantics when storing the change back in ReviewDb.
   *
   * @param change change entity. The delta is applied against this entity's {@code noteDbState} and
   *     the new state is stored back in the entity as a side effect.
   * @param delta delta to apply.
   * @return new state, equivalent to what is stored in {@code change} as a side effect.
   */
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
            Optional.of(RefState.create(changeMetaId, draftIds)),
            // Copy old read-only deadline rather than advancing it; the caller is
            // still responsible for finishing the rest of its work before the lease
            // runs out.
            oldState != null ? oldState.getReadOnlyUntil() : Optional.empty());
    change.setNoteDbState(state.toString());
    return state;
  }

  // TODO(dborowitz): Ugly. Refactor these static methods into a Checker class
  // or something. They do not belong in NoteDbChangeState itself because:
  //  - need to inject Config but don't want a whole Factory
  //  - can't be methods on NoteDbChangeState because state is nullable (though
  //    we could also solve this by inventing an empty-but-non-null state)
  // Also we should clean up duplicated code between static/non-static methods.
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

  public static long getReadOnlySkew(Config cfg) {
    return cfg.getTimeUnit("notedb", null, "maxTimestampSkew", 1000, TimeUnit.MILLISECONDS);
  }

  static Timestamp timeForReadOnlyCheck(long skewMs) {
    // Subtract some slop in case the machine that set the change's read-only
    // lease has a clock behind ours.
    return new Timestamp(TimeUtil.nowMs() - skewMs);
  }

  public static void checkNotReadOnly(@Nullable Change change, long skewMs) {
    checkNotReadOnly(parse(change), skewMs);
  }

  public static void checkNotReadOnly(@Nullable NoteDbChangeState state, long skewMs) {
    if (state == null) {
      return; // No state means ReviewDb primary non-read-only.
    } else if (state.isReadOnly(timeForReadOnlyCheck(skewMs))) {
      throw new OrmRuntimeException(
          "change "
              + state.getChangeId()
              + " is read-only until "
              + state.getReadOnlyUntil().get());
    }
  }

  private final Change.Id changeId;
  private final PrimaryStorage primaryStorage;
  private final Optional<RefState> refState;
  private final Optional<Timestamp> readOnlyUntil;

  public NoteDbChangeState(
      Change.Id changeId,
      PrimaryStorage primaryStorage,
      Optional<RefState> refState,
      Optional<Timestamp> readOnlyUntil) {
    this.changeId = checkNotNull(changeId);
    this.primaryStorage = checkNotNull(primaryStorage);
    this.refState = checkNotNull(refState);
    this.readOnlyUntil = checkNotNull(readOnlyUntil);

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

  public boolean isReadOnly(Timestamp now) {
    return readOnlyUntil.isPresent() && now.before(readOnlyUntil.get());
  }

  public Optional<Timestamp> getReadOnlyUntil() {
    return readOnlyUntil;
  }

  public NoteDbChangeState withReadOnlyUntil(Timestamp ts) {
    return new NoteDbChangeState(changeId, primaryStorage, refState, Optional.of(ts));
  }

  public Change.Id getChangeId() {
    return changeId;
  }

  public ObjectId getChangeMetaId() {
    return refState().changeMetaId();
  }

  public ImmutableMap<Account.Id, ObjectId> getDraftIds() {
    return refState().draftIds();
  }

  public Optional<RefState> getRefState() {
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
        if (!readOnlyUntil.isPresent()) {
          // Don't include enum field, just IDs (though parse would accept it).
          return refState().toString();
        }
        return primaryStorage.code + "=" + readOnlyUntil.get().getTime() + "," + refState.get();
      case NOTE_DB:
        if (!readOnlyUntil.isPresent()) {
          return NOTE_DB_PRIMARY_STATE;
        }
        return primaryStorage.code + "=" + readOnlyUntil.get().getTime();
      default:
        throw new IllegalArgumentException("Unsupported PrimaryStorage: " + primaryStorage);
    }
  }

  @Override
  public int hashCode() {
    return Objects.hash(changeId, primaryStorage, refState, readOnlyUntil);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NoteDbChangeState)) {
      return false;
    }
    NoteDbChangeState s = (NoteDbChangeState) o;
    return changeId.equals(s.changeId)
        && primaryStorage.equals(s.primaryStorage)
        && refState.equals(s.refState)
        && readOnlyUntil.equals(s.readOnlyUntil);
  }
}
