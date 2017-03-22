// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.update;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.io.IOException;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

public class RepoView implements AutoCloseable {
  private final Repository repo;
  private final RevWalk rw;
  private final ObjectInserter inserter;
  private final ChainedReceiveCommands commands;
  private final boolean closeRepo;

  RepoView(GitRepositoryManager repoManager, Project.NameKey project) throws IOException {
    repo = repoManager.openRepository(project);
    inserter = repo.newObjectInserter();
    rw = new RevWalk(inserter.newReader());
    commands = new ChainedReceiveCommands(repo);
    closeRepo = true;
  }

  RepoView(Repository repo, RevWalk rw, ObjectInserter inserter) {
    checkArgument(
        rw.getObjectReader().getCreatedFromInserter() == inserter,
        "expected RevWalk %s to be created by ObjectInserter %s",
        rw,
        inserter);
    this.repo = checkNotNull(repo);
    this.rw = checkNotNull(rw);
    this.inserter = checkNotNull(inserter);
    commands = new ChainedReceiveCommands(repo);
    closeRepo = false;
  }

  public RevWalk getRevWalk() {
    return rw;
  }

  public ObjectInserter getInserter() {
    return inserter;
  }

  public ChainedReceiveCommands getCommands() {
    return commands;
  }

  public Optional<ObjectId> getRef(String name) throws IOException {
    return getCommands().get(name);
  }

  // TODO(dborowitz): Remove this so callers can't do arbitrary stuff.
  Repository getRepository() {
    return repo;
  }

  @Override
  public void close() {
    if (closeRepo) {
      inserter.close();
      rw.close();
      repo.close();
    }
  }
}
