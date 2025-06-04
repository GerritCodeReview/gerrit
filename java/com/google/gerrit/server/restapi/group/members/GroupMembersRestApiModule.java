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

/** Guice module that binds all REST endpoints for {@code /groups/<group-id>/members}. */
public class GroupMembersRestApiModule extends RestApiModule {

  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), MEMBER_KIND);

    /** Add members to group {@code POST /groups/<group-id>/members}. */
    post(GROUP_KIND, "members").to(AddMembers.class);
    /** Add members to group {@code POST /groups/<group-id>/members.add}. */
    post(GROUP_KIND, "members.add").to(AddMembers.class);
    /** Remove members from group {@code POST /groups/<group-id>/members.delete}. */
    post(GROUP_KIND, "members.delete").to(DeleteMembers.class);

    /** List members in group {@code GET /groups/<group-id>/members}. */
    child(GROUP_KIND, "members").to(MembersCollection.class);

    /** Add member to group {@code PUT /groups/<group-id>/members/<member-id>}. */
    create(MEMBER_KIND).to(CreateMember.class);
    /** Get member in group {@code GET /groups/<group-id>/members/<member-id>}. */
    get(MEMBER_KIND).to(GetMember.class);
    /** Update member in group {@code PUT /groups/<group-id>/members/<member-id>}. */
    put(MEMBER_KIND).to(UpdateMember.class);
    /** Remove member from group {@code DELETE /groups/<group-id>/members/<member-id>}. */
    delete(MEMBER_KIND).to(DeleteMember.class);
  }
}
