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

package com.google.gerrit.server.group;

import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.group.Index.Input;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

@Singleton
public class Index implements RestModifyView<GroupResource, Input> {
  public static class Input {}

  private final GroupCache groupCache;

  @Inject
  Index(GroupCache groupCache) {
    this.groupCache = groupCache;
  }

  @Override
  public Response<?> apply(GroupResource rsrc, Input input)
      throws IOException, AuthException, UnprocessableEntityException {
    if (!rsrc.getControl().isOwner()) {
      throw new AuthException("not allowed to index group");
    }

    AccountGroup.UUID groupUuid = rsrc.getGroup().getGroupUUID();
    if (!rsrc.isInternalGroup()) {
      throw new UnprocessableEntityException(
          String.format("External Group Not Allowed: %s", groupUuid.get()));
    }

    Optional<InternalGroup> group = groupCache.get(groupUuid);
    // evicting the group from the cache, reindexes the group
    if (group.isPresent()) {
      groupCache.evict(group.get().getGroupUUID(), group.get().getId(), group.get().getNameKey());
    }
    return Response.none();
  }
}
