// Copyright (C) 2018 The Android Open Source Project
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

import static com.google.gerrit.reviewdb.client.RefNames.REFS_VERSION;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.reviewdb.server.OrmException;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

public class NoteDbSchemaVersionManager {
  private final AllProjectsName allProjectsName;
  private final GitRepositoryManager repoManager;

  @Inject
  @VisibleForTesting
  public NoteDbSchemaVersionManager(
      AllProjectsName allProjectsName, GitRepositoryManager repoManager) {
    // Can't inject GitReferenceUpdated here because it has dependencies that are not always
    // available in this injector (e.g. during init). This is ok for now since no other ref updates
    // during init are available to plugins, and there are not any other use cases for listening for
    // updates to the version ref.
    this.allProjectsName = allProjectsName;
    this.repoManager = repoManager;
  }

  public int read() throws OrmException {
    try (Repository repo = repoManager.openRepository(allProjectsName)) {
      return IntBlob.parse(repo, REFS_VERSION).map(IntBlob::value).orElse(0);
    } catch (IOException e) {
      throw new OrmException("Failed to read " + REFS_VERSION, e);
    }
  }

  public void init() throws IOException, OrmException {
    try (Repository repo = repoManager.openRepository(allProjectsName);
        RevWalk rw = new RevWalk(repo)) {
      Optional<IntBlob> old = IntBlob.parse(repo, REFS_VERSION, rw);
      if (old.isPresent()) {
        throw new OrmException(
            String.format(
                "Expected no old version for %s, found %s", REFS_VERSION, old.get().value()));
      }
      IntBlob.store(
          repo,
          rw,
          allProjectsName,
          REFS_VERSION,
          old.map(IntBlob::id).orElse(ObjectId.zeroId()),
          // TODO(dborowitz): Find some way to not hard-code this constant here. We can't depend on
          // NoteDbSchemaVersions from this package, because the schema java_library depends on the
          // server java_library, so that would add a circular dependency. But *this* class must
          // live in the server library, because it's used by things like NoteDbMigrator. One
          // option: once NoteDbMigrator goes away, this class could move back to the schema
          // subpackage.
          180,
          GitReferenceUpdated.DISABLED);
    }
  }

  public void increment(int expectedOldVersion) throws IOException, OrmException {
    try (Repository repo = repoManager.openRepository(allProjectsName);
        RevWalk rw = new RevWalk(repo)) {
      Optional<IntBlob> old = IntBlob.parse(repo, REFS_VERSION, rw);
      if (old.isPresent() && old.get().value() != expectedOldVersion) {
        throw new OrmException(
            String.format(
                "Expected old version %d for %s, found %d",
                expectedOldVersion, REFS_VERSION, old.get().value()));
      }
      IntBlob.store(
          repo,
          rw,
          allProjectsName,
          REFS_VERSION,
          old.map(IntBlob::id).orElse(ObjectId.zeroId()),
          expectedOldVersion + 1,
          GitReferenceUpdated.DISABLED);
    }
  }
}
