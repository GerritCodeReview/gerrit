// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * This class is used to compute a compound key that represents the state of the internal groups in
 * NoteDb.
 */
@Singleton
public class GroupsSnapshotReader {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;

  @Inject
  GroupsSnapshotReader(GitRepositoryManager repoManager, AllUsersName allUsersName) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
  }

  @AutoValue
  public abstract static class Snapshot {
    /**
     * 128-bit hash of all group ref {@link com.google.gerrit.git.ObjectIds}. To be used as cache
     * key.
     */
    public abstract String hash();

    /** Snapshot of the state of all relevant NoteDb group refs. */
    public abstract ImmutableList<Ref> groupsRefs();

    public static Snapshot create(String hash, ImmutableList<Ref> groupsRefs) {
      return new AutoValue_GroupsSnapshotReader_Snapshot(hash, groupsRefs);
    }
  }

  /** Retrieves a snapshot key of all internal groups refs from NoteDb. */
  public Snapshot getSnapshot() throws IOException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      ImmutableList<Ref> groupsRefs =
          ImmutableList.copyOf(repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_GROUPS));
      ByteBuffer buf = ByteBuffer.allocate(groupsRefs.size() * Constants.OBJECT_ID_LENGTH);
      for (Ref groupRef : groupsRefs) {
        groupRef.getObjectId().copyRawTo(buf);
      }
      String hash = Hashing.murmur3_128().hashBytes(buf.array()).toString();
      return Snapshot.create(hash, groupsRefs);
    }
  }
}
