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

/**
 * Holds the current state of the NoteDb migration.
 * <p>
 * The migration will proceed one root entity type at a time. A <em>root
 * entity</em> is an entity stored in ReviewDb whose key's
 * {@code getParentKey()} method returns null. For an example of the entity
 * hierarchy rooted at Change, see the diagram in
 * {@code com.google.gerrit.reviewdb.client.Change}.
 * <p>
 * During a transitional period, each root entity group from ReviewDb may be
 * either <em>written to</em> or <em>both written to and read from</em> NoteDb.
 * <p>
 * This class controls the state of the migration according to options in
 * {@code gerrit.config}. In general, any changes to these options should only
 * be made by adventurous administrators, who know what they're doing, on
 * non-production data, for the purposes of testing the NoteDb implementation.
 * Changing options quite likely requires re-running {@code RebuildNoteDb}. For
 * these reasons, the options remain undocumented.
 */
public abstract class NotesMigration {
  /**
   * Read changes from NoteDb.
   * <p>
   * Change data is read from NoteDb refs, but ReviewDb is still the source of
   * truth. If the loader determines NoteDb is out of date, the change data in
   * NoteDb will be transparently rebuilt. This means that some code paths that
   * look read-only may in fact attempt to write.
   * <p>
   * Requires {@code writeChanges() = true}. If false, change data is read from
   * ReviewDb.
   */
  public abstract boolean readChanges();

  /**
   * Write changes to NoteDb.
   * <p>
   * Updates to change data are written to NoteDb refs, but ReviewDb is still
   * the source of truth. Change data will not be written unless the NoteDb refs
   * are already up to date, and the write path will attempt to rebuild the
   * change if not.
   * <p>
   * If false, writes to NoteDb are not attempted.
   */
  public abstract boolean writeChanges();

  /**
   * Read changes from NoteDb, and fail when attempting to write.
   * <p>
   * For use by batch or offline programs that read change data from NoteDb, but
   * should not write to the storage. If NoteDb is out of date, changes are
   * rebuilt in order to return their information to callers, but the results
   * are not saved to the storage.
   * <p>
   * Explicit writes of NoteDb change data will fail with an error; contrast
   * with {@code writeChanges() = false}, which silently skips writing.
   * <p>
   * Requires {@code readChanges() = true} and {@code writeChanges() = true}.
   */
  public abstract boolean readOnlyChanges();

  public abstract boolean readAccounts();

  public abstract boolean writeAccounts();

  /**
   * Whether to fail when reading any data from NoteDb.
   * <p>
   * Used in conjunction with {@link #readChanges()} for tests.
   */
  public boolean failOnLoad() {
    return false;
  }

  public boolean enabled() {
    return writeChanges() || readChanges()
        || writeAccounts() || readAccounts();
  }
}
