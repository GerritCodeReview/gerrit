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

package com.google.gerrit.server.restapi.group.groups;

import static com.google.gerrit.server.group.GroupResource.GROUP_KIND;
import static com.google.gerrit.server.group.SubgroupResource.SUBGROUP_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.group.groups.AddSubgroups.CreateSubgroup;
import com.google.gerrit.server.restapi.group.groups.AddSubgroups.UpdateSubgroup;
import com.google.gerrit.server.restapi.group.groups.DeleteSubgroups.DeleteSubgroup;

/** Guice module that binds all REST endpoints for {@code /groups/<group-id>/groups}. */
public class GroupGroupsRestApiModule extends RestApiModule {

  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), SUBGROUP_KIND);

    /** Add subgroups to group {@code POST /groups/<group-id>/groups}. */
    post(GROUP_KIND, "groups").to(AddSubgroups.class);
    /** Add subgroups to group {@code POST /groups/<group-id>/groups.add}. */
    post(GROUP_KIND, "groups.add").to(AddSubgroups.class);
    /** Remove subgroups from group {@code POST /groups/<group-id>/groups.delete}. */
    post(GROUP_KIND, "groups.delete").to(DeleteSubgroups.class);

    /** List subgroups in group {@code GET /groups/<group-id>/groups}. */
    child(GROUP_KIND, "groups").to(SubgroupsCollection.class);

    /** Add subgroup to group {@code PUT /groups/<group-id>/groups/<group-id>}. */
    create(SUBGROUP_KIND).to(CreateSubgroup.class);
    /** Remove subgroup from group {@code DELETE /groups/<group-id>/groups/<group-id>}. */
    delete(SUBGROUP_KIND).to(DeleteSubgroup.class);
    /** Get subgroup in group {@code GET /groups/<group-id>/groups/<group-id>}. */
    get(SUBGROUP_KIND).to(GetSubgroup.class);
    /** Update subgroup in group {@code PUT /groups/<group-id>/groups/<group-id>}. */
    put(SUBGROUP_KIND).to(UpdateSubgroup.class);
  }
}
