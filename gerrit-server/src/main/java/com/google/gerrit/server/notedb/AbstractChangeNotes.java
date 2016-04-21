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
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;

/** View of contents at a single ref related to some change. **/
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

    // Must be a Provider due to dependency cycle between ChangeRebuilder and
    // Args via ChangeNotes.Factory.
    final Provider<ChangeRebuilder> rebuilder;

    @Inject
    Args(
        GitRepositoryManager repoManager,
        NotesMigration migration,
        AllUsersName allUsers,
        ChangeNoteUtil noteUtil,
        NoteDbMetrics metrics,
        Provider<ReviewDb> db,
        Provider<ChangeRebuilder> rebuilder) {
      this.repoManager = repoManager;
      this.migration = migration;
      this.allUsers = allUsers;
      this.noteUtil = noteUtil;
      this.metrics = metrics;
      this.db = db;
      this.rebuilder = rebuilder;
    }
  }

  @AutoValue
  public abstract static class LoadHandle implements AutoCloseable {
    public static LoadHandle create(RevWalk walk, ObjectId id) {
      return new AutoValue_AbstractChangeNotes_LoadHandle(
          checkNotNull(walk), id != null ? id.copy() : null);
    }

    public static LoadHandle missing() {
      return new AutoValue_AbstractChangeNotes_LoadHandle(null, null);
    }

    @Nullable public abstract RevWalk walk();
    @Nullable public abstract ObjectId id();

    @Override
    public void close() {
      if (walk() != null) {
        walk().close();
      }
    }
  }

  protected final Args args;
  private final Change.Id changeId;

  private ObjectId revision;
  private boolean loaded;

  AbstractChangeNotes(Args args, Change.Id changeId) {
    this.args = args;
    this.changeId = changeId;
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
    if (!args.migration.readChanges() || changeId == null) {
      loadDefaults();
      return self();
    }
    try (Timer1.Context timer = args.metrics.readLatency.start(CHANGES);
        Repository repo = args.repoManager.openRepository(getProjectName());
        LoadHandle handle = openHandle(repo)) {
      revision = handle.id();
      onLoad(handle);
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
    return LoadHandle.create(new RevWalk(repo), readRef(repo));
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
   * @return the NameKey for the project where the notes should be stored,
   *    which is not necessarily the same as the change's project.
   */
  public abstract Project.NameKey getProjectName();

  /** @return name of the reference storing this configuration. */
  protected abstract String getRefName();

  /** Set up the metadata, parsing any state from the loaded revision. */
  protected abstract void onLoad(LoadHandle handle)
      throws IOException, ConfigInvalidException;

  @SuppressWarnings("unchecked")
  protected final T self() {
    return (T) this;
  }
}
