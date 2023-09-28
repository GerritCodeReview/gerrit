// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.InternalGroup;
import com.google.gerrit.exceptions.NoSuchGroupException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.group.db.GroupDelta;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.PostUpdateContext;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;

public class BlockUserOp extends ReviewerOp {
  public interface Factory {
    BlockUserOp create(Account reviewer);
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String blockedUsersGroupName;
  private final GroupCache groupCache;
  private final Provider<GroupsUpdate> groupsUpdateProvider;
  private final Account reviewer;

  @AssistedInject
  BlockUserOp(
      @GerritServerConfig Config config,
      GroupCache groupCache,
      @ServerInitiated Provider<GroupsUpdate> groupsUpdateProvider,
      @Assisted Account reviewer) {
    this.blockedUsersGroupName = config.getString("groups", null, "blockedUsersGroup");
    this.groupCache = groupCache;
    this.groupsUpdateProvider = groupsUpdateProvider;
    this.reviewer = reviewer;
  }

  @Override
  public boolean updateChange(ChangeContext ctx)
      throws RestApiException, IOException, PermissionBackendException {
    Optional<InternalGroup> group = groupCache.get(AccountGroup.nameKey(blockedUsersGroupName));
    if (group.isEmpty()) {
      logger.atInfo().log(
          "Blocked users group (%s) cannot be find. Blocking user is not possible.");
      return false;
    }

    try {
      GroupDelta groupDelta =
          GroupDelta.builder()
              .setMemberModification(memberIds -> Sets.union(memberIds, Set.of(reviewer.id())))
              .build();
      groupsUpdateProvider.get().updateGroup(group.get().getGroupUUID(), groupDelta);
    } catch (NoSuchGroupException | ConfigInvalidException e) {
      throw new ResourceConflictException(
          String.format("Blocked users group %s was not updated", blockedUsersGroupName), e);
    }

    return true;
  }

  @Override
  public void postUpdate(PostUpdateContext ctx) throws Exception {
    opResult = Result.builder().setBlockedReviewer(reviewer.id()).build();
  }
}
