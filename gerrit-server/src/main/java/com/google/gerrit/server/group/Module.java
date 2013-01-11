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

import static com.google.gerrit.server.group.GroupResource.GROUP_KIND;
import static com.google.gerrit.server.group.IncludedGroupResource.INCLUDED_GROUP_KIND;
import static com.google.gerrit.server.group.MemberResource.MEMBER_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

public class Module extends RestApiModule {
  @Override
  protected void configure() {
    bind(GroupsCollection.class);

    DynamicMap.mapOf(binder(), GROUP_KIND);
    DynamicMap.mapOf(binder(), MEMBER_KIND);
    DynamicMap.mapOf(binder(), INCLUDED_GROUP_KIND);

    get(GROUP_KIND).to(GetGroup.class);

    child(GROUP_KIND, "members").to(MembersCollection.class);
    get(MEMBER_KIND).to(GetMember.class);
    put(GROUP_KIND, "members").to(PutMember.class);

    child(GROUP_KIND, "groups").to(IncludedGroupsCollection.class);
    get(INCLUDED_GROUP_KIND).to(GetIncludedGroup.class);
  }
}
