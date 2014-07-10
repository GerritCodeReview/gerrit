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
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

/** View of contents at a single ref related to some change. **/
public abstract class AbstractChangeNotes<T> extends VersionedMetaData {
  private boolean loaded;
  protected final GitRepositoryManager repoManager;
  private final Change change;

  AbstractChangeNotes(GitRepositoryManager repoManager, Change change) {
    this.repoManager = repoManager;
    this.change = new Change(change);
  }

  public Change.Id getChangeId() {
    return change.getId();
  }

  public Change getChange() {
    return change;
  }

  public T load() throws OrmException {
    if (!loaded) {
      Repository repo;
      try {
        repo = repoManager.openRepository(getProjectName());
      } catch (IOException e) {
        throw new OrmException(e);
      }
      try {
        load(repo);
        loaded = true;
      } catch (ConfigInvalidException | IOException e) {
        throw new OrmException(e);
      } finally {
        repo.close();
      }
    }
    return self();
  }

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
