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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.AllExternalGroupsKeyProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * This class is used to compute a compound key that represents the state of the internal groups in
 * NoteDb
 */
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
  public GroupsExternalKey currentKey() throws IOException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {

      List<Ref> groupsRefs = repo.getRefDatabase().getRefsByPrefix(RefNames.REFS_GROUPS);
      return GroupsExternalKey.create(groupsRefs.stream().collect(toImmutableList()));
    }
  }

  @AutoValue
  public abstract static class GroupsExternalKey {
    public static GroupsExternalKey create(ImmutableList<Ref> groupsRefs) {
      return new AutoValue_ExternalGroupsKeyReader_GroupsExternalKey(groupsRefs);
    }

    public abstract ImmutableList<Ref> groupsRefs();

    enum Serializer implements CacheSerializer<GroupsExternalKey> {
      INSTANCE;

      @Override
      public byte[] serialize(GroupsExternalKey object) {
        return Protos.toByteArray(
            AllExternalGroupsKeyProto.newBuilder()
                .addAllGroupRefName(
                    object.groupsRefs().stream()
                        .map(ref -> ref.getName())
                        .collect(toImmutableList()))
                .build());
      }

      @Override
      public GroupsExternalKey deserialize(byte[] in) {
        // TODO(ghareeb): need to implement deserialize
        throw new IllegalStateException();
      }
    }
  }
}
