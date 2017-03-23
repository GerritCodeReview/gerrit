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

import com.google.common.collect.Maps;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;

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

  public Optional<ObjectId> getRef(String name) throws IOException {
    return getCommands().get(name);
  }

  // TODO(dborowitz): Document the crazy consistency semantics.
  public Map<String, ObjectId> getRefs(String prefix) throws IOException {
    Map<String, ObjectId> result =
        new HashMap<>(
            Maps.transformValues(repo.getRefDatabase().getRefs(prefix), Ref::getObjectId));

    // Only update actually modified refs; don't take the chance of incurring lots of random reads
    // via commands.get on the off chance that we might see some cached values.
    for (ReceiveCommand cmd : commands.getCommands().values()) {
      if (!cmd.getRefName().startsWith(prefix)) {
        continue;
      }
      String suffix = cmd.getRefName().substring(prefix.length());
      if (cmd.getNewId().equals(ObjectId.zeroId())) {
        result.remove(suffix);
      } else {
        result.put(suffix, cmd.getNewId());
      }
    }

    return result;
  }

  @Override
  public void close() {
    if (closeRepo) {
      inserter.close();
      rw.close();
      repo.close();
    }
  }

  Repository getRepository() {
    return repo;
  }

  ObjectInserter getInserter() {
    return inserter;
  }

  ChainedReceiveCommands getCommands() {
    return commands;
  }
}
