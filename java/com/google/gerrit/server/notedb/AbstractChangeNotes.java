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

import static com.google.gerrit.server.notedb.NoteDbTable.CHANGES;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.metrics.Timer1;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotesCommit.ChangeNotesRevWalk;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** View of contents at a single ref related to some change. * */
public abstract class AbstractChangeNotes<T> {
  @VisibleForTesting
  @Singleton
  @UsedAt(UsedAt.Project.PLUGIN_CHECKS)
  public static class Args {
    // TODO(dborowitz): Some less smelly way of disabling NoteDb in tests.
    public final AtomicBoolean failOnLoadForTest;
    public final ChangeNoteJson changeNoteJson;
    public final GitRepositoryManager repoManager;
    public final AllUsersName allUsers;
    public final LegacyChangeNoteRead legacyChangeNoteRead;
    public final NoteDbMetrics metrics;

    // Providers required to avoid dependency cycles.

    // ChangeNoteCache -> Args
    public final Provider<ChangeNotesCache> cache;

    @Inject
    Args(
        GitRepositoryManager repoManager,
        AllUsersName allUsers,
        ChangeNoteJson changeNoteJson,
        LegacyChangeNoteRead legacyChangeNoteRead,
        NoteDbMetrics metrics,
        Provider<ChangeNotesCache> cache) {
      this.failOnLoadForTest = new AtomicBoolean();
      this.repoManager = repoManager;
      this.allUsers = allUsers;
      this.legacyChangeNoteRead = legacyChangeNoteRead;
      this.changeNoteJson = changeNoteJson;
      this.metrics = metrics;
      this.cache = cache;
    }
  }

  public static class LoadHandle implements AutoCloseable {
    private final Repository repo;
    private final ObjectId id;
    private ChangeNotesRevWalk rw;

    private LoadHandle(Repository repo, @Nullable ObjectId id) {
      this.repo = requireNonNull(repo);

      if (ObjectId.zeroId().equals(id)) {
        id = null;
      } else if (id != null) {
        id = id.copy();
      }
      this.id = id;
    }

    public ChangeNotesRevWalk walk() {
      if (rw == null) {
        rw = ChangeNotesCommit.newRevWalk(repo);
      }
      return rw;
    }

    @Nullable
    public ObjectId id() {
      return id;
    }

    @Override
    public void close() {
      if (rw != null) {
        rw.close();
      }
    }
  }

  protected final Args args;
  private final Change.Id changeId;

  private ObjectId revision;
  private boolean loaded;

  protected AbstractChangeNotes(Args args, Change.Id changeId) {
    this.args = requireNonNull(args);
    this.changeId = requireNonNull(changeId);
  }

  public Change.Id getChangeId() {
    return changeId;
  }

  /** @return revision of the metadata that was loaded. */
  public ObjectId getRevision() {
    return revision;
  }

  public T load() {
    if (loaded) {
      return self();
    }

    if (args.failOnLoadForTest.get()) {
      throw new StorageException("Reading from NoteDb is disabled");
    }
    try (Timer1.Context timer = args.metrics.readLatency.start(CHANGES);
        Repository repo = args.repoManager.openRepository(getProjectName());
        // Call openHandle even if reading is disabled, to trigger
        // auto-rebuilding before this object may get passed to a ChangeUpdate.
        LoadHandle handle = openHandle(repo)) {
      revision = handle.id();
      onLoad(handle);
      loaded = true;
    } catch (ConfigInvalidException | IOException e) {
      throw new StorageException(e);
    }
    return self();
  }

  protected ObjectId readRef(Repository repo) throws IOException {
    Ref ref = repo.getRefDatabase().exactRef(getRefName());
    return ref != null ? ref.getObjectId() : null;
  }

  /**
   * Open a handle for reading this entity from a repository.
   *
   * <p>Implementations may override this method to provide auto-rebuilding behavior.
   *
   * @param repo open repository.
   * @return handle for reading the entity.
   * @throws NoSuchChangeException change does not exist.
   * @throws IOException a repo-level error occurred.
   */
  protected LoadHandle openHandle(Repository repo) throws NoSuchChangeException, IOException {
    return openHandle(repo, readRef(repo));
  }

  protected LoadHandle openHandle(Repository repo, ObjectId id) {
    return new LoadHandle(repo, id);
  }

  public T reload() {
    loaded = false;
    return load();
  }

  public ObjectId loadRevision() {
    if (loaded) {
      return getRevision();
    }
    try (Repository repo = args.repoManager.openRepository(getProjectName())) {
      Ref ref = repo.getRefDatabase().exactRef(getRefName());
      return ref != null ? ref.getObjectId() : null;
    } catch (IOException e) {
      throw new StorageException(e);
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
  protected abstract void onLoad(LoadHandle handle)
      throws NoSuchChangeException, IOException, ConfigInvalidException;

  @SuppressWarnings("unchecked")
  protected final T self() {
    return (T) this;
  }
}
