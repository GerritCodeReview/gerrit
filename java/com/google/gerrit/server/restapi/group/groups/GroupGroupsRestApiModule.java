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

public class GroupGroupsRestApiModule extends RestApiModule {

  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), SUBGROUP_KIND);

    post(GROUP_KIND, "groups").to(AddSubgroups.class);
    post(GROUP_KIND, "groups.add").to(AddSubgroups.class);
    post(GROUP_KIND, "groups.delete").to(DeleteSubgroups.class);

    child(GROUP_KIND, "groups").to(SubgroupsCollection.class);
    create(SUBGROUP_KIND).to(CreateSubgroup.class);
    delete(SUBGROUP_KIND).to(DeleteSubgroup.class);
    get(SUBGROUP_KIND).to(GetSubgroup.class);
    put(SUBGROUP_KIND).to(UpdateSubgroup.class);
  }
}
