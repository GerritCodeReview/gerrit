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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Hashing;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class ExternalGroupsKeyReader {
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;

  @Inject
  ExternalGroupsKeyReader(
      GitRepositoryManager repoManager, AllUsersName allUsersName, MetricMaker metricMaker) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
  }

  @VisibleForTesting
  public String currentKey() throws IOException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      List<Ref> groupsRefs = repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_GROUPS);
      ByteBuffer buf = ByteBuffer.allocate(groupsRefs.size() * Constants.OBJECT_ID_LENGTH);
      for (Ref groupRef : groupsRefs) {
        groupRef.getObjectId().copyRawTo(buf);
      }
      return Hashing.murmur3_128().hashBytes(buf.array()).toString();
    }
  }
}
