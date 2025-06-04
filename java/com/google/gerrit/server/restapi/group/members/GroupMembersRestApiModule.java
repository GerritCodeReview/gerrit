// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.group.members;

import static com.google.gerrit.server.group.GroupResource.GROUP_KIND;
import static com.google.gerrit.server.group.MemberResource.MEMBER_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.group.members.AddMembers.CreateMember;
import com.google.gerrit.server.restapi.group.members.AddMembers.UpdateMember;
import com.google.gerrit.server.restapi.group.members.DeleteMembers.DeleteMember;

public class GroupMembersRestApiModule extends RestApiModule {

  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), MEMBER_KIND);

    post(GROUP_KIND, "members").to(AddMembers.class);
    post(GROUP_KIND, "members.add").to(AddMembers.class);
    post(GROUP_KIND, "members.delete").to(DeleteMembers.class);

    child(GROUP_KIND, "members").to(MembersCollection.class);
    create(MEMBER_KIND).to(CreateMember.class);
    get(MEMBER_KIND).to(GetMember.class);
    put(MEMBER_KIND).to(UpdateMember.class);
    delete(MEMBER_KIND).to(DeleteMember.class);
  }
}
