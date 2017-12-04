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
import static com.google.gerrit.server.group.MemberResource.MEMBER_KIND;
import static com.google.gerrit.server.group.SubgroupResource.SUBGROUP_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.audit.GroupMemberAuditListener;
import com.google.gerrit.server.group.AddMembers.UpdateMember;
import com.google.gerrit.server.group.AddSubgroups.UpdateSubgroup;
import com.google.gerrit.server.group.DeleteMembers.DeleteMember;
import com.google.gerrit.server.group.DeleteSubgroups.DeleteSubgroup;
import com.google.gerrit.server.group.db.GroupsUpdate;
import com.google.gerrit.server.notedb.GroupsMigration;
import com.google.inject.Provides;

public class Module extends RestApiModule {
  private final GroupsMigration groupsMigration;

  public Module(GroupsMigration groupsMigration) {
    this.groupsMigration = groupsMigration;
  }

  @Override
  protected void configure() {
    bind(GroupsCollection.class);

    DynamicMap.mapOf(binder(), GROUP_KIND);
    DynamicMap.mapOf(binder(), MEMBER_KIND);
    DynamicMap.mapOf(binder(), SUBGROUP_KIND);

    get(GROUP_KIND).to(GetGroup.class);
    put(GROUP_KIND).to(PutGroup.class);
    get(GROUP_KIND, "detail").to(GetDetail.class);
    post(GROUP_KIND, "index").to(Index.class);
    post(GROUP_KIND, "members").to(AddMembers.class);
    post(GROUP_KIND, "members.add").to(AddMembers.class);
    post(GROUP_KIND, "members.delete").to(DeleteMembers.class);
    post(GROUP_KIND, "groups").to(AddSubgroups.class);
    post(GROUP_KIND, "groups.add").to(AddSubgroups.class);
    post(GROUP_KIND, "groups.delete").to(DeleteSubgroups.class);
    get(GROUP_KIND, "description").to(GetDescription.class);
    put(GROUP_KIND, "description").to(PutDescription.class);
    delete(GROUP_KIND, "description").to(PutDescription.class);
    get(GROUP_KIND, "name").to(GetName.class);
    put(GROUP_KIND, "name").to(PutName.class);
    get(GROUP_KIND, "owner").to(GetOwner.class);
    put(GROUP_KIND, "owner").to(PutOwner.class);
    get(GROUP_KIND, "options").to(GetOptions.class);
    put(GROUP_KIND, "options").to(PutOptions.class);
    get(GROUP_KIND, "log.audit").to(GetAuditLog.class);
    post(GROUP_KIND, "rebuild").to(Rebuild.class);

    child(GROUP_KIND, "members").to(MembersCollection.class);
    get(MEMBER_KIND).to(GetMember.class);
    put(MEMBER_KIND).to(UpdateMember.class);
    delete(MEMBER_KIND).to(DeleteMember.class);

    child(GROUP_KIND, "groups").to(SubgroupsCollection.class);
    get(SUBGROUP_KIND).to(GetSubgroup.class);
    put(SUBGROUP_KIND).to(UpdateSubgroup.class);
    delete(SUBGROUP_KIND).to(DeleteSubgroup.class);

    factory(CreateGroup.Factory.class);
    factory(GroupsUpdate.Factory.class);

    if (!groupsMigration.disableGroupReviewDb()) {
      // DbGroupMemberAuditListener is used solely for the ReviewDb audit log. It does not respect
      // ReviewDb wrappers that disable reads. Hence, we don't want to bind it if ReviewDb is
      // disabled.
      DynamicSet.bind(binder(), GroupMemberAuditListener.class)
          .to(DbGroupMemberAuditListener.class);
    }
  }

  @Provides
  @ServerInitiated
  GroupsUpdate provideServerInitiatedGroupsUpdate(GroupsUpdate.Factory groupsUpdateFactory) {
    return groupsUpdateFactory.create(null);
  }

  @Provides
  @UserInitiated
  GroupsUpdate provideUserInitiatedGroupsUpdate(
      GroupsUpdate.Factory groupsUpdateFactory, IdentifiedUser currentUser) {
    return groupsUpdateFactory.create(currentUser);
  }
}
