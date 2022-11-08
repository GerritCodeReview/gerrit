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

import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritImportedServerIds;
import com.google.gerrit.server.config.GerritServerId;
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
    public final NoteDbMetrics metrics;
    public final String serverId;
    public final ImmutableList<String> importedServerIds;

    // Providers required to avoid dependency cycles.

    // ChangeNoteCache -> Args
    public final Provider<ChangeNotesCache> cache;

    @Inject
    Args(
        GitRepositoryManager repoManager,
        AllUsersName allUsers,
        ChangeNoteJson changeNoteJson,
        NoteDbMetrics metrics,
        Provider<ChangeNotesCache> cache,
        @GerritServerId String serverId,
        @GerritImportedServerIds ImmutableList<String> importedServerIds) {
      this.failOnLoadForTest = new AtomicBoolean();
      this.repoManager = repoManager;
      this.allUsers = allUsers;
      this.changeNoteJson = changeNoteJson;
      this.metrics = metrics;
      this.cache = cache;
      this.serverId = serverId;
      this.importedServerIds = importedServerIds;
    }
  }

  /** An {@link AutoCloseable} for parsing a single commit into ChangeNotesCommits. */
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

  protected AbstractChangeNotes(Args args, Change.Id changeId, @Nullable ObjectId metaSha1) {
    this.args = requireNonNull(args);
    this.changeId = requireNonNull(changeId);
    this.revision = metaSha1;
  }

  protected AbstractChangeNotes(Args args, Change.Id changeId) {
    this(args, changeId, null);
  }

  public Change.Id getChangeId() {
    return changeId;
  }

  /** Returns revision of the metadata that was loaded. */
  public ObjectId getRevision() {
    return revision;
  }

  public T load() {
    try (Repository repo = args.repoManager.openRepository(getProjectName())) {
      load(repo);
      return self();
    } catch (IOException e) {
      throw new StorageException(e);
    }
  }

  public T load(Repository repo) {
    if (loaded) {
      return self();
    }

    if (args.failOnLoadForTest.get()) {
      throw new StorageException("Reading from NoteDb is disabled");
    }
    try (Timer0.Context timer = args.metrics.readLatency.start();
        // Call openHandle even if reading is disabled, to trigger
        // auto-rebuilding before this object may get passed to a ChangeUpdate.
        LoadHandle handle = openHandle(repo, revision)) {
      revision = handle.id();
      onLoad(handle);
      loaded = true;
    } catch (ConfigInvalidException | IOException e) {
      throw new StorageException(e);
    }
    return self();
  }

  @Nullable
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
   * @param id SHA1 of the entity to read from the repository. The SHA1 is not sanity checked and is
   *     assumed to be valid. If null, lookup SHA1 from the /meta ref.
   * @return handle for reading the entity.
   * @throws NoSuchChangeException change does not exist.
   * @throws IOException a repo-level error occurred.
   */
  protected LoadHandle openHandle(Repository repo, @Nullable ObjectId id)
      throws NoSuchChangeException, IOException {
    if (id == null) {
      id = readRef(repo);
    }

    return new LoadHandle(repo, id);
  }

  public T reload() {
    loaded = false;
    return load();
  }

  @Nullable
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
   * Returns the NameKey for the project where the notes should be stored, which is not necessarily
   * the same as the change's project.
   */
  public abstract Project.NameKey getProjectName();

  /** Returns name of the reference storing this configuration. */
  protected abstract String getRefName();

  /** Set up the metadata, parsing any state from the loaded revision. */
  protected abstract void onLoad(LoadHandle handle)
      throws NoSuchChangeException, IOException, ConfigInvalidException;

  @SuppressWarnings("unchecked")
  protected final T self() {
    return (T) this;
  }
}
