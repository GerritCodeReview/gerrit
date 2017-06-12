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

package com.google.gerrit.server.git;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Collection of {@link ReceiveCommand}s that supports multiple updates per ref.
 *
 * <p>The underlying behavior of {@link BatchRefUpdate} is undefined (an implementations vary) when
 * more than one command per ref is added. This class works around that limitation by allowing
 * multiple updates per ref, as long as the previous new SHA-1 matches the next old SHA-1.
 */
public class ChainedReceiveCommands implements RefCache {
  private final Map<String, ReceiveCommand> commands = new LinkedHashMap<>();
  private final RepoRefCache refCache;

  public ChainedReceiveCommands(Repository repo) {
    this(new RepoRefCache(repo));
  }

  public ChainedReceiveCommands(RepoRefCache refCache) {
    this.refCache = checkNotNull(refCache);
  }

  public RepoRefCache getRepoRefCache() {
    return refCache;
  }

  public boolean isEmpty() {
    return commands.isEmpty();
  }

  /**
   * Add a command.
   *
   * @param cmd command to add. If a command has been previously added for the same ref, the new
   *     SHA-1 of the most recent previous command must match the old SHA-1 of this command.
   */
  public void add(ReceiveCommand cmd) {
    checkArgument(!cmd.getOldId().equals(cmd.getNewId()), "ref update is a no-op: %s", cmd);
    ReceiveCommand old = commands.get(cmd.getRefName());
    if (old == null) {
      commands.put(cmd.getRefName(), cmd);
      return;
    }
    checkArgument(
        old.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED,
        "cannot chain ref update %s after update %s with result %s",
        cmd,
        old,
        old.getResult());
    checkArgument(
        cmd.getOldId().equals(old.getNewId()),
        "cannot chain ref update %s after update %s with different new ID",
        cmd,
        old);
    commands.put(
        cmd.getRefName(), new ReceiveCommand(old.getOldId(), cmd.getNewId(), cmd.getRefName()));
  }

  /**
   * Get the latest value of a ref according to this sequence of commands.
   *
   * <p>After the value for a ref is read from the repo once, it is cached as in {@link
   * RepoRefCache}.
   *
   * @see RefCache#get(String)
   */
  @Override
  public Optional<ObjectId> get(String refName) throws IOException {
    ReceiveCommand cmd = commands.get(refName);
    if (cmd != null) {
      return !cmd.getNewId().equals(ObjectId.zeroId())
          ? Optional.of(cmd.getNewId())
          : Optional.empty();
    }
    return refCache.get(refName);
  }

  /**
   * Add commands from this instance to a native JGit batch update.
   *
   * <p>Exactly one command per ref will be added to the update. The old SHA-1 will be the old SHA-1
   * of the first command added to this instance for that ref; the new SHA-1 will be the new SHA-1
   * of the last command.
   *
   * @param bru batch update
   */
  public void addTo(BatchRefUpdate bru) {
    for (ReceiveCommand cmd : commands.values()) {
      bru.addCommand(cmd);
    }
  }

  /** @return an unmodifiable view of commands. */
  public Map<String, ReceiveCommand> getCommands() {
    return Collections.unmodifiableMap(commands);
  }
}
