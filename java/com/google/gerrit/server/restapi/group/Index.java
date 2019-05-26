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

package com.google.gerrit.server.restapi.group;

import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.group.GroupResource;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;

@Singleton
public class Index implements RestModifyView<GroupResource, Input> {

  private final Provider<GroupIndexer> indexer;

  @Inject
  Index(Provider<GroupIndexer> indexer) {
    this.indexer = indexer;
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

    indexer.get().index(groupUuid);
    return Response.none();
  }
}
