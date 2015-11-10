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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.VersionedMetaData;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

/** View of contents at a single ref related to some change. **/
public abstract class AbstractChangeNotes<T> extends VersionedMetaData {
  protected final GitRepositoryManager repoManager;
  protected final NotesMigration migration;
  private final Change.Id changeId;

  private boolean loaded;

  AbstractChangeNotes(GitRepositoryManager repoManager,
      NotesMigration migration, Change.Id changeId) {
    this.repoManager = repoManager;
    this.migration = migration;
    this.changeId = changeId;
  }

  public Change.Id getChangeId() {
    return changeId;
  }

  public T load() throws OrmException {
    if (loaded) {
      return self();
    }
    if (!migration.enabled()) {
      loadDefaults();
      return self();
    }
    try (Repository repo = repoManager.openMetadataRepository(getProjectName())) {
      load(repo);
      loaded = true;
    } catch (ConfigInvalidException | IOException e) {
      throw new OrmException(e);
    }
    return self();
  }

  public T reload() throws OrmException {
    loaded = false;
    return load();
  }

  public ObjectId loadRevision() throws OrmException {
    if (loaded) {
      return getRevision();
    } else if (!migration.enabled()) {
      return null;
    }
    try (Repository repo = repoManager.openMetadataRepository(getProjectName())) {
      Ref ref = repo.getRefDatabase().exactRef(getRefName());
      return ref != null ? ref.getObjectId() : null;
    } catch (IOException e) {
      throw new OrmException(e);
    }
  }

  /** Load default values for any instance variables when notedb is disabled. */
  protected abstract void loadDefaults();

  /**
   * @return the NameKey for the project where the notes should be stored,
   *    which is not necessarily the same as the change's project.
   */
  protected abstract Project.NameKey getProjectName();

  @SuppressWarnings("unchecked")
  protected final T self() {
    return (T) this;
  }
}
