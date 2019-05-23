// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.config;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowCapability;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.capabilityKey;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.restapi.config.PostCaches.Operation.FLUSH;
import static com.google.gerrit.server.restapi.config.PostCaches.Operation.FLUSH_ALL;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.server.restapi.config.ListCaches.CacheInfo;
import com.google.gerrit.server.restapi.config.PostCaches;
import com.google.inject.Inject;
import java.util.Arrays;
import org.junit.Test;

public class CacheOperationsIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  @Test
  public void flushAll() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/caches/project_list");
    r.assertOK();
    CacheInfo cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertThat(cacheInfo.entries.mem).isGreaterThan((long) 0);

    r = adminRestSession.post("/config/server/caches/", new PostCaches.Input(FLUSH_ALL));
    r.assertOK();
    r.consume();

    r = adminRestSession.get("/config/server/caches/project_list");
    r.assertOK();
    cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertThat(cacheInfo.entries.mem).isNull();
  }

  @Test
  public void flushAll_Forbidden() throws Exception {
    userRestSession
        .post("/config/server/caches/", new PostCaches.Input(FLUSH_ALL))
        .assertForbidden();
  }

  @Test
  public void flushAll_BadRequest() throws Exception {
    adminRestSession
        .post("/config/server/caches/", new PostCaches.Input(FLUSH_ALL, Arrays.asList("projects")))
        .assertBadRequest();
  }

  @Test
  public void flush() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/caches/project_list");
    r.assertOK();
    CacheInfo cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertThat(cacheInfo.entries.mem).isGreaterThan((long) 0);

    r = adminRestSession.get("/config/server/caches/projects");
    r.assertOK();
    cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertThat(cacheInfo.entries.mem).isGreaterThan((long) 1);

    r =
        adminRestSession.post(
            "/config/server/caches/",
            new PostCaches.Input(FLUSH, Arrays.asList("accounts", "project_list")));
    r.assertOK();
    r.consume();

    r = adminRestSession.get("/config/server/caches/project_list");
    r.assertOK();
    cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertThat(cacheInfo.entries.mem).isNull();

    r = adminRestSession.get("/config/server/caches/projects");
    r.assertOK();
    cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertThat(cacheInfo.entries.mem).isGreaterThan((long) 1);
  }

  @Test
  public void flush_Forbidden() throws Exception {
    userRestSession
        .post("/config/server/caches/", new PostCaches.Input(FLUSH, Arrays.asList("projects")))
        .assertForbidden();
  }

  @Test
  public void flush_BadRequest() throws Exception {
    adminRestSession.post("/config/server/caches/", new PostCaches.Input(FLUSH)).assertBadRequest();
  }

  @Test
  public void flush_UnprocessableEntity() throws Exception {
    RestResponse r = adminRestSession.get("/config/server/caches/projects");
    r.assertOK();
    CacheInfo cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertThat(cacheInfo.entries.mem).isGreaterThan((long) 0);

    r =
        adminRestSession.post(
            "/config/server/caches/",
            new PostCaches.Input(FLUSH, Arrays.asList("projects", "unprocessable")));
    r.assertUnprocessableEntity();
    r.consume();

    r = adminRestSession.get("/config/server/caches/projects");
    r.assertOK();
    cacheInfo = newGson().fromJson(r.getReader(), CacheInfo.class);
    assertThat(cacheInfo.entries.mem).isGreaterThan((long) 0);
  }

  @Test
  public void flushWebSessions_Forbidden() throws Exception {
    projectOperations
        .allProjectsForUpdate()
        .add(allowCapability(GlobalCapability.FLUSH_CACHES).group(REGISTERED_USERS))
        .add(allowCapability(GlobalCapability.VIEW_CACHES).group(REGISTERED_USERS))
        .update();
    try {
      RestResponse r =
          userRestSession.post(
              "/config/server/caches/", new PostCaches.Input(FLUSH, Arrays.asList("projects")));
      r.assertOK();
      r.consume();

      userRestSession
          .post(
              "/config/server/caches/", new PostCaches.Input(FLUSH, Arrays.asList("web_sessions")))
          .assertForbidden();
    } finally {
      projectOperations
          .allProjectsForUpdate()
          .remove(capabilityKey(GlobalCapability.FLUSH_CACHES).group(REGISTERED_USERS))
          .remove(capabilityKey(GlobalCapability.VIEW_CACHES).group(REGISTERED_USERS))
          .update();
    }
  }
}
