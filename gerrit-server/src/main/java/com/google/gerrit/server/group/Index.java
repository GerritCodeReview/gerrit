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

import com.google.gerrit.common.data.GroupDescriptions;
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

    AccountGroup group = GroupDescriptions.toAccountGroup(rsrc.getGroup());
    if (group == null) {
      throw new UnprocessableEntityException(
          String.format("External Group Not Allowed: %s", rsrc.getGroupUUID().get()));
    }

    // evicting the group from the cache, reindexes the group
    groupCache.evict(group);
    return Response.none();
  }
}
