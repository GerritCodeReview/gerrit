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

package com.google.gerrit.server.restapi.group;

import static com.google.gerrit.server.group.GroupResource.GROUP_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.group.groups.GroupGroupsRestApiModule;
import com.google.gerrit.server.restapi.group.members.GroupMembersRestApiModule;

/** Guice module that binds all REST endpoints for {@code /groups/}. */
public class GroupRestApiModule extends RestApiModule {

  @Override
  protected void configure() {
    bind(GroupsCollection.class);

    DynamicMap.mapOf(binder(), GROUP_KIND);

    /** Create group {@code PUT /groups/<group-name>}. */
    create(GROUP_KIND).to(CreateGroup.class);
    /** Delete group {@code DELETE /groups/<group-id>}. */
    delete(GROUP_KIND).to(DeleteGroup.class);
    /** Get group {@code GET /groups/<group-id>}. */
    get(GROUP_KIND).to(GetGroup.class);
    /** Update group {@code PUT /groups/<group-id>}. */
    put(GROUP_KIND).to(PutGroup.class);

    /** Get group description {@code GET /groups/<group-id>/description}. */
    get(GROUP_KIND, "description").to(GetDescription.class);
    /** Update group description {@code PUT /groups/<group-id>/description}. */
    put(GROUP_KIND, "description").to(PutDescription.class);
    /** Delete group description {@code DELETE /groups/<group-id>/description}. */
    delete(GROUP_KIND, "description").to(PutDescription.class);

    /** Get group detail {@code GET /groups/<group-id>/detail}. */
    get(GROUP_KIND, "detail").to(GetDetail.class);

    /** Index group {@code POST /groups/<group-id>/index}. */
    post(GROUP_KIND, "index").to(Index.class);

    /** Get group audit logs {@code GET /groups/<group-id>/log.audit}. */
    get(GROUP_KIND, "log.audit").to(GetAuditLog.class);

    /** Get group name {@code GET /groups/<group-id>/name}. */
    get(GROUP_KIND, "name").to(GetName.class);
    /** Update group name {@code PUT /groups/<group-id>/name}. */
    put(GROUP_KIND, "name").to(PutName.class);

    /** Get group options {@code GET /groups/<group-id>/options}. */
    get(GROUP_KIND, "options").to(GetOptions.class);
    /** Update group options {@code PUT /groups/<group-id>/options}. */
    put(GROUP_KIND, "options").to(PutOptions.class);

    /** Get group owner {@code GET /groups/<group-id>/owner}. */
    get(GROUP_KIND, "owner").to(GetOwner.class);
    /** Update group owner {@code PUT /groups/<group-id>/owner}. */
    put(GROUP_KIND, "owner").to(PutOwner.class);

    /** Module for {@code /grousp/<group-id>/groups}. */
    install(new GroupGroupsRestApiModule());
    /** Module for {@code /grousp/<group-id>/members}. */
    install(new GroupMembersRestApiModule());
  }
}
