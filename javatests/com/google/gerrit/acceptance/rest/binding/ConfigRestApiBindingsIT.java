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

import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.rest.util.RestApiCallHelper;
import com.google.gerrit.acceptance.rest.util.RestCall;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.server.project.ProjectCacheImpl;
import com.google.gerrit.server.restapi.config.ListTasks.TaskInfo;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import org.junit.Test;

/**
 * Tests for checking the bindings of the config REST API.
 *
 * <p>These tests only verify that the config REST endpoints are correctly bound, they do no test
 * the functionality of the config REST endpoints.
 */
public class ConfigRestApiBindingsIT extends AbstractDaemonTest {
  /**
   * Config REST endpoints to be tested, the URLs contain no placeholders since the only supported
   * config identifier ('server') can be hard-coded.
   */
  private static final ImmutableList<RestCall> CONFIG_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/config/server/version"),
          RestCall.get("/config/server/info"),
          RestCall.get("/config/server/preferences"),
          RestCall.put("/config/server/preferences"),
          RestCall.get("/config/server/preferences.diff"),
          RestCall.put("/config/server/preferences.diff"),
          RestCall.get("/config/server/preferences.edit"),
          RestCall.put("/config/server/preferences.edit"),
          RestCall.get("/config/server/top-menus"),
          RestCall.put("/config/server/email.confirm"),
          RestCall.post("/config/server/check.consistency"),
          RestCall.post("/config/server/reload"),
          RestCall.get("/config/server/summary"),
          RestCall.get("/config/server/capabilities"),
          RestCall.get("/config/server/caches"),
          RestCall.post("/config/server/caches"),
          RestCall.get("/config/server/tasks"));

  /**
   * Cache REST endpoints to be tested, the URLs contain a placeholder for the cache identifier.
   * Since there is only supported a single supported config identifier ('server') it can be
   * hard-coded.
   */
  private static final ImmutableList<RestCall> CACHE_ENDPOINTS =
      ImmutableList.of(RestCall.get("/config/server/caches/%s"));

  /**
   * Task REST endpoints to be tested, the URLs contain a placeholder for the task identifier. Since
   * there is only supported a single supported config identifier ('server') it can be hard-coded.
   */
  private static final ImmutableList<RestCall> TASK_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/config/server/tasks/%s"),

          // Task deletion must be tested last
          RestCall.delete("/config/server/tasks/%s"));

  @Inject private ProjectOperations projectOperations;

  @Test
  public void configEndpoints() throws Exception {
    // 'Access Database' is needed for the '/config/server/check.consistency' REST endpoint
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.ACCESS_DATABASE).group(REGISTERED_USERS))
        .update();

    RestApiCallHelper.execute(adminRestSession, CONFIG_ENDPOINTS);
  }

  @Test
  public void cacheEndpoints() throws Exception {
    RestApiCallHelper.execute(adminRestSession, CACHE_ENDPOINTS, ProjectCacheImpl.CACHE_NAME);
  }

  @Test
  public void taskEndpoints() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/tasks/");
    List<TaskInfo> result =
        newGson().fromJson(r.getReader(), new TypeToken<List<TaskInfo>>() {}.getType());
    r.consume();

    Optional<String> id =
        result
            .stream()
            .filter(t -> "Log File Compressor".equals(t.command))
            .map(t -> t.id)
            .findFirst();
    assertThat(id).isPresent();

    RestApiCallHelper.execute(adminRestSession, TASK_ENDPOINTS, id.get());
  }
}
