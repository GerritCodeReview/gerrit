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
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Restricted view of a {@link Repository} for use by {@link BatchUpdateOp} implementations.
 *
 * <p>This class serves two purposes in the context of {@link BatchUpdate}. First, the subset of
 * normal Repository functionality is purely read-only, which prevents implementors from modifying
 * the repository outside of {@link BatchUpdateOp#updateRepo}. Write operations can only be
 * performed by calling methods on {@link RepoContext}.
 *
 * <p>Second, the read methods take into account any pending operations on the repository that
 * implementations have staged using the write methods on {@link RepoContext}. Callers do not have
 * to worry about whether operations have been performed yet.
 */
public class RepoView {
  private final Repository repo;
  private final RevWalk rw;
  private final ObjectInserter inserter;
  private final ObjectInserter inserterWrapper;
  private final ChainedReceiveCommands commands;
  private final boolean closeRepo;

  RepoView(GitRepositoryManager repoManager, Project.NameKey project) throws IOException {
    repo = repoManager.openRepository(project);
    inserter = repo.newObjectInserter();
    inserterWrapper = new NonFlushingInserter(inserter);
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
    this.repo = requireNonNull(repo);
    this.rw = requireNonNull(rw);
    this.inserter = requireNonNull(inserter);
    inserterWrapper = new NonFlushingInserter(inserter);
    commands = new ChainedReceiveCommands(repo);
    closeRepo = false;
  }

  /**
   * Get this repo's configuration.
   *
   * <p>This is the storage-level config you would get with {@link Repository#getConfig()}, not, for
   * example, the Gerrit-level project config.
   *
   * @return a defensive copy of the config; modifications have no effect on the underlying config.
   */
  public Config getConfig() {
    return new Config(repo.getConfig());
  }

  /**
   * Get an open revwalk on the repo.
   *
   * <p>Guaranteed to be able to read back any objects inserted in the repository via {@link
   * RepoContext#getInserter()}, even if objects have not been flushed to the underlying repo. In
   * particular this includes any object returned by {@link #getRef(String)}, even taking into
   * account not-yet-executed commands.
   *
   * @return revwalk.
   */
  public RevWalk getRevWalk() {
    return rw;
  }

  /**
   * Read a single ref from the repo.
   *
   * <p>Takes into account any ref update commands added during the course of the update using
   * {@link RepoContext#addRefUpdate}, even if they have not yet been executed on the underlying
   * repo.
   *
   * <p>The results of individual ref lookups are cached: calling this method multiple times with
   * the same ref name will return the same result (unless a command was added in the meantime). The
   * repo is not reread.
   *
   * @param name exact ref name.
   * @return the value of the ref, if present.
   * @throws IOException if an error occurred.
   */
  public Optional<ObjectId> getRef(String name) throws IOException {
    return getCommands().get(name);
  }

  /**
   * Look up refs by prefix.
   *
   * <p>Takes into account any ref update commands added during the course of the update using
   * {@link RepoContext#addRefUpdate}, even if they have not yet been executed on the underlying
   * repo.
   *
   * <p>For any ref that has previously been accessed with {@link #getRef(String)}, the value in the
   * result map will be that same cached value. Any refs that have <em>not</em> been previously
   * accessed are re-scanned from the repo on each call.
   *
   * @param prefix ref prefix; must end in '/' or else be empty.
   * @return a map of ref suffixes to SHA-1s. The refs are all under {@code prefix} and have the
   *     prefix stripped.
   * @throws IOException if an error occurred.
   */
  public Map<String, ObjectId> getRefs(String prefix) throws IOException {
    Map<String, ObjectId> result =
        repo.getRefDatabase()
            .getRefsByPrefix(prefix)
            .stream()
            .collect(toMap(r -> r.getName().substring(prefix.length()), Ref::getObjectId));

    // First, overwrite any cached reads from the underlying RepoRefCache. If any of these differ,
    // it's because a ref was updated after the RepoRefCache read it. It feels a little odd to
    // prefer the *old* value in this case, but it would be weirder to be inconsistent with getRef.
    //
    // Mostly this doesn't matter. If the caller was intending to write to the ref, they lost a
    // race, and they will get a lock failure. If they just want to read, well, the JGit interface
    // doesn't currently guarantee that any snapshot of multiple refs is consistent, so they were
    // probably out of luck anyway.
    commands
        .getRepoRefCache()
        .getCachedRefs()
        .forEach((k, v) -> updateRefIfPrefixMatches(result, prefix, k, v));

    // Second, overwrite with any pending commands.
    commands
        .getCommands()
        .values()
        .forEach(
            c ->
                updateRefIfPrefixMatches(result, prefix, c.getRefName(), toOptional(c.getNewId())));

    return result;
  }

  private static Optional<ObjectId> toOptional(ObjectId id) {
    return id.equals(ObjectId.zeroId()) ? Optional.empty() : Optional.of(id);
  }

  private static void updateRefIfPrefixMatches(
      Map<String, ObjectId> map, String prefix, String fullRefName, Optional<ObjectId> maybeId) {
    if (!fullRefName.startsWith(prefix)) {
      return;
    }
    String suffix = fullRefName.substring(prefix.length());
    if (maybeId.isPresent()) {
      map.put(suffix, maybeId.get());
    } else {
      map.remove(suffix);
    }
  }

  // Not AutoCloseable so callers can't improperly close it. Plus it's never managed with a try
  // block anyway.
  void close() {
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

  ObjectInserter getInserterWrapper() {
    return inserterWrapper;
  }

  ChainedReceiveCommands getCommands() {
    return commands;
  }

  private static class NonFlushingInserter extends ObjectInserter.Filter {
    private final ObjectInserter delegate;

    private NonFlushingInserter(ObjectInserter delegate) {
      this.delegate = delegate;
    }

    @Override
    protected ObjectInserter delegate() {
      return delegate;
    }

    @Override
    public void flush() {
      // Do nothing.
    }

    @Override
    public void close() {
      // Do nothing; the delegate is closed separately.
    }
  }
}
