// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.project.renaming;

import com.google.gerrit.common.errors.ProjectRenamingFailedException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenameRepositoryTask implements Task {
  private static final Logger log = LoggerFactory
      .getLogger(RenameRepositoryTask.class);

  private final GitRepositoryManager repoManager;

  private final Project.NameKey source;
  private final Project.NameKey destination;

  public interface Factory extends Task.Factory {
    RenameRepositoryTask create(@Assisted("source") Project.NameKey source,
        @Assisted("destination") Project.NameKey destination);
  }

  @Inject
  public RenameRepositoryTask(GitRepositoryManager repoManager,
      @Assisted("source") Project.NameKey source,
      @Assisted("destination") Project.NameKey destination) {
    this.repoManager = repoManager;
    this.source = source;
    this.destination = destination;
  }

  @Override
  public void carryOut() throws ProjectRenamingFailedException {
    try {
      repoManager.renameRepository(source, destination);
    } catch (RepositoryNotFoundException e) {
      throw new ProjectRenamingFailedException("Could not find repository for "
          + source, e);
    }
  }

  @Override
  public void rollback() {
    try {
      repoManager.renameRepository(destination, source);
    } catch (Throwable e) {
      log.error("Could not roll back renaming repository " + source + ". "
          + "Please move project " + destination + " to " + source + ".", e);
    }
  }
}
