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

package com.google.gerrit.server.api.groups;

import com.google.gerrit.extensions.api.groups.GroupApi;
import com.google.gerrit.extensions.api.groups.Groups;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
class GroupsImpl implements Groups {
  private final GroupsCollection groups;
  private final GroupApiImpl.Factory api;

  @Inject
  GroupsImpl(GroupsCollection groups,
      GroupApiImpl.Factory api) {
    this.groups = groups;
    this.api = api;
  }

  @Override
  public GroupApi id(String id) throws RestApiException {
    return api.create(
        groups.parse(TopLevelResource.INSTANCE, IdString.fromDecoded(id)));
  }
}
