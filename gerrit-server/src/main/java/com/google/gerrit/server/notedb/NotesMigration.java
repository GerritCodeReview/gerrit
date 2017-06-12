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
 *
 * <p>The migration will proceed one root entity type at a time. A <em>root entity</em> is an entity
 * stored in ReviewDb whose key's {@code getParentKey()} method returns null. For an example of the
 * entity hierarchy rooted at Change, see the diagram in {@code
 * com.google.gerrit.reviewdb.client.Change}.
 *
 * <p>During a transitional period, each root entity group from ReviewDb may be either <em>written
 * to</em> or <em>both written to and read from</em> NoteDb.
 *
 * <p>This class controls the state of the migration according to options in {@code gerrit.config}.
 * In general, any changes to these options should only be made by adventurous administrators, who
 * know what they're doing, on non-production data, for the purposes of testing the NoteDb
 * implementation. Changing options quite likely requires re-running {@code RebuildNoteDb}. For
 * these reasons, the options remain undocumented.
 */
public abstract class NotesMigration {
  /**
   * Read changes from NoteDb.
   *
   * <p>Change data is read from NoteDb refs, but ReviewDb is still the source of truth. If the
   * loader determines NoteDb is out of date, the change data in NoteDb will be transparently
   * rebuilt. This means that some code paths that look read-only may in fact attempt to write.
   *
   * <p>If true and {@code writeChanges() = false}, changes can still be read from NoteDb, but any
   * attempts to write will generate an error.
   */
  public abstract boolean readChanges();

  /**
   * Write changes to NoteDb.
   *
   * <p>Updates to change data are written to NoteDb refs, but ReviewDb is still the source of
   * truth. Change data will not be written unless the NoteDb refs are already up to date, and the
   * write path will attempt to rebuild the change if not.
   *
   * <p>If false, the behavior when attempting to write depends on {@code readChanges()}. If {@code
   * readChanges() = false}, writes to NoteDb are simply ignored; if {@code true}, any attempts to
   * write will generate an error.
   */
  protected abstract boolean writeChanges();

  /**
   * Read sequential change ID numbers from NoteDb.
   *
   * <p>If true, change IDs are read from {@code refs/sequences/changes} in All-Projects. If false,
   * change IDs are read from ReviewDb's native sequences.
   */
  public abstract boolean readChangeSequence();

  public abstract boolean readAccounts();

  public abstract boolean writeAccounts();

  /**
   * Whether to fail when reading any data from NoteDb.
   *
   * <p>Used in conjunction with {@link #readChanges()} for tests.
   */
  public boolean failOnLoad() {
    return false;
  }

  public boolean commitChangeWrites() {
    // It may seem odd that readChanges() without writeChanges() means we should
    // attempt to commit writes. However, this method is used by callers to know
    // whether or not they should short-circuit and skip attempting to read or
    // write NoteDb refs.
    //
    // It is possible for commitChangeWrites() to return true and
    // failChangeWrites() to also return true, causing an error later in the
    // same codepath. This specific condition is used by the auto-rebuilding
    // path to rebuild a change and stage the results, but not commit them due
    // to failChangeWrites().
    return writeChanges() || readChanges();
  }

  public boolean failChangeWrites() {
    return !writeChanges() && readChanges();
  }

  public boolean enabled() {
    return writeChanges() || readChanges() || writeAccounts() || readAccounts();
  }
}
