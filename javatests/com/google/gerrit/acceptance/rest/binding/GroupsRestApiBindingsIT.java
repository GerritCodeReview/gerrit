// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.binding;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import org.junit.Test;

/**
 * Tests for checking the bindings of the groups REST API.
 *
 * <p>These tests only verify that the group REST endpoints are correctly bound, they do no test the
 * functionality of the group REST endpoints.
 */
public class GroupsRestApiBindingsIT extends AbstractDaemonTest {
  /**
   * Group REST endpoints to be tested, each URL contains a placeholder for the group identifier.
   */
  private static final ImmutableList<RestCall> GROUP_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/groups/%s"),
          RestCall.put("/groups/%s"),
          RestCall.get("/groups/%s/detail"),
          RestCall.get("/groups/%s/name"),
          RestCall.put("/groups/%s/name"),
          RestCall.get("/groups/%s/description"),
          RestCall.put("/groups/%s/description"),
          RestCall.delete("/groups/%s/description"),
          RestCall.get("/groups/%s/owner"),
          RestCall.put("/groups/%s/owner"),
          RestCall.get("/groups/%s/options"),
          RestCall.put("/groups/%s/options"),
          RestCall.post("/groups/%s/members"),
          RestCall.post("/groups/%s/members.add"),
          RestCall.post("/groups/%s/members.delete"),
          RestCall.post("/groups/%s/groups"),
          RestCall.post("/groups/%s/groups.add"),
          RestCall.post("/groups/%s/groups.delete"),
          RestCall.get("/groups/%s/log.audit"),
          RestCall.post("/groups/%s/index"),
          RestCall.get("/groups/%s/members"),
          RestCall.get("/groups/%s/groups"));

  /**
   * Member REST endpoints to be tested, each URL contains placeholders for the group identifier and
   * the member identifier.
   */
  private static final ImmutableList<RestCall> MEMBER_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/groups/%s/members/%s"),
          RestCall.put("/groups/%s/members/%s"),

          // Member deletion must be tested last
          RestCall.delete("/groups/%s/members/%s"));

  /**
   * Subgroup REST endpoints to be tested, each URL contains placeholders for the group identifier
   * and the subgroup identifier.
   */
  private static final ImmutableList<RestCall> SUBGROUP_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/groups/%s/groups/%s"),
          RestCall.put("/groups/%s/groups/%s"),

          // Subgroup deletion must be tested last
          RestCall.delete("/groups/%s/groups/%s"));

  @Test
  public void groupEndpoints() throws Exception {
    String group = gApi.groups().create("test-group").get().name;
    RestApiCallHelper.execute(adminRestSession, GROUP_ENDPOINTS, group);
  }

  @Test
  public void memberEndpoints() throws Exception {
    String group = gApi.groups().create("test-group").get().name;
    gApi.groups().id(group).addMembers(admin.email());
    RestApiCallHelper.execute(adminRestSession, MEMBER_ENDPOINTS, group, admin.email());
  }

  @Test
  public void subgroupEndpoints() throws Exception {
    String group = gApi.groups().create("test-group").get().name;
    String subgroup = gApi.groups().create("test-subgroup").get().name;
    gApi.groups().id(group).addGroups(subgroup);
    RestApiCallHelper.execute(adminRestSession, SUBGROUP_ENDPOINTS, group, subgroup);
  }
}
