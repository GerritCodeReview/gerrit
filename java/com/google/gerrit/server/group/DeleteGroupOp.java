// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.group;

import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.update.RepoOnlyOp;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;

public class DeleteGroupOp implements RepoOnlyOp {

  public interface Factory {
    DeleteGroupOp create(GroupDescription.Basic group);
  }

  private final GroupDescription.Basic group;
  private final GroupCache groupCache;
  private final Provider<GroupIndexer> indexer;

  @Inject
  DeleteGroupOp(
      @Assisted GroupDescription.Basic group,
      GroupCache groupCache,
      Provider<GroupIndexer> indexer) {
    this.group = group;
    this.groupCache = groupCache;
    this.indexer = indexer;
  }

  @Override
  public void updateRepo(RepoContext ctx) throws IOException {
    String groupRefPrefix = RefNames.refsGroups(group.getGroupUUID());
    for (Map.Entry<String, ObjectId> e : ctx.getRepoView().getRefs(groupRefPrefix).entrySet()) {
      removeRef(ctx, e, groupRefPrefix);
    }
    ctx.deleteGroup(group.getGroupUUID());
    groupCache.evict(group.getGroupUUID());
    indexer.get().index(group.getGroupUUID());
    groupCache.evict(AccountGroup.nameKey(group.getName()));
  }

  private void removeRef(RepoContext ctx, Map.Entry<String, ObjectId> entry, String prefix)
      throws IOException {
    ctx.addRefUpdate(entry.getValue(), ObjectId.zeroId(), prefix + entry.getKey());
  }
}
