// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.util.Url;

class GetGroup implements RestReadView<GroupResource> {

  @Override
  public Object apply(GroupResource resource) throws AuthException,
      BadRequestException, ResourceConflictException, Exception {
    return parse(resource.getControl().getGroup());
  }

  public static GroupInfo parse(final GroupDescription.Basic group) {
    GroupInfo info = new GroupInfo();
    info.name = group.getName();
    info.uuid = group.getGroupUUID().get();
    info.isVisibleToAll = group.isVisibleToAll();
    if (group instanceof GroupDescription.Internal) {
      final AccountGroup internalGroup =
          ((GroupDescription.Internal) group).getAccountGroup();
      info.groupId = internalGroup.getId().get();
      info.description = Strings.emptyToNull(internalGroup.getDescription());
      info.ownerUuid = internalGroup.getOwnerGroupUUID().get();
    }
    info.finish();
    return info;
  }

  public static class GroupInfo {
    final String kind = "gerritcodereview#group";
    String id;
    String name;
    String uuid;
    int groupId;
    String description;
    boolean isVisibleToAll;
    String ownerUuid;

    void finish() {
      id = Url.encode(GroupsCollection.UUID_PREFIX + uuid);
    }
  }
}
