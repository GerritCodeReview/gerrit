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

import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import java.util.Objects;

/**
 * Current low-level settings of the NoteDb migration for changes.
 *
 * <p>This class only describes the migration state of the {@link
 * com.google.gerrit.reviewdb.client.Change Change} entity group, since it is possible for a given
 * site to be in different states of the Change NoteDb migration process while staying at the same
 * ReviewDb schema version. It does <em>not</em> describe the migration state of non-Change tables;
 * those are automatically migrated using the ReviewDb schema migration process, so the NoteDb
 * migration state at a given ReviewDb schema cannot vary.
 *
 * <p>In many places, core Gerrit code should not directly care about the NoteDb migration state,
 * and should prefer high-level APIs like {@link com.google.gerrit.server.ApprovalsUtil
 * ApprovalsUtil} that don't require callers to inspect the migration state. The
 * <em>implementation</em> of those utilities does care about the state, and should query the {@code
 * NotesMigration} for the properties of the migration, for example, {@link #changePrimaryStorage()
 * where new changes should be stored}.
 *
 * <p>Core Gerrit code is mostly interested in one facet of the migration at a time (reading or
 * writing, say), but not all combinations of return values are supported or even make sense.
 *
 * <p>This class controls the state of the migration according to options in {@code gerrit.config}.
 * In general, any changes to these options should only be made by adventurous administrators, who
 * know what they're doing, on non-production data, for the purposes of testing the NoteDb
 * implementation. Changing options quite likely requires re-running {@code MigrateToNoteDb}. For
 * these reasons, the options remain undocumented.
 *
 * <p><strong>Note:</strong> Callers should not assume the values returned by {@code
 * NotesMigration}'s methods will not change in a running server.
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
   * <p>This method is awkwardly named because you should be using either {@link
   * #commitChangeWrites()} or {@link #failChangeWrites()} instead.
   *
   * <p>Updates to change data are written to NoteDb refs, but ReviewDb is still the source of
   * truth. Change data will not be written unless the NoteDb refs are already up to date, and the
   * write path will attempt to rebuild the change if not.
   *
   * <p>If false, the behavior when attempting to write depends on {@code readChanges()}. If {@code
   * readChanges() = false}, writes to NoteDb are simply ignored; if {@code true}, any attempts to
   * write will generate an error.
   */
  public abstract boolean rawWriteChangesSetting();

  /**
   * Read sequential change ID numbers from NoteDb.
   *
   * <p>If true, change IDs are read from {@code refs/sequences/changes} in All-Projects. If false,
   * change IDs are read from ReviewDb's native sequences.
   */
  public abstract boolean readChangeSequence();

  /** @return default primary storage for new changes. */
  public abstract PrimaryStorage changePrimaryStorage();

  /**
   * Disable ReviewDb access for changes.
   *
   * <p>When set, ReviewDb operations involving the Changes table become no-ops. Lookups return no
   * results; updates do nothing, as does opening, committing, or rolling back a transaction on the
   * Changes table.
   */
  public abstract boolean disableChangeReviewDb();

  /**
   * Fuse meta ref updates in the same batch as code updates.
   *
   * <p>When set, each {@link com.google.gerrit.server.update.BatchUpdate} results in a single
   * {@link org.eclipse.jgit.lib.BatchRefUpdate} to update both code and meta refs atomically.
   * Setting this option with a repository backend that does not support atomic multi-ref
   * transactions ({@link org.eclipse.jgit.lib.RefDatabase#performsAtomicTransactions()}) is a
   * configuration error, and all updates will fail at runtime.
   *
   * <p>Has no effect if {@link #disableChangeReviewDb()} is false.
   */
  public abstract boolean fuseUpdates();

  /**
   * Set the values returned by this instance to match another instance.
   *
   * <p>Optional operation: not all implementations support setting values after initialization.
   *
   * @param other other instance to copy values from.
   * @return this.
   */
  public NotesMigration setFrom(NotesMigration other) {
    throw new UnsupportedOperationException(getClass().getSimpleName() + " is read-only");
  }

  /**
   * Whether to fail when reading any data from NoteDb.
   *
   * <p>Used in conjunction with {@link #readChanges()} for tests.
   */
  public boolean failOnLoad() {
    return false;
  }

  public final boolean commitChangeWrites() {
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
    return rawWriteChangesSetting() || readChanges();
  }

  public final boolean failChangeWrites() {
    return !rawWriteChangesSetting() && readChanges();
  }

  public final boolean enabled() {
    return rawWriteChangesSetting() || readChanges();
  }

  @Override
  public final boolean equals(Object o) {
    if (!(o instanceof NotesMigration)) {
      return false;
    }
    NotesMigration m = (NotesMigration) o;
    return readChanges() == m.readChanges()
        && rawWriteChangesSetting() == m.rawWriteChangesSetting()
        && readChangeSequence() == m.readChangeSequence()
        && changePrimaryStorage() == m.changePrimaryStorage()
        && disableChangeReviewDb() == m.disableChangeReviewDb()
        && fuseUpdates() == m.fuseUpdates()
        && failOnLoad() == m.failOnLoad();
  }

  @Override
  public final int hashCode() {
    return Objects.hash(
        readChanges(),
        rawWriteChangesSetting(),
        readChangeSequence(),
        changePrimaryStorage(),
        disableChangeReviewDb(),
        fuseUpdates(),
        failOnLoad());
  }
}
