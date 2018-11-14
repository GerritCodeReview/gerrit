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

package com.google.gerrit.server.notedb.schema;

import static com.google.gerrit.reviewdb.client.RefNames.REFS_VERSION;

import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.IntBlob;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.Optional;
import javax.inject.Inject;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

public class NoteDbSchemaVersionManager {
  private final AllProjectsName allProjectsName;
  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated gitRefUpdated;

  @Inject
  NoteDbSchemaVersionManager(
      AllProjectsName allProjectsName,
      GitRepositoryManager repoManager,
      GitReferenceUpdated gitRefUpdated) {
    this.allProjectsName = allProjectsName;
    this.repoManager = repoManager;
    this.gitRefUpdated = gitRefUpdated;
  }

  public int read() throws OrmException {
    try (Repository repo = repoManager.openRepository(allProjectsName)) {
      return IntBlob.parse(repo, REFS_VERSION).map(IntBlob::value).orElse(0);
    } catch (IOException e) {
      throw new OrmException("Failed to read " + REFS_VERSION, e);
    }
  }

  public void increment(int expectedOldVersion) throws IOException, OrmException {
    try (Repository repo = repoManager.openRepository(allProjectsName);
        RevWalk rw = new RevWalk(repo)) {
      Optional<IntBlob> old = IntBlob.parse(repo, REFS_VERSION, rw);
      int actualOldVersion = old.map(IntBlob::value).orElse(0);
      if (actualOldVersion != expectedOldVersion) {
        throw new IOException(
            String.format(
                "expected old version %d for %s, found %d",
                expectedOldVersion, REFS_VERSION, actualOldVersion));
      }
      IntBlob.store(
          repo,
          rw,
          allProjectsName,
          REFS_VERSION,
          old.map(IntBlob::id).orElse(ObjectId.zeroId()),
          actualOldVersion + 1,
          gitRefUpdated);
    }
  }
}
