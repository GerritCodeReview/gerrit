// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.notedb.NoteDbTable.CHANGES;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilder;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** View of contents at a single ref related to some change. * */
public abstract class AbstractChangeNotes<T> {
  @VisibleForTesting
  @Singleton
  public static class Args {
    final GitRepositoryManager repoManager;
    final NotesMigration migration;
    final AllUsersName allUsers;
    final ChangeNoteUtil noteUtil;
    final NoteDbMetrics metrics;
    final Provider<ReviewDb> db;

    // Providers required to avoid dependency cycles.

    // ChangeRebuilder -> ChangeNotes.Factory -> Args
    final Provider<ChangeRebuilder> rebuilder;

    // ChangeNoteCache -> Args
    final Provider<ChangeNotesCache> cache;

    @Inject
    Args(
        GitRepositoryManager repoManager,
        NotesMigration migration,
        AllUsersName allUsers,
        ChangeNoteUtil noteUtil,
        NoteDbMetrics metrics,
        Provider<ReviewDb> db,
        Provider<ChangeRebuilder> rebuilder,
        Provider<ChangeNotesCache> cache) {
      this.repoManager = repoManager;
      this.migration = migration;
      this.allUsers = allUsers;
      this.noteUtil = noteUtil;
      this.metrics = metrics;
      this.db = db;
      this.rebuilder = rebuilder;
      this.cache = cache;
    }
  }

  @AutoValue
  public abstract static class LoadHandle implements AutoCloseable {
    public static LoadHandle create(ChangeNotesRevWalk walk, ObjectId id) {
      if (ObjectId.zeroId().equals(id)) {
        id = null;
      } else if (id != null) {
        id = id.copy();
      }
      return new AutoValue_AbstractChangeNotes_LoadHandle(checkNotNull(walk), id);
    }

    public static LoadHandle missing() {
      return new AutoValue_AbstractChangeNotes_LoadHandle(null, null);
    }

    @Nullable
    public abstract ChangeNotesRevWalk walk();

    @Nullable
    public abstract ObjectId id();

    @Override
    public void close() {
      if (walk() != null) {
        walk().close();
      }
    }
  }

  protected final Args args;
  protected final PrimaryStorage primaryStorage;
  protected final boolean autoRebuild;
  private final Change.Id changeId;

  private ObjectId revision;
  private boolean loaded;

  AbstractChangeNotes(
      Args args, Change.Id changeId, @Nullable PrimaryStorage primaryStorage, boolean autoRebuild) {
    this.args = checkNotNull(args);
    this.changeId = checkNotNull(changeId);
    this.primaryStorage = primaryStorage;
    this.autoRebuild = primaryStorage == PrimaryStorage.REVIEW_DB && autoRebuild;
  }

  public Change.Id getChangeId() {
    return changeId;
  }

  /** @return revision of the metadata that was loaded. */
  public ObjectId getRevision() {
    return revision;
  }

  public T load() throws OrmException {
    if (loaded) {
      return self();
    }
    boolean read = args.migration.readChanges();
    if (!read && primaryStorage == PrimaryStorage.NOTE_DB) {
      throw new OrmException("NoteDb is required to read change " + changeId);
    }
    boolean readOrWrite = read || args.migration.writeChanges();
    if (!readOrWrite && !autoRebuild) {
      loadDefaults();
      return self();
    }
    if (args.migration.failOnLoad()) {
      throw new OrmException("Reading from NoteDb is disabled");
    }
    try (Timer1.Context timer = args.metrics.readLatency.start(CHANGES);
        Repository repo = args.repoManager.openRepository(getProjectName());
        // Call openHandle even if reading is disabled, to trigger
        // auto-rebuilding before this object may get passed to a ChangeUpdate.
        LoadHandle handle = openHandle(repo)) {
      if (read) {
        revision = handle.id();
        onLoad(handle);
      } else {
        loadDefaults();
      }
      loaded = true;
    } catch (ConfigInvalidException | IOException e) {
      throw new OrmException(e);
    }
    return self();
  }

  protected ObjectId readRef(Repository repo) throws IOException {
    Ref ref = repo.getRefDatabase().exactRef(getRefName());
    return ref != null ? ref.getObjectId() : null;
  }

  protected LoadHandle openHandle(Repository repo) throws IOException {
    return openHandle(repo, readRef(repo));
  }

  protected LoadHandle openHandle(Repository repo, ObjectId id) {
    return LoadHandle.create(ChangeNotesCommit.newRevWalk(repo), id);
  }

  public T reload() throws OrmException {
    loaded = false;
    return load();
  }

  public ObjectId loadRevision() throws OrmException {
    if (loaded) {
      return getRevision();
    } else if (!args.migration.enabled()) {
      return null;
    }
    try (Repository repo = args.repoManager.openRepository(getProjectName())) {
      Ref ref = repo.getRefDatabase().exactRef(getRefName());
      return ref != null ? ref.getObjectId() : null;
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  /** Load default values for any instance variables when NoteDb is disabled. */
  protected abstract void loadDefaults();

  /**
   * @return the NameKey for the project where the notes should be stored, which is not necessarily
   *     the same as the change's project.
   */
  public abstract Project.NameKey getProjectName();

  /** @return name of the reference storing this configuration. */
  protected abstract String getRefName();

  /** Set up the metadata, parsing any state from the loaded revision. */
  protected abstract void onLoad(LoadHandle handle) throws IOException, ConfigInvalidException;

  @SuppressWarnings("unchecked")
  protected final T self() {
    return (T) this;
  }
}
